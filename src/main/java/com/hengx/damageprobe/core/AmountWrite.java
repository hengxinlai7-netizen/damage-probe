package com.hengx.damageprobe.core;

/**
 * 一次 amount 写操作的快照。
 *
 * @param stage    "LivingHurtEvent" / "LivingDamageEvent"
 * @param from     写之前的值
 * @param to       写之后的值
 * @param caller   调用点类名.方法名(可能带 :行号);null = 没抓到
 * @param callSite 完整调用点(类名.方法名:行号)
 * @param siteHash 调用点哈希,用于"同一位置只抓一次栈"的缓存
 * @param thread   执行写操作的线程名
 * @param firstAtSite 是否是本进程里第一次在这个调用点写 amount
 */
public record AmountWrite(
        String stage,
        float from,
        float to,
        String caller,
        String callSite,
        long siteHash,
        String thread,
        boolean firstAtSite) {

    public float delta() {
        return to - from;
    }

    public String callerDisplay() {
        return caller != null ? caller : "未捕获";
    }
}
