package com.hengx.damageprobe.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.hengx.damageprobe.probe.EffectProbe;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * MobEffect 入口。
 *
 * <p>官方名 / SRG：
 * {@code addEffect(MobEffectInstance)} m_7292_（委托给 2 参版本，不重复记录）、
 * {@code addEffect(MobEffectInstance, Entity)} m_147207_、
 * {@code forceAddEffect(MobEffectInstance, Entity)} m_147215_、
 * {@code removeEffect(MobEffect)} m_21195_、
 * {@code removeAllEffects()} m_21219_。</p>
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityEffectMixin {

    @Inject(
            method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("RETURN"))
    private void damageprobe$addEffect(MobEffectInstance instance, Entity source,
                                       CallbackInfoReturnable<Boolean> cir) {
        EffectProbe.onAdd((LivingEntity) (Object) this, instance, source, cir.getReturnValueZ(), "addEffect");
    }

    @Inject(method = "forceAddEffect", at = @At("TAIL"))
    private void damageprobe$forceAddEffect(MobEffectInstance instance, Entity source, CallbackInfo ci) {
        EffectProbe.onForce((LivingEntity) (Object) this, instance, source);
    }

    @Inject(method = "removeEffect", at = @At("RETURN"))
    private void damageprobe$removeEffect(MobEffect effect, CallbackInfoReturnable<Boolean> cir) {
        EffectProbe.onRemove((LivingEntity) (Object) this, effect, cir.getReturnValueZ());
    }

    @Inject(method = "removeAllEffects", at = @At("RETURN"))
    private void damageprobe$removeAllEffects(CallbackInfoReturnable<Boolean> cir) {
        EffectProbe.onRemoveAll((LivingEntity) (Object) this, cir.getReturnValueZ());
    }
}
