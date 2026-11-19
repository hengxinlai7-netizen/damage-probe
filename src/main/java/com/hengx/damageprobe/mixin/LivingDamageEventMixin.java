package com.hengx.damageprobe.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.hengx.damageprobe.core.AmountCapture;

import net.minecraftforge.event.entity.living.LivingDamageEvent;

/**
 * 同上,记录 {@code LivingDamageEvent#setAmount} 的调用点。
 * 大多数模组在 LivingHurtEvent 改伤害;这里能抓到在最终阶段再补一刀的模组。
 */
@Mixin(LivingDamageEvent.class)
public abstract class LivingDamageEventMixin {

    @Inject(method = "setAmount", at = @At("HEAD"), remap = false)
    private void damageprobe$captureSetAmount(float amount, CallbackInfo ci) {
        LivingDamageEvent self = (LivingDamageEvent) (Object) this;
        AmountCapture.recordSet(self, "LivingDamageEvent", self.getAmount(), amount);
    }
}
