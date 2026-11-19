package com.hengx.damageprobe.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.hengx.damageprobe.ProbeState;

/**
 * 纯文本格式化。日志和聊天栏共用同一份文本,避免两边看到的信息不一致。
 */
public final class ProbeFormatter {

    private ProbeFormatter() {
    }

    /** 伤害数值:整数显示 20.0,小数最多 3 位并去掉尾零。 */
    public static String f(double value) {
        if (Double.isNaN(value)) {
            return "?";
        }
        if (Math.abs(value - Math.rint(value)) < 1.0E-6D) {
            return String.format(Locale.ROOT, "%.1f", value);
        }
        String s = String.format(Locale.ROOT, "%.3f", value);
        while (s.endsWith("0")) {
            s = s.substring(0, s.length() - 1);
        }
        if (s.endsWith(".")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    public static String signed(double value) {
        return (value >= 0.0D ? "+" : "") + f(value);
    }

    /** 摘要用一位小数；详细阶段仍用 f() 保留更多精度。 */
    public static String f1(double value) {
        if (Double.isNaN(value)) {
            return "?";
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }

    /** 归属里 0 不显示 +0.0,直接 0.0；其余保留一位小数并带符号。 */
    public static String delta(double value) {
        if (Math.abs(value) < 0.05D) {
            return "0.0";
        }
        return String.format(Locale.ROOT, "%+.1f", value);
    }

    public static String summary(HitRecord r) {
        return summary(r, true);
    }

    /** 新摘要:理论 / hurt出 / damage / 实扣 / 掉血 / 归属 / 线程。 */
    public static String summary(HitRecord r, boolean withAttribution) {
        StringBuilder sb = new StringBuilder();
        if (r.isAsync()) {
            sb.append("[异步!] ");
        }
        sb.append(r.sourceLabel()).append(" → ").append(r.targetLabel());
        sb.append(" | 理论 ").append(f1(r.attackAmount));
        sb.append(" | hurt出 ").append(r.hurtSeen ? f1(r.hurtOut) : "-");
        sb.append(" | damage ").append(r.damageSeen ? f1(r.damageOut) : "-");
        sb.append(" | 实扣 ").append(actualText(r));
        sb.append(" | 掉血 ").append(f1(r.healthDrop()));
        sb.append(" | 血 ").append(f1(r.healthBefore)).append("→").append(f1(r.healthAfter))
                .append("/").append(f1(r.maxHealth));

        if (r.absorptionBefore > 0.0F || r.absorptionUsed() > 0.0F) {
            sb.append(" | 吸收 ").append(f(r.absorptionUsed()));
        }

        String cancel = cancelStage(r);
        if (cancel != null) {
            sb.append(" | 取消(").append(cancel).append(")");
        }

        if (withAttribution) {
            String attribution = attribution(r);
            if (attribution != null && !attribution.isEmpty()) {
                sb.append(" | 归属: ").append(attribution);
            }
        }

        if (r.dead) {
            sb.append(" | 死亡");
        }
        sb.append(" | 线程=").append(r.threadAtAttack);
        if (!r.mainThread) {
            sb.append(" (非主线程!)");
        }
        else if (r.isAsync()) {
            sb.append(" (中间阶段换了线程!)");
        }
        return sb.toString();
    }

    private static String actualText(HitRecord r) {
        if (!r.actuallyCompleted) {
            return r.actuallySeen ? "?" : "-";
        }
        if (r.overkill) {
            return f1(r.actuallyOut) + "(过量击杀,超出剩余血量 " + f1(r.overkillAmount) + ")";
        }
        return f1(r.actuallyOut);
    }

    /**
     * 归属分析：理论 → hurt 出口 → damage 入口 → actuallyHurt 实扣 → tick 结算。
     *
     * <p>“护甲段”= damageIn / hurtOut，差值为 damageIn - hurtOut，可正可负；
     * “实扣”= 实扣 - damageIn，被 setHealth/托管血量 clamp 时是负数，
     * 过量击杀时显示“过量”；“结算”= 掉血 - 实扣，正常为 0。</p>
     */
    public static String attribution(HitRecord r) {
        if (r.attackCanceled || r.hurtCanceled) {
            String stage = cancelStage(r);
            return "取消(" + (stage != null ? stage : "?") + ")";
        }
        if (!r.hurtSeen && !r.damageSeen && !r.actuallyCompleted) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        if (r.hurtSeen && !Float.isNaN(r.hurtOut) && !Float.isNaN(r.attackAmount)) {
            double ratio = r.attackAmount == 0.0D ? Double.NaN : r.hurtOut / (double) r.attackAmount;
            sb.append("事件×");
            sb.append(Double.isNaN(ratio) ? "?" : String.format(Locale.ROOT, "%.2f", ratio));
            double diff = r.hurtOut - r.attackAmount;
            if (Math.abs(diff) >= 0.05D) {
                sb.append("(").append(delta(diff)).append(")");
            }
            sb.append(' ');
        }
        if (r.damageSeen) {
            double base = r.hurtSeen ? r.hurtOut : r.attackAmount;
            double damageIn = !Float.isNaN(r.damageIn) ? r.damageIn : r.damageOut;
            if (!Double.isNaN(base) && !Double.isNaN(damageIn)) {
                double ratio = base == 0.0D
                        ? (damageIn == 0.0D ? 1.0D : Double.NaN)
                        : damageIn / base;
                sb.append("护甲段×").append(ratioText(ratio))
                        .append("(").append(delta(damageIn - base)).append(") ");
            }
        }
        if (r.damageSeen && r.actuallyCompleted) {
            if (r.overkill) {
                sb.append("实扣 过量 ");
            }
            else {
                double damageIn = !Float.isNaN(r.damageIn) ? r.damageIn : r.damageOut;
                if (!Double.isNaN(damageIn)) {
                    sb.append("实扣").append(delta(r.actuallyOut - damageIn)).append(' ');
                }
            }
        }
        if (r.actuallyCompleted) {
            sb.append("结算").append(delta(r.healthDrop() - r.actuallyOut));
        }
        else if (r.damageSeen) {
            sb.append("结算").append(delta(r.healthDrop() - r.damageOut));
        }
        return sb.toString().trim();
    }

    private static String ratioText(double ratio) {
        return Double.isNaN(ratio) ? "?" : String.format(Locale.ROOT, "%.2f", ratio);
    }

    /** 详细模式:每个阶段一行。 */
    public static List<String> details(HitRecord r) {
        List<String> lines = new ArrayList<>();
        lines.add("=== 命中记录 #" + r.id + " ===");

        StringBuilder target = new StringBuilder("  目标: ").append(r.targetLabel())
                .append(" (uuid=").append(r.uuid)
                .append(", pos=").append(f(r.pos.x)).append(",").append(f(r.pos.y)).append(",").append(f(r.pos.z))
                .append(", dim=").append(r.dimension).append(")")
                .append(" 血 ").append(f(r.healthBefore)).append("/").append(f(r.maxHealth));
        if (r.absorptionBefore > 0.0F) {
            target.append(" 吸收=").append(f(r.absorptionBefore));
        }
        lines.add(target.toString());

        lines.add("  来源: " + r.sourceId + " [" + r.flagString() + "]"
                + " 直接=" + (r.directEntity != null ? r.directEntity : "无")
                + " 造成者=" + (r.causingEntity != null ? r.causingEntity : "无"));

        StringBuilder thread = new StringBuilder("  线程: ").append(r.threadAtAttack)
                .append(r.mainThread ? " (主线程)" : " (非主线程!!! 这通常是模组在异步线程改血)");
        if (r.isAsync() && r.mainThread) {
            thread.append(" [hurt=").append(r.threadAtHurt)
                    .append(", damage=").append(r.threadAtDamage)
                    .append(", actually=").append(r.threadAtActually).append("]");
        }
        lines.add(thread.toString());

        lines.add("  [HIGHEST] LivingAttackEvent: 理论=" + f(r.attackAmount)
                + (r.attackCanceled ? " 已取消" : ""));

        if (r.hurtSeen) {
            lines.add("  [HIGHEST] LivingHurtEvent: 进入=" + f(r.hurtIn)
                    + (r.hurtCanceled ? " 已取消" : ""));
            for (int i = 0; i < r.hurtWrites.size(); i++) {
                AmountWrite w = r.hurtWrites.get(i);
                lines.add("  改动#" + (i + 1) + ": " + f(w.from()) + " -> " + f(w.to())
                        + " (" + signed(w.delta()) + ") @ " + w.callSite()
                        + (w.firstAtSite() ? " (首次)" : ""));
            }
            StringBuilder hurt = new StringBuilder("  [LOWEST]  LivingHurtEvent: ")
                    .append(f(r.hurtIn)).append(" -> ").append(f(r.hurtOut))
                    .append(" (").append(signed(r.hurtOut - r.hurtIn)).append(") ")
                    .append(r.hurtCanceled ? "已取消" : "未取消");
            if (r.isModified()) {
                AmountWrite last = r.lastHurtWrite();
                if (last != null && last.caller() != null) {
                    hurt.append("  改动点=").append(last.callSite());
                }
                else if (r.fallbackCallSite != null) {
                    hurt.append("  改动点=未捕获(可能直接写字段) 调用点=").append(r.fallbackCallSite);
                }
                else {
                    hurt.append("  改动点=未捕获");
                }
            }
            lines.add(hurt.toString());
        }
        else {
            lines.add("  [--]      LivingHurtEvent: 未触发(攻击在 hurt 前被取消,或目标无敌)");
        }

        if (r.damageSeen) {
            lines.add("  [HIGHEST] LivingDamageEvent: 进入=" + f(r.damageIn));
            for (int i = 0; i < r.damageWrites.size(); i++) {
                AmountWrite w = r.damageWrites.get(i);
                lines.add("  改动#" + (i + 1) + ": " + f(w.from()) + " -> " + f(w.to())
                        + " (" + signed(w.delta()) + ") @ " + w.callSite()
                        + (w.firstAtSite() ? " (首次)" : ""));
            }
            lines.add("  [LOWEST]  LivingDamageEvent: 实际=" + f(r.damageOut)
                    + (r.damageCanceled ? " 已取消" : " 未取消"));
        }
        else {
            lines.add("  [--]      LivingDamageEvent: 未触发(伤害为 0,或 LivingHurtEvent 被取消)");
        }

        if (r.actuallySeen) {
            lines.add("  [HEAD]   actuallyHurt: 入参=" + f(r.actuallyIn)
                    + "(事件前) 血=" + f(r.actuallyHealthBefore));
            if (r.actuallyCompleted) {
                StringBuilder tail = new StringBuilder("  [TAIL]   actuallyHurt: 实扣=")
                        .append(f(r.actuallyOut))
                        .append(" 血=").append(f(r.actuallyHealthAfter))
                        .append(" 调用=").append(r.actuallyCalls).append("次");
                if (r.overkill) {
                    tail.append(" (过量击杀,超出剩余血量 ").append(f(r.overkillAmount)).append(")");
                }
                lines.add(tail.toString());
            }
            else {
                lines.add("  [TAIL]   actuallyHurt: 未返回(异常/强制中断)");
            }
        }
        else {
            lines.add("  [--]      actuallyHurt: 未触发(伤害在 hurt/damage 层被取消)");
        }

        StringBuilder settle = new StringBuilder("  结算: 血 ")
                .append(f(r.healthBefore)).append(" -> ").append(f(r.healthAfter))
                .append("  掉血=").append(f(r.healthDrop()))
                .append("  吸收=").append(f(r.absorptionUsed()))
                .append("  实扣=").append(r.actuallyCompleted ? f(r.actuallyOut) : "-");
        if (r.dead) {
            settle.append("  (死亡)");
        }
        settle.append("  线程=").append(r.threadAtSettle);
        lines.add(settle.toString());

        if (ProbeState.selftest()) {
            lines.add("  tick=" + r.tickRecorded + " settledTick=" + r.settledTick
                    + " entityId=" + r.entityId + " verboseAtHit=" + r.verboseAtHit);
        }
        return lines;
    }

    public static List<String> truncate(List<String> lines, int maxLines) {
        int max = Math.max(1, maxLines);
        if (lines.size() <= max) {
            return lines;
        }
        if (max <= 2) {
            return new ArrayList<>(lines.subList(0, max));
        }
        // 保留每段开头 + 最后 2 行(actuallyHurt TAIL / 结算),中间省略。
        int keepTail = Math.min(2, max - 1);
        int head = max - keepTail - 1;
        List<String> out = new ArrayList<>(lines.subList(0, head));
        out.add("  ... 已省略 " + (lines.size() - head - keepTail) + " 行 (maxLinesPerHit=" + max + ")");
        out.addAll(lines.subList(lines.size() - keepTail, lines.size()));
        return out;
    }

    public static String shortCaller(String full) {
        if (full == null || full.isEmpty()) {
            return "?";
        }
        int last = full.lastIndexOf('.');
        if (last <= 0) {
            return full;
        }
        int prev = full.lastIndexOf('.', last - 1);
        return prev >= 0 ? full.substring(prev + 1) : full;
    }

    private static String cancelStage(HitRecord r) {
        if (r.attackCanceled) {
            return "LivingAttackEvent";
        }
        if (r.hurtCanceled) {
            return "LivingHurtEvent";
        }
        if (r.damageCanceled) {
            return "LivingDamageEvent";
        }
        return null;
    }
}
