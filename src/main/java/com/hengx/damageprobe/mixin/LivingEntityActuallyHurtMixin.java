package com.hengx.damageprobe.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.hengx.damageprobe.core.ProbeEngine;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * 第 4 阶段:原版 {@code LivingEntity#actuallyHurt}。
 *
 * <p>官方名 {@code actuallyHurt(DamageSource,float)}，SRG 名 {@code m_6475_}。
 * HEAD 记录"进入真正扣血流程的 amount"与扣血前血量；TAIL 记录调用后的血量，
 * 用 {@code healthBefore - healthAfter} 得到真实扣血量（这才是被 clamp/拦截后的结果）。</p>
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityActuallyHurtMixin {

    @Inject(method = "actuallyHurt", at = @At("HEAD"))
    private void damageprobe$actuallyHurtHead(DamageSource source, float amount, CallbackInfo ci) {
        ProbeEngine.onActuallyHurtHead((LivingEntity) (Object) this, source, amount);
    }

    @Inject(method = "actuallyHurt", at = @At("TAIL"))
    private void damageprobe$actuallyHurtTail(DamageSource source, float amount, CallbackInfo ci) {
        ProbeEngine.onActuallyHurtTail((LivingEntity) (Object) this, source, amount);
    }
}
