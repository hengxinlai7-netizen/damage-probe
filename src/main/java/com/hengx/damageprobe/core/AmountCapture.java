package com.hengx.damageprobe.core;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import com.hengx.damageprobe.ProbeState;

/**
 * 捕获 {@code LivingHurtEvent#setAmount} / {@code LivingDamageEvent#setAmount} 的调用点。
 *
 * <p>为什么不用反射遍历 EventBus:EventBus 的 listener 列表是内部实现,而且拿到列表也无法
 * 知道"哪一个 listener 改了值"。这里换一个更直接、更可靠的办法:在事件类的 {@code setAmount}
 * 入口注入(Mixin),任何模组只要走官方 API 改 amount,都会在同一线程的栈上留下调用点。</p>
 *
 * <p>栈只在该线程上抓;写入按事件对象身份归档,支持嵌套事件(例如荆棘在 hurt 里再造成伤害)。
 * 同一个"类.方法:行"只记录一次 firstAtSite,避免重复抓整条栈。</p>
 */
public final class AmountCapture {

    private static final int MAX_RECENT = 128;

    private static final ThreadLocal<ArrayDeque<RawWrite>> RECENT = ThreadLocal.withInitial(ArrayDeque::new);
    private static final Set<Long> SEEN_SITES = ConcurrentHashMap.newKeySet();

    private AmountCapture() {
    }

    /** 由 Mixin 在 setAmount 的 HEAD 调用。 */
    public static void recordSet(Object event, String stage, float from, float to) {
        if (!ProbeState.enabled()) {
            return;
        }
        // 神化 AdventureEvents.onDamage 这类"末尾无条件 setAmount(当前值)"会覆盖真正的修改者。
        // 值没变就不算一次"改动",不进入 writes,也不抓栈。
        if (Float.compare(from, to) == 0) {
            return;
        }
        Caller caller = findCaller();
        long hash = caller != null ? caller.hash() : 0L;
        boolean first = caller != null && SEEN_SITES.add(hash);
        AmountWrite write = new AmountWrite(
                stage,
                from,
                to,
                caller != null ? caller.display() : null,
                caller != null ? caller.callSite() : null,
                hash,
                Thread.currentThread().getName(),
                first);

        ArrayDeque<RawWrite> q = RECENT.get();
        synchronized (q) {
            q.addLast(new RawWrite(event, write));
            while (q.size() > MAX_RECENT) {
                q.removeFirst();
            }
        }
    }

    /** 取当前事件在指定阶段的所有写操作(不删除)。 */
    public static List<AmountWrite> collect(Object event, String stage) {
        List<AmountWrite> out = new ArrayList<>();
        ArrayDeque<RawWrite> q = RECENT.get();
        synchronized (q) {
            for (RawWrite raw : q) {
                if (raw.event == event && raw.write.stage().equals(stage)) {
                    out.add(raw.write);
                }
            }
        }
        return out;
    }

    /** 事件处理结束后清掉该事件的写入,避免 ThreadLocal 里越积越多。 */
    public static void clear(Object event) {
        ArrayDeque<RawWrite> q = RECENT.get();
        synchronized (q) {
            q.removeIf(raw -> raw.event == event);
        }
    }

    /**
     * 兜底:如果 amount 变了但没捕获到 setAmount(例如模组用反射/AT 直接写字段),
     * 在 LOWEST 抓一次当前栈,给出"最后一个非基础设施调用点"。
     */
    public static String fallbackCallSite() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        for (StackTraceElement frame : stack) {
            if (!isInfrastructure(frame.getClassName())) {
                return format(frame);
            }
        }
        return null;
    }

    private static Caller findCaller() {
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        // 栈顶是 Thread.getStackTrace -> AmountCapture.recordSet -> Mixin -> EventBus -> 调用者。
        // 跳过所有基础设施帧,第一个剩下的就是真正调用 setAmount 的模组/测试代码。
        for (StackTraceElement frame : stack) {
            if (!isInfrastructure(frame.getClassName())) {
                return new Caller(frame.getClassName(), frame.getMethodName(), frame.getLineNumber());
            }
        }
        return null;
    }

    private static boolean isInfrastructure(String className) {
        return className.startsWith("java.")
                || className.startsWith("javax.")
                || className.startsWith("jdk.")
                || className.startsWith("sun.")
                || className.startsWith("com.sun.")
                || className.startsWith("net.minecraft.")
                || className.startsWith("net.minecraftforge.")
                || className.startsWith("org.spongepowered.")
                || className.startsWith("com.mojang.")
                || className.startsWith("com.hengx.damageprobe.mixin.")
                || className.startsWith("com.hengx.damageprobe.core.")
                || className.startsWith("com.hengx.damageprobe.probe.")
                || className.equals("com.hengx.damageprobe.ProbeState");
    }

    private static String format(StackTraceElement frame) {
        String cls = frame.getClassName();
        String method = frame.getMethodName();
        return frame.getLineNumber() > 0 ? cls + "." + method + ":" + frame.getLineNumber() : cls + "." + method;
    }

    private record RawWrite(Object event, AmountWrite write) {
    }

    private record Caller(String className, String methodName, int line) {
        String display() {
            return className + "." + methodName;
        }

        String callSite() {
            return line > 0 ? className + "." + methodName + ":" + line : className + "." + methodName;
        }

        long hash() {
            long h = 31L * className.hashCode() + methodName.hashCode();
            return 31L * h + line;
        }
    }
}
