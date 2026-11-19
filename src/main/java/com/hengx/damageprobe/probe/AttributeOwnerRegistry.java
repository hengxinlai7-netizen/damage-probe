package com.hengx.damageprobe.probe;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;

/**
 * AttributeInstance 不持有 owner，所以在 LivingEntity#getAttribute / getAttributes 返回时建立
 * AttributeInstance -> LivingEntity 的反查表。mod 要拿实例加修饰符，必然先经过这两个入口。
 */
public final class AttributeOwnerRegistry {

    private static final Map<AttributeInstance, LivingEntity> OWNERS =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<AttributeMap, LivingEntity> MAP_OWNERS =
            Collections.synchronizedMap(new WeakHashMap<>());

    private AttributeOwnerRegistry() {
    }

    public static void register(AttributeInstance instance, LivingEntity owner) {
        if (instance != null && owner != null) {
            OWNERS.put(instance, owner);
        }
    }

    public static void registerAll(AttributeMap map, LivingEntity owner) {
        if (map == null || owner == null) {
            return;
        }
        MAP_OWNERS.put(map, owner);
        try {
            Collection<AttributeInstance> instances = map.getSyncableAttributes();
            for (AttributeInstance instance : instances) {
                register(instance, owner);
            }
        }
        catch (Throwable ignored) {
            // 单个异常不影响属性探针
        }
    }

    /** MobEffect / AttributeMap 内部通过 getInstance 拿实例时也能反查 owner。 */
    public static void registerFromMap(AttributeMap map, AttributeInstance instance) {
        if (map == null || instance == null) {
            return;
        }
        LivingEntity owner = MAP_OWNERS.get(map);
        if (owner != null) {
            register(instance, owner);
        }
    }

    public static LivingEntity owner(AttributeInstance instance) {
        return instance != null ? OWNERS.get(instance) : null;
    }
}
