package com.hengx.damageprobe.probe;

import com.hengx.damageprobe.core.ProbeEngine;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/** MobEffect 施加/移除的捕获与文本。 */
public final class EffectProbe {

    private EffectProbe() {
    }

    public static void onAdd(LivingEntity target, MobEffectInstance instance, Entity source,
                             boolean accepted, String api) {
        if (target == null || instance == null || target.level().isClientSide()) {
            return;
        }
        String effectId = ProbeText.effect(instance.getEffect());
        int level = instance.getAmplifier() + 1;
        int sourceId = source != null ? source.getId() : -1;
        String sourceText = source != null
                ? ProbeText.entity(source)
                : ("addEffect".equals(api) ? "无(单参 addEffect)" : "无(" + api + ")");
        ProbeEvent.Result result = accepted ? ProbeEvent.Result.APPLIED : ProbeEvent.Result.REJECTED;
        String text = "目标=" + ProbeText.entity(target)
                + " 获得 " + effectId + " " + level + "级 " + instance.getDuration() + "t"
                + " 可见=" + ProbeText.yesNo(instance.isVisible())
                + " 图标=" + ProbeText.yesNo(instance.showIcon())
                + " 来源=" + sourceText
                + " 结果=" + result.label
                + " 调用点=" + ProbeText.callSite();
        String key = api + "|" + target.getId() + "|" + effectId + "|" + level + "|" + sourceId;
        boolean playerRelated = target instanceof Player || source instanceof Player;
        ProbeEventStore.record(new ProbeEvent(ProbeEvent.Category.EFFECT, ProbeEngine.serverTick(),
                target.getId(), sourceId, key, text, result, playerRelated));
    }

    public static void onForce(LivingEntity target, MobEffectInstance instance, Entity source) {
        if (target == null || instance == null || target.level().isClientSide()) {
            return;
        }
        boolean present = target.hasEffect(instance.getEffect());
        String effectId = ProbeText.effect(instance.getEffect());
        int level = instance.getAmplifier() + 1;
        int sourceId = source != null ? source.getId() : -1;
        ProbeEvent.Result result = present ? ProbeEvent.Result.APPLIED : ProbeEvent.Result.REJECTED;
        String text = "目标=" + ProbeText.entity(target)
                + " 强制获得 " + effectId + " " + level + "级 " + instance.getDuration() + "t"
                + " 来源=" + (source != null ? ProbeText.entity(source) : "无(forceAddEffect)")
                + " 结果=" + (present ? "已强制施加" : "被拒绝")
                + " 调用点=" + ProbeText.callSite();
        String key = "forceAddEffect|" + target.getId() + "|" + effectId + "|" + level + "|" + sourceId;
        boolean playerRelated = target instanceof Player || source instanceof Player;
        ProbeEventStore.record(new ProbeEvent(ProbeEvent.Category.EFFECT, ProbeEngine.serverTick(),
                target.getId(), sourceId, key, text, result, playerRelated));
    }

    public static void onRemove(LivingEntity target, MobEffect effect, boolean removed) {
        if (target == null || effect == null || target.level().isClientSide()) {
            return;
        }
        String effectId = ProbeText.effect(effect);
        ProbeEvent.Result result = removed ? ProbeEvent.Result.REMOVED : ProbeEvent.Result.NONE;
        String text = "目标=" + ProbeText.entity(target)
                + " 移除 " + effectId
                + " 结果=" + (removed ? "已移除" : "未找到(返回 false)")
                + " 调用点=" + ProbeText.callSite();
        String key = "removeEffect|" + target.getId() + "|" + effectId;
        boolean playerRelated = target instanceof Player;
        ProbeEventStore.record(new ProbeEvent(ProbeEvent.Category.EFFECT, ProbeEngine.serverTick(),
                target.getId(), -1, key, text, result, playerRelated));
    }

    public static void onRemoveAll(LivingEntity target, boolean removedAny) {
        if (target == null || target.level().isClientSide()) {
            return;
        }
        ProbeEvent.Result result = removedAny ? ProbeEvent.Result.ALL_REMOVED : ProbeEvent.Result.NONE;
        String text = "目标=" + ProbeText.entity(target)
                + " 移除全部效果"
                + " 结果=" + (removedAny ? "全部移除" : "没有可移除效果")
                + " 调用点=" + ProbeText.callSite();
        String key = "removeAllEffects|" + target.getId();
        boolean playerRelated = target instanceof Player;
        ProbeEventStore.record(new ProbeEvent(ProbeEvent.Category.EFFECT, ProbeEngine.serverTick(),
                target.getId(), -1, key, text, result, playerRelated));
    }
}
