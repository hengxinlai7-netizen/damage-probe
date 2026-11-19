package com.hengx.damageprobe.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.hengx.damageprobe.probe.ModifierProbe;

import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

/**
 * AttributeModifier 入口。
 *
 * <p>官方名 / SRG：
 * addTransientModifier m_22118_、addPermanentModifier m_22125_、
 * removeModifier(AttributeModifier) m_22130_、removeModifier(UUID) m_22120_（内部调用前者）、
 * removeModifiers() m_22132_。</p>
 */
@Mixin(AttributeInstance.class)
public abstract class AttributeInstanceProbeMixin {

    @Inject(method = "addTransientModifier", at = @At("HEAD"))
    private void damageprobe$beforeAddTransient(AttributeModifier modifier, CallbackInfo ci) {
        ModifierProbe.begin((AttributeInstance) (Object) this, "addTransientModifier", modifier);
    }

    @Inject(method = "addTransientModifier", at = @At("RETURN"))
    private void damageprobe$afterAddTransient(AttributeModifier modifier, CallbackInfo ci) {
        ModifierProbe.end((AttributeInstance) (Object) this, "addTransientModifier", modifier);
    }

    @Inject(method = "addPermanentModifier", at = @At("HEAD"))
    private void damageprobe$beforeAddPermanent(AttributeModifier modifier, CallbackInfo ci) {
        ModifierProbe.begin((AttributeInstance) (Object) this, "addPermanentModifier", modifier);
    }

    @Inject(method = "addPermanentModifier", at = @At("RETURN"))
    private void damageprobe$afterAddPermanent(AttributeModifier modifier, CallbackInfo ci) {
        ModifierProbe.end((AttributeInstance) (Object) this, "addPermanentModifier", modifier);
    }

    @Inject(method = "removeModifier", at = @At("HEAD"))
    private void damageprobe$beforeRemove(AttributeModifier modifier, CallbackInfo ci) {
        ModifierProbe.begin((AttributeInstance) (Object) this, "removeModifier", modifier);
    }

    @Inject(method = "removeModifier", at = @At("RETURN"))
    private void damageprobe$afterRemove(AttributeModifier modifier, CallbackInfo ci) {
        ModifierProbe.end((AttributeInstance) (Object) this, "removeModifier", modifier);
    }

    @Inject(method = "removeModifiers", at = @At("HEAD"))
    private void damageprobe$beforeRemoveAll(CallbackInfo ci) {
        ModifierProbe.beginRemoveAll((AttributeInstance) (Object) this);
    }

    @Inject(method = "removeModifiers", at = @At("RETURN"))
    private void damageprobe$afterRemoveAll(CallbackInfo ci) {
        ModifierProbe.endRemoveAll((AttributeInstance) (Object) this);
    }
}
