package com.hengx.damageprobe.probe;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.hengx.damageprobe.ProbeState;
import com.hengx.damageprobe.core.ProbeEngine;

/**
 * 效果/修饰符记录的合并、限流与伤害联动。
 *
 * <p>合并窗口 20 tick，键包含 目标/效果或属性/修饰符/动作/结果；窗口内重复只计数，
 * 等 20 tick 没有新事件后再输出一次 {@code (×N)}。这样 curio 每 tick 重刷也不会刷屏。</p>
 */
public final class ProbeEventStore {

    private static final int DEDUPE_TICKS = 20;
    private static final int MAX_PENDING = 512;
    private static final int MAX_LINES = 32;
    private static final int MAX_RECENT = 256;
    private static final int LINK_KEEP_TICKS = 80;

    private static final Object LOCK = new Object();
    private static final Map<String, Pending> PENDING = new LinkedHashMap<>();
    private static final Map<Long, Map<String, LinkAgg>> BY_TICK = new HashMap<>();
    private static final Deque<String> RECENT = new ArrayDeque<>();

    private ProbeEventStore() {
    }

    public static void record(ProbeEvent event) {
        if (event == null) {
            return;
        }
        if (event.category == ProbeEvent.Category.EFFECT && !ProbeState.effectsEnabled()) {
            return;
        }
        if (event.category == ProbeEvent.Category.MODIFIER && !ProbeState.modifiersEnabled()) {
            return;
        }
        if (ProbeState.playerRelatedOnly() && !event.playerRelated) {
            return;
        }
        if (ProbeState.rejectedOnly() && event.result != ProbeEvent.Result.REJECTED) {
            return;
        }

        String key = event.category + "|" + event.key + "|" + event.result;
        Pending expired = null;
        synchronized (LOCK) {
            Pending old = PENDING.get(key);
            if (old != null && event.tick - old.lastTick <= DEDUPE_TICKS) {
                old.count++;
                old.lastTick = event.tick;
                link(event);
                return;
            }
            if (old != null) {
                PENDING.remove(key);
                expired = old;
            }
            Pending fresh = new Pending(event);
            PENDING.put(key, fresh);
            link(event);
            if (PENDING.size() > MAX_PENDING) {
                String oldest = PENDING.keySet().iterator().next();
                expired = PENDING.remove(oldest);
            }
        }
        if (expired != null) {
            emitBatch(List.of(expired));
        }
    }

    public static void onServerTickEnd() {
        long tick = ProbeEngine.serverTick();
        List<Pending> expired = new ArrayList<>();
        synchronized (LOCK) {
            var it = PENDING.entrySet().iterator();
            while (it.hasNext()) {
                Pending p = it.next().getValue();
                if (tick - p.lastTick >= DEDUPE_TICKS) {
                    expired.add(p);
                    it.remove();
                }
            }
            long cutoff = tick - LINK_KEEP_TICKS;
            BY_TICK.keySet().removeIf(t -> t < cutoff);
        }
        emitBatch(expired);
    }

    /** 自检用：立即输出所有待合并记录。 */
    public static void flushAll() {
        List<Pending> all;
        synchronized (LOCK) {
            all = new ArrayList<>(PENDING.values());
            PENDING.clear();
        }
        emitBatch(all);
    }

    /** 伤害记录联动：同一 tick 内目标/攻击者身上的效果与修饰符变化。 */
    public static List<String> linked(long tick, int targetId, int causingId) {
        List<String> out = new ArrayList<>();
        synchronized (LOCK) {
            Map<String, LinkAgg> map = BY_TICK.get(tick);
            if (map == null) {
                return out;
            }
            for (LinkAgg agg : map.values()) {
                ProbeEvent e = agg.event;
                // 只联动“发生在受击者或攻击者身上”的变动；不要因为攻击者是某个效果的
                // source 就把“攻击者给别人加 buff”挂到它的每一次命中上。
                boolean targetMatch = e.targetId == targetId || e.targetId == causingId;
                if (!targetMatch) {
                    continue;
                }
                String suffix = agg.count > 1 ? " (×" + agg.count + ")" : "";
                out.add(e.category.label + " " + e.text + suffix);
            }
        }
        return out;
    }

    public static int pendingCount() {
        synchronized (LOCK) {
            return PENDING.size();
        }
    }

    public static List<String> recentTexts() {
        synchronized (LOCK) {
            return new ArrayList<>(RECENT);
        }
    }

    public static void clear() {
        synchronized (LOCK) {
            PENDING.clear();
            BY_TICK.clear();
            RECENT.clear();
        }
    }

    private static void link(ProbeEvent event) {
        Map<String, LinkAgg> map = BY_TICK.computeIfAbsent(event.tick, t -> new LinkedHashMap<>());
        String key = event.category + "|" + event.key + "|" + event.result;
        LinkAgg agg = map.get(key);
        if (agg == null) {
            map.put(key, new LinkAgg(event));
        }
        else {
            agg.count++;
        }
    }

    private static void emitBatch(List<Pending> pending) {
        if (pending.isEmpty()) {
            return;
        }
        int max = MAX_LINES;
        int limit = Math.min(max, pending.size());
        for (int i = 0; i < limit; i++) {
            Pending p = pending.get(i);
            String suffix = p.count > 1 ? " (×" + p.count + ")" : "";
            String text = "tick=" + p.event.tick + " " + p.event.text + suffix;
            ProbeOutput.emit(p.event.category, p.event.result, text);
            synchronized (LOCK) {
                RECENT.addLast(text);
                while (RECENT.size() > MAX_RECENT) {
                    RECENT.removeFirst();
                }
            }
        }
        if (pending.size() > limit) {
            ProbeEvent.Category cat = pending.get(limit).event.category;
            ProbeOutput.emitOmitted(cat, pending.size() - limit);
        }
    }

    private static final class Pending {
        final ProbeEvent event;
        int count = 1;
        long lastTick;

        Pending(ProbeEvent event) {
            this.event = event;
            this.lastTick = event.tick;
        }
    }

    private static final class LinkAgg {
        final ProbeEvent event;
        int count = 1;

        LinkAgg(ProbeEvent event) {
            this.event = event;
        }
    }
}
