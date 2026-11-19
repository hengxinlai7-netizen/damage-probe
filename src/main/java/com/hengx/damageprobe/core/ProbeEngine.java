package com.hengx.damageprobe.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.hengx.damageprobe.DamageProbe;
import com.hengx.damageprobe.ProbeState;
import com.hengx.damageprobe.output.Output;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;

/**
 * 记录生命周期。
 *
 * <pre>
 * LivingAttackEvent HIGHEST  → 建记录(血量前 / 理论伤害 / 线程)
 * LivingAttackEvent LOWEST   → 补记"最终是否被取消"
 * LivingHurtEvent HIGHEST    → 记录进入值
 * LivingHurtEvent LOWEST     → 记录最终值 + setAmount 调用点
 * LivingDamageEvent HIGHEST  → 记录进入值(护甲/抗性/吸收之后)
 * LivingDamageEvent LOWEST   → 记录最终值
 * actuallyHurt HEAD          → 记录入参(事件前) + 扣血前血量
 * actuallyHurt TAIL          → 记录调用后血量,实扣 = before - after
 * ServerTickEvent END        → 读取 healthAfter,结算 healthBefore-healthAfter,输出
 * </pre>
 *
 * <p>同一实体在同一 tick 内多次命中:下一次 LivingAttackEvent 到来时先结算上一条,
 * 此时 health 已经是上一次 setHealth 后的值,所以每条记录的"掉血"互不污染。</p>
 */
public final class ProbeEngine {

    private static final Object LOCK = new Object();
    private static final Map<Integer, Deque<HitRecord>> OPEN = new HashMap<>();
    private static final List<HitRecord> PENDING_EMIT = new ArrayList<>();
    private static final List<HitRecord> RECENT = new ArrayList<>();
    private static final int RECENT_MAX = 128;

    private static volatile MinecraftServer server;
    private static volatile long serverTick;

    private ProbeEngine() {
    }

    // ---------------------------------------------------------------- server 生命周期

    public static void setServer(MinecraftServer value) {
        server = value;
    }

    public static void clearServer() {
        server = null;
        synchronized (LOCK) {
            OPEN.clear();
            PENDING_EMIT.clear();
        }
    }

    public static MinecraftServer server() {
        return server;
    }

    public static long serverTick() {
        return serverTick;
    }

    public static List<HitRecord> recentRecords() {
        synchronized (RECENT) {
            return new ArrayList<>(RECENT);
        }
    }

    public static void clearRecent() {
        synchronized (RECENT) {
            RECENT.clear();
        }
    }

    public static int openCount() {
        synchronized (LOCK) {
            int n = 0;
            for (Deque<HitRecord> dq : OPEN.values()) {
                n += dq.size();
            }
            return n;
        }
    }

    // ---------------------------------------------------------------- 事件入口

    public static void onAttack(LivingAttackEvent event) {
        if (!ProbeState.enabled()) {
            return;
        }
        LivingEntity target = event.getEntity();
        if (isClientSide(target)) {
            // 客户端侧(单人时与服务端同 JVM、entityId 相同)也会触发攻击事件。
            // 例如 Iron's 的法术弹丸 spawn 包不同步 damage,客户端那只的伤害恒为 0,
            // 若允许建记录就会与服务端记录串号,产生"Render thread 异步伤害"的假阳性。
            return;
        }
        DamageSource source = event.getSource();
        if (!passesFilter(target, source)) {
            return;
        }

        boolean main = isMainThread();
        synchronized (LOCK) {
            Deque<HitRecord> dq = OPEN.computeIfAbsent(target.getId(), id -> new ArrayDeque<>());
            // 上一条记录如果已经走完 hurt/damage(或已取消),先结算;当前 health 是它 setHealth 之后的值。
            while (!dq.isEmpty() && dq.peekFirst().isResolved()) {
                settleLocked(dq.pollFirst());
            }

            HitRecord record = new HitRecord(target, source, event.getAmount(), serverTick, main);
            if (event.isCanceled()) {
                record.attackCanceled = true;
                record.addTimeline("[HIGHEST] LivingAttackEvent: 已取消");
            }
            dq.addLast(record);
        }
    }

    public static void onAttackLowest(LivingAttackEvent event) {
        if (!ProbeState.enabled()) {
            return;
        }
        if (isClientSide(event.getEntity())) {
            return;
        }
        synchronized (LOCK) {
            HitRecord record = top(event.getEntity());
            if (record == null) {
                return;
            }
            if (event.isCanceled()) {
                record.attackCanceled = true;
                record.addTimeline("[LOWEST]  LivingAttackEvent: 已取消");
            }
            markThread(record);
        }
    }

    public static void onHurtHighest(LivingHurtEvent event) {
        if (!ProbeState.enabled()) {
            return;
        }
        if (isClientSide(event.getEntity())) {
            return;
        }
        synchronized (LOCK) {
            HitRecord record = top(event.getEntity());
            if (record == null) {
                return;
            }
            record.hurtSeen = true;
            record.hurtIn = event.getAmount();
            record.threadAtHurt = Thread.currentThread().getName();
            if (record.contextEnabled && record.context == null) {
                record.context = ContextSnapshot.capture(event.getEntity(), event.getSource());
            }
            markThread(record);
            record.addTimeline("[HIGHEST] LivingHurtEvent: 进入=" + ProbeFormatter.f(record.hurtIn));
        }
    }

    public static void onHurtLowest(LivingHurtEvent event) {
        if (!ProbeState.enabled()) {
            return;
        }
        if (isClientSide(event.getEntity())) {
            AmountCapture.clear(event);
            return;
        }
        // 先收集;即使记录因为并发/过滤不存在,也要清掉 ThreadLocal 里的写入。
        List<AmountWrite> writes = AmountCapture.collect(event, "LivingHurtEvent");
        AmountCapture.clear(event);

        synchronized (LOCK) {
            HitRecord record = top(event.getEntity());
            if (record == null) {
                return;
            }
            record.hurtOut = event.getAmount();
            record.hurtCanceled = event.isCanceled();
            record.hurtWrites.addAll(writes);
            record.threadAtHurt = Thread.currentThread().getName();
            markThread(record);

            if (record.isModified() && writes.isEmpty()) {
                // amount 变了但没有走 setAmount:可能是反射/AT 直接写字段。抓一次栈作为兜底。
                record.fallbackCallSite = AmountCapture.fallbackCallSite();
            }

            StringBuilder line = new StringBuilder("[LOWEST]  LivingHurtEvent: ")
                    .append(ProbeFormatter.f(record.hurtIn)).append(" -> ").append(ProbeFormatter.f(record.hurtOut))
                    .append(" (").append(ProbeFormatter.signed(record.hurtOut - record.hurtIn)).append(") ")
                    .append(record.hurtCanceled ? "已取消" : "未取消");
            if (record.isModified()) {
                AmountWrite last = record.lastHurtWrite();
                line.append("  改动点=").append(last != null ? last.callSite() : "未捕获");
            }
            record.addTimeline(line.toString());
        }
    }

    public static void onDamageHighest(LivingDamageEvent event) {
        if (!ProbeState.enabled()) {
            return;
        }
        if (isClientSide(event.getEntity())) {
            return;
        }
        synchronized (LOCK) {
            HitRecord record = top(event.getEntity());
            if (record == null) {
                return;
            }
            record.damageSeen = true;
            record.damageIn = event.getAmount();
            record.threadAtDamage = Thread.currentThread().getName();
            if (record.target != null) {
                record.healthAtDamage = record.target.getHealth();
                record.absorptionAtDamage = record.target.getAbsorptionAmount();
            }
            if (event.isCanceled()) {
                record.damageCanceled = true;
            }
            markThread(record);
            record.addTimeline("[HIGHEST] LivingDamageEvent: 进入=" + ProbeFormatter.f(record.damageIn));
        }
    }

    public static void onDamageLowest(LivingDamageEvent event) {
        if (!ProbeState.enabled()) {
            return;
        }
        if (isClientSide(event.getEntity())) {
            AmountCapture.clear(event);
            return;
        }
        List<AmountWrite> writes = AmountCapture.collect(event, "LivingDamageEvent");
        AmountCapture.clear(event);

        synchronized (LOCK) {
            HitRecord record = top(event.getEntity());
            if (record == null) {
                return;
            }
            record.damageOut = event.getAmount();
            record.damageCanceled = event.isCanceled();
            record.damageWrites.addAll(writes);
            record.threadAtDamage = Thread.currentThread().getName();
            markThread(record);
            record.addTimeline("[LOWEST]  LivingDamageEvent: 实际=" + ProbeFormatter.f(record.damageOut)
                    + (record.damageCanceled ? " 已取消" : " 未取消"));
        }
    }

    public static void onServerTickStart() {
        serverTick++;
    }

    public static void onActuallyHurtHead(LivingEntity entity, DamageSource source, float amount) {
        if (!ProbeState.enabled() || isClientSide(entity)) {
            return;
        }
        synchronized (LOCK) {
            HitRecord record = top(entity);
            if (record == null) {
                return;
            }
            record.actuallySeen = true;
            record.actuallyCalls++;
            if (Float.isNaN(record.actuallyIn)) {
                record.actuallyIn = amount;
                record.actuallyHealthBefore = entity.getHealth();
            }
            record.actuallyInSum = Float.isNaN(record.actuallyInSum)
                    ? amount : record.actuallyInSum + amount;
            record.threadAtActually = Thread.currentThread().getName();
            markThread(record);
            record.addTimeline("[HEAD] actuallyHurt: 入参=" + ProbeFormatter.f(amount)
                    + "(事件前) 血=" + ProbeFormatter.f(entity.getHealth()));
        }
    }

    public static void onActuallyHurtTail(LivingEntity entity, DamageSource source, float amount) {
        if (isClientSide(entity)) {
            return;
        }
        synchronized (LOCK) {
            HitRecord record = top(entity);
            if (record == null) {
                return;
            }
            record.actuallyHealthAfter = entity.getHealth();
            if (Float.isNaN(record.actuallyHealthBefore)) {
                // HEAD 没跑到(极端情况):至少不要让实扣变成 NaN。
                record.actuallyHealthBefore = record.actuallyHealthAfter;
            }
            record.actuallyOut = record.actuallyHealthBefore - record.actuallyHealthAfter;
            record.actuallyCompleted = true;
            // 过量击杀：目标已死且伤害 > 实扣。这不是 clamp，不能把差额算成“被吞”。
            boolean killed = entity.isDeadOrDying() || entity.getHealth() <= 0.0F;
            float incoming = !Float.isNaN(record.damageIn) ? record.damageIn : record.damageOut;
            if (killed && !Float.isNaN(incoming) && incoming > record.actuallyOut) {
                record.overkill = true;
                record.overkillAmount = incoming - record.actuallyOut;
            }
            record.threadAtActually = Thread.currentThread().getName();
            markThread(record);
            record.addTimeline("[TAIL] actuallyHurt: 实扣=" + ProbeFormatter.f(record.actuallyOut)
                    + " 血=" + ProbeFormatter.f(record.actuallyHealthAfter)
                    + " 调用=" + record.actuallyCalls + "次");
        }
    }

    public static void onServerTickEnd() {
        List<HitRecord> toEmit;
        synchronized (LOCK) {
            for (Deque<HitRecord> dq : OPEN.values()) {
                for (HitRecord record : dq) {
                    settleLocked(record);
                }
            }
            OPEN.clear();
            if (PENDING_EMIT.isEmpty()) {
                return;
            }
            PENDING_EMIT.sort(Comparator.comparingLong(r -> r.id));
            toEmit = new ArrayList<>(PENDING_EMIT);
            PENDING_EMIT.clear();
        }

        for (HitRecord record : toEmit) {
            synchronized (RECENT) {
                RECENT.add(record);
                while (RECENT.size() > RECENT_MAX) {
                    RECENT.remove(0);
                }
            }
            try {
                Output.emit(record);
            }
            catch (Throwable t) {
                DamageProbe.LOGGER.warn("[伤害探针] 输出命中记录 #{} 时出错", record.id, t);
            }
        }
    }

    // ---------------------------------------------------------------- 内部

    /**
     * 客户端侧事件一律不记录。
     *
     * <p>单人游戏里客户端与服务端同 JVM,entityId 完全相同。客户端的弹丸命中会触发一次
     * 0 伤害的攻击事件(Iron's 的法术弹丸 spawn 包只同步 owner/homingTarget,不同步 damage),
     * 若允许它建记录,就会与服务端记录共用同一个 entityId 队列,造成串号 ——
     * 表现就是"服务端那条抢到客户端的 0 伤害记录",并显示成 Render thread 异步伤害。</p>
     */
    private static boolean isClientSide(LivingEntity entity) {
        return entity == null || entity.level() == null || entity.level().isClientSide();
    }

    private static HitRecord top(LivingEntity entity) {
        Deque<HitRecord> dq = OPEN.get(entity.getId());
        return dq == null ? null : dq.peekLast();
    }

    private static boolean passesFilter(LivingEntity target, DamageSource source) {
        if (ProbeState.playerOnly()) {
            boolean playerSource = source.getEntity() instanceof Player || source.getDirectEntity() instanceof Player;
            if (!playerSource) {
                return false;
            }
        }
        if (ProbeState.playerTargetOnly() && !(target instanceof Player)) {
            return false;
        }
        return true;
    }

    private static void markThread(HitRecord record) {
        if (!Thread.currentThread().getName().equals(record.threadAtAttack)) {
            record.crossThread = true;
        }
    }

    private static boolean isMainThread() {
        MinecraftServer s = server;
        return s != null && s.isSameThread();
    }

    private static void settleLocked(HitRecord record) {
        if (record.settled) {
            return;
        }
        LivingEntity target = record.target;
        if (target != null) {
            record.healthAfter = target.getHealth();
            record.absorptionAfter = target.getAbsorptionAmount();
            record.dead = target.isDeadOrDying();
        }
        else {
            record.healthAfter = 0.0F;
            record.absorptionAfter = 0.0F;
        }
        record.threadAtSettle = Thread.currentThread().getName();
        if (!record.threadAtSettle.equals(record.threadAtAttack)) {
            record.crossThread = true;
        }
        record.settled = true;
        record.settledTick = serverTick;
        record.target = null;
        record.addTimeline("结算: 血 " + ProbeFormatter.f(record.healthBefore) + " -> "
                + ProbeFormatter.f(record.healthAfter) + "  掉血=" + ProbeFormatter.f(record.healthDrop()));
        PENDING_EMIT.add(record);
    }
}
