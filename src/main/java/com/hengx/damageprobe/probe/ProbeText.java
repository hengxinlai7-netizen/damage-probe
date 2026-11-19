package com.hengx.damageprobe.probe;

import com.hengx.damageprobe.core.AmountCapture;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraftforge.registries.ForgeRegistries;

/** 效果/修饰符探针共用的文本工具。 */
public final class ProbeText {

    private ProbeText() {
    }

    public static String entity(Entity entity) {
        if (entity == null) {
            return "无";
        }
        String type = entity.getType().getDescription().getString();
        String name = entity.getDisplayName().getString();
        String label = name != null && !name.equals(type) ? type + "[" + name + "]" : type;
        return label + "#" + entity.getId() + "(" + entity.getUUID() + ")";
    }

    public static String effect(MobEffect effect) {
        if (effect == null) {
            return "?";
        }
        ResourceLocation id = ForgeRegistries.MOB_EFFECTS.getKey(effect);
        return id != null ? id.toString() : effect.getDescriptionId();
    }

    public static String attribute(Attribute attribute) {
        if (attribute == null) {
            return "?";
        }
        ResourceLocation id = ForgeRegistries.ATTRIBUTES.getKey(attribute);
        return id != null ? id.toString() : attribute.getDescriptionId();
    }

    public static String callSite() {
        String site = AmountCapture.fallbackCallSite();
        return site != null ? site : "未知";
    }

    public static String yesNo(boolean value) {
        return value ? "是" : "否";
    }

    public static String living(LivingEntity entity) {
        return entity != null ? entity(entity) : "未知";
    }
}
