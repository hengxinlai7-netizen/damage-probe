package com.hengx.damageprobe.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.hengx.damageprobe.probe.AttributeOwnerRegistry;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;

/** 建立 AttributeInstance -> LivingEntity 反查表。 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityAttributeOwnerMixin {

    @Inject(method = "getAttribute", at = @At("RETURN"))
    private void damageprobe$mapAttribute(Attribute attribute, CallbackInfoReturnable<AttributeInstance> cir) {
        AttributeOwnerRegistry.register(cir.getReturnValue(), (LivingEntity) (Object) this);
    }

    @Inject(method = "getAttributes", at = @At("RETURN"))
    private void damageprobe$mapAttributes(CallbackInfoReturnable<AttributeMap> cir) {
        AttributeOwnerRegistry.registerAll(cir.getReturnValue(), (LivingEntity) (Object) this);
    }}
