package com.hengx.damageprobe.core;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 一次命中发生时的"影响伤害因子"快照。
 *
 * <p>只在 LivingHurtEvent HIGHEST 抓一次；属性遍历有上限，避免模组/整合包属性太多刷屏。</p>
 */
public final class ContextSnapshot {

    private static final int MAX_ATTRS = 8;
    private static final int MAX_EFFECTS = 8;
    private static final Map<Class<?>, String> MANAGED_CACHE = new ConcurrentHashMap<>();

    public final String attackerLabel;
    public final double attackDamage;
    public final List<String> attackerAttributes;
    public final List<String> attackerEffects;

    public final double armor;
    public final double armorToughness;
    public final double maxHealth;
    public final double health;
    public final double absorption;
    public final int invulnerableTime;
    public final List<String> targetEffects;
    public final String healthManaged;

    public final String sourceId;
    public final boolean bypassArmor;
    public final boolean bypassEnchantments;
    public final boolean bypassResistance;
    public final boolean bypassEffects;
    public final boolean bypassInvulnerability;
    public final boolean bypassCooldown;
    public final boolean bypassShield;
    public final boolean bypassMagic;

    private ContextSnapshot(LivingEntity target, DamageSource source) {
        Entity causing = source.getEntity();
        LivingEntity attacker = causing instanceof LivingEntity living
                ? living
                : source.getDirectEntity() instanceof LivingEntity direct ? direct : null;

        this.attackerLabel = attacker != null ? attacker.getDisplayName().getString() : "无";
        this.attackDamage = attacker != null && attacker.getAttribute(Attributes.ATTACK_DAMAGE) != null
                ? attacker.getAttributeValue(Attributes.ATTACK_DAMAGE) : Double.NaN;
        this.attackerAttributes = attributes(attacker);
        this.attackerEffects = effects(attacker);

        this.armor = target.getAttributeValue(Attributes.ARMOR);
        this.armorToughness = target.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        this.maxHealth = target.getMaxHealth();
        this.health = target.getHealth();
        this.absorption = target.getAbsorptionAmount();
        this.invulnerableTime = target.invulnerableTime;
        this.targetEffects = effects(target);
        this.healthManaged = detectManaged(target.getClass());

        this.sourceId = source.typeHolder().unwrapKey()
                .map(key -> key.location().toString())
                .orElseGet(source::getMsgId);
        this.bypassArmor = source.is(DamageTypeTags.BYPASSES_ARMOR);
        this.bypassEnchantments = source.is(DamageTypeTags.BYPASSES_ENCHANTMENTS);
        this.bypassResistance = source.is(DamageTypeTags.BYPASSES_RESISTANCE);
        this.bypassEffects = source.is(DamageTypeTags.BYPASSES_EFFECTS);
        this.bypassInvulnerability = source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
        this.bypassCooldown = source.is(DamageTypeTags.BYPASSES_COOLDOWN);
        this.bypassShield = source.is(DamageTypeTags.BYPASSES_SHIELD);
        // 1.20.1 没有 IS_MAGIC / BYPASSES_MAGIC tag;用魔法伤害类型近似"穿魔法"。
        this.bypassMagic = source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC);
    }

    public static ContextSnapshot capture(LivingEntity target, DamageSource source) {
        if (target == null || source == null) {
            return null;
        }
        try {
            return new ContextSnapshot(target, source);
        }
        catch (Throwable ignored) {
            return null;
        }
    }

    /** 上下文快照固定 3 行；不受 maxLinesPerHit 限制。 */
    public List<String> lines() {
        List<String> out = new ArrayList<>(3);

        StringBuilder a = new StringBuilder("上下文[攻击者]: ").append(attackerLabel)
                .append(" 攻击力=").append(Double.isNaN(attackDamage) ? "-" : ProbeFormatter.f(attackDamage))
                .append(" 非默认属性=").append(attackerAttributes.isEmpty() ? "无" : String.join(", ", attackerAttributes))
                .append(" 效果=").append(attackerEffects.isEmpty() ? "无" : String.join(", ", attackerEffects));
        out.add(a.toString());

        StringBuilder t = new StringBuilder("上下文[目标]: 护甲=").append(ProbeFormatter.f(armor))
                .append(" 韧性=").append(ProbeFormatter.f(armorToughness))
                .append(" 血=").append(ProbeFormatter.f(health)).append("/").append(ProbeFormatter.f(maxHealth))
                .append(" 吸收=").append(ProbeFormatter.f(absorption))
                .append(" 无敌帧=").append(invulnerableTime)
                .append(" 效果=").append(targetEffects.isEmpty() ? "无" : String.join(", ", targetEffects))
                .append(" 血量托管=").append(healthManaged);
        out.add(t.toString());

        out.add("上下文[来源]: " + sourceId
                + " [穿甲=" + yesNo(bypassArmor)
                + " 穿附魔=" + yesNo(bypassEnchantments)
                + " 穿抗性=" + yesNo(bypassResistance)
                + " 穿效果=" + yesNo(bypassEffects)
                + " 穿无敌=" + yesNo(bypassInvulnerability)
                + " 穿冷却=" + yesNo(bypassCooldown)
                + " 穿盾=" + yesNo(bypassShield)
                + " 穿魔法=" + yesNo(bypassMagic) + "]");
        return out;
    }

    private static List<String> attributes(LivingEntity attacker) {
        List<String> out = new ArrayList<>();
        if (attacker == null) {
            return out;
        }
        int omitted = 0;
        try {
            Collection<AttributeInstance> instances = attacker.getAttributes().getSyncableAttributes();
            for (AttributeInstance instance : instances) {
                try {
                    Attribute attribute = instance.getAttribute();
                    if (attribute == Attributes.ATTACK_DAMAGE) {
                        continue;
                    }
                    double defaultValue = attribute.getDefaultValue();
                    double value = instance.getValue();
                    double base = instance.getBaseValue();
                    if (Math.abs(value - defaultValue) < 1.0E-6D && Math.abs(base - defaultValue) < 1.0E-6D) {
                        continue;
                    }
                    if (out.size() >= MAX_ATTRS) {
                        omitted++;
                        continue;
                    }
                    ResourceLocation id = ForgeRegistries.ATTRIBUTES.getKey(attribute);
                    out.add((id != null ? id.toString() : attribute.getDescriptionId()) + "=" + ProbeFormatter.f(value));
                }
                catch (Throwable ignored) {
                    // 单个模组属性异常不影响整体上下文
                }
            }
        }
        catch (Throwable ignored) {
            return out;
        }
        if (omitted > 0) {
            out.add("…+" + omitted);
        }
        return out;
    }

    private static List<String> effects(LivingEntity entity) {
        List<String> out = new ArrayList<>();
        if (entity == null) {
            return out;
        }
        try {
            int omitted = 0;
            for (MobEffectInstance instance : entity.getActiveEffects()) {
                if (out.size() >= MAX_EFFECTS) {
                    omitted++;
                    continue;
                }
                try {
                    out.add(instance.getEffect().getDisplayName().getString()
                            + " " + (instance.getAmplifier() + 1));
                }
                catch (Throwable ignored) {
                    // ignore
                }
            }
            if (omitted > 0) {
                out.add("…+" + omitted);
            }
        }
        catch (Throwable ignored) {
            // ignore
        }
        return out;
    }

    /**
     * "血量托管"是尽力判断：目标类自己声明了 setHealth/getHealth（官方名或 SRG 名），
     * 通常意味着它覆盖了原版扣血入口。判断不了就记"否"，不反射调用。
     */
    private static String detectManaged(Class<?> type) {
        return MANAGED_CACHE.computeIfAbsent(type, cls -> {
            try {
                for (Method method : cls.getDeclaredMethods()) {
                    if (method.getParameterCount() == 1 && method.getParameterTypes()[0] == float.class) {
                        String name = method.getName();
                        if (name.equals("setHealth") || name.equals("m_21153_")) {
                            return "是(" + cls.getSimpleName() + "#" + name + ")";
                        }
                    }
                    if (method.getParameterCount() == 0) {
                        String name = method.getName();
                        if (name.equals("getHealth") || name.equals("m_21223_")) {
                            return "是(" + cls.getSimpleName() + "#" + name + ")";
                        }
                    }
                }
            }
            catch (Throwable ignored) {
                return "未知";
            }
            return "否";
        });
    }

    private static String yesNo(boolean value) {
        return value ? "是" : "否";
    }
}
