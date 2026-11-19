package com.hengx.damageprobe.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.hengx.damageprobe.core.AmountCapture;

import net.minecraftforge.event.entity.living.LivingHurtEvent;

/**
 * 只读探针:在 {@code LivingHurtEvent#setAmount} 入口记录"谁改的"。
 *
 * <p>不修改事件行为,也不依赖 EventBus 内部结构。目标类是 Forge 类,不是 SRG,所以
 * {@code remap = false}。</p>
 */
@Mixin(LivingHurtEvent.class)
public abstract class LivingHurtEventMixin {

    @Inject(method = "setAmount", at = @At("HEAD"), remap = false)
    private void damageprobe$captureSetAmount(float amount, CallbackInfo ci) {
        LivingHurtEvent self = (LivingHurtEvent) (Object) this;
        AmountCapture.recordSet(self, "LivingHurtEvent", self.getAmount(), amount);
    }
}
