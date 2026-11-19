package com.hengx.damageprobe.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.hengx.damageprobe.core.ProbeEngine;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;

/**
 * 玩家覆盖了 {@code LivingEntity#actuallyHurt}（官方名 {@code actuallyHurt}，
 * SRG 名同样是 {@code m_6475_}），且不调用 super。只注入 LivingEntity 会漏掉所有玩家目标，
 * 所以这里单独注入 Player 的覆盖版本。
 */
@Mixin(Player.class)
public abstract class PlayerActuallyHurtMixin {

    @Inject(method = "actuallyHurt", at = @At("HEAD"))
    private void damageprobe$actuallyHurtHead(DamageSource source, float amount, CallbackInfo ci) {
        ProbeEngine.onActuallyHurtHead((Player) (Object) this, source, amount);
    }

    @Inject(method = "actuallyHurt", at = @At("TAIL"))
    private void damageprobe$actuallyHurtTail(DamageSource source, float amount, CallbackInfo ci) {
        ProbeEngine.onActuallyHurtTail((Player) (Object) this, source, amount);
    }
}
