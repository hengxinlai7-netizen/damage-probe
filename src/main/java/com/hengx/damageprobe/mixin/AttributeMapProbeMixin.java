package com.hengx.damageprobe.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.hengx.damageprobe.probe.AttributeOwnerRegistry;

import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;

/**
 * MobEffect 之类的内部代码直接走 AttributeMap#getInstance 拿实例（不经过
 * LivingEntity#getAttribute），这里补上 AttributeInstance -> LivingEntity 的登记。
 */
@Mixin(AttributeMap.class)
public abstract class AttributeMapProbeMixin {

    @Inject(method = "getInstance", at = @At("RETURN"))
    private void damageprobe$mapInstance(Attribute attribute, CallbackInfoReturnable<AttributeInstance> cir) {
        AttributeOwnerRegistry.registerFromMap((AttributeMap) (Object) this, cir.getReturnValue());
    }
}
