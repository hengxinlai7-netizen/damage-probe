package com.hengx.damageprobe.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.hengx.damageprobe.ProbeConfig;
import com.hengx.damageprobe.ProbeState;

import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * 一条命中记录:从 LivingAttackEvent 建立,到 tick 结算时补齐真实掉血。
 *
 * <p>同一个实体的记录用 deque 保存;下一次攻击到来时先结算上一条(此时 health 已经
 * 是上一次 setHealth 之后的值),因此同一 tick 的多次命中不会互相覆盖。</p>
 */
public final class HitRecord {

    public final long id;
    public final int entityId;
    public final long tickRecorded;

    /** 结算前持有实体引用;结算后置 null,避免 recent 列表拖住实体。 */
    public LivingEntity target;

    public final String targetType;
    public final String targetName;
    public final String customName;
    public final boolean targetIsPlayer;
    public final UUID uuid;
    public final Vec3 pos;
    public final String dimension;
    public final Set<String> targetTags;

    public final String sourceId;
    public final String directEntity;
    public final String causingEntity;
    public final String causingLabel;
    public final int causingEntityId;

    public final boolean projectile;
    public final boolean magic;
    public final boolean explosion;
    public final boolean fire;
    public final boolean freezing;
    public final boolean lightning;
    public final boolean bypassArmor;
    public final boolean bypassInvulnerability;
    public final boolean bypassResistance;
    public final boolean bypassEnchantments;
    public final boolean bypassEffects;
    public final boolean bypassCooldown;
    public final boolean bypassShield;

    public final float attackAmount;
    public float healthBefore;
    public float maxHealth;
    public float absorptionBefore;

    public boolean attackCanceled;
    public boolean hurtSeen;
    public float hurtIn = Float.NaN;
    public float hurtOut = Float.NaN;
    public boolean hurtCanceled;

    public boolean damageSeen;
    public float damageIn = Float.NaN;
    public float damageOut = Float.NaN;
    public boolean damageCanceled;
    public float healthAtDamage = Float.NaN;
    public float absorptionAtDamage = Float.NaN;

    /** 第 4 阶段：actuallyHurt 的真实扣血。 */
    public boolean actuallySeen;
    public boolean actuallyCompleted;
    public int actuallyCalls;
    public float actuallyIn = Float.NaN;
    public float actuallyInSum = Float.NaN;
    public float actuallyHealthBefore = Float.NaN;
    public float actuallyHealthAfter = Float.NaN;
    public float actuallyOut = Float.NaN;
    /** 过量击杀：目标死亡且 damageIn > 实扣；此时差额不是被吞，而是血不够。 */
    public boolean overkill;
    public float overkillAmount = Float.NaN;
    public String threadAtActually;

    public final String threadAtAttack;
    public final boolean mainThread;
    public String threadAtHurt;
    public String threadAtDamage;
    public String threadAtSettle;
    public boolean crossThread;

    public final boolean verboseAtHit;
    public final boolean contextEnabled;
    public ContextSnapshot context;
    public final List<AmountWrite> hurtWrites = new ArrayList<>();
    public final List<String> linked = new ArrayList<>();
    public final List<AmountWrite> damageWrites = new ArrayList<>();
    public final List<String> timeline = new ArrayList<>();

    public float healthAfter = Float.NaN;
    public float absorptionAfter = Float.NaN;
    public boolean dead;
    public boolean settled;
    public long settledTick = -1L;
    public String fallbackCallSite;

    public HitRecord(LivingEntity target, DamageSource source, float attackAmount, long tickRecorded, boolean mainThread) {
        this.id = ProbeState.nextId();
        this.entityId = target.getId();
        this.tickRecorded = tickRecorded;
        this.target = target;

        this.targetType = target.getType().getDescription().getString();
        this.targetName = target.getDisplayName().getString();
        this.customName = target.getCustomName() != null ? target.getCustomName().getString() : null;
        this.targetIsPlayer = target instanceof Player;
        this.uuid = target.getUUID();
        this.pos = target.position();
        this.dimension = target.level().dimension().location().toString();
        this.targetTags = new HashSet<>(target.getTags());

        this.sourceId = source.typeHolder().unwrapKey()
                .map(key -> key.location().toString())
                .orElseGet(source::getMsgId);
        this.directEntity = describe(source.getDirectEntity());
        this.causingEntity = describe(source.getEntity());
        this.causingLabel = shortLabel(source.getEntity());
        this.causingEntityId = source.getEntity() != null ? source.getEntity().getId() : -1;

        this.projectile = source.is(DamageTypeTags.IS_PROJECTILE);
        this.magic = source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC);
        this.explosion = source.is(DamageTypeTags.IS_EXPLOSION);
        this.fire = source.is(DamageTypeTags.IS_FIRE);
        this.freezing = source.is(DamageTypeTags.IS_FREEZING);
        this.lightning = source.is(DamageTypeTags.IS_LIGHTNING);
        this.bypassArmor = source.is(DamageTypeTags.BYPASSES_ARMOR);
        this.bypassInvulnerability = source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
        this.bypassResistance = source.is(DamageTypeTags.BYPASSES_RESISTANCE);
        this.bypassEnchantments = source.is(DamageTypeTags.BYPASSES_ENCHANTMENTS);
        this.bypassEffects = source.is(DamageTypeTags.BYPASSES_EFFECTS);
        this.bypassCooldown = source.is(DamageTypeTags.BYPASSES_COOLDOWN);
        this.bypassShield = source.is(DamageTypeTags.BYPASSES_SHIELD);

        this.attackAmount = attackAmount;
        this.healthBefore = target.getHealth();
        this.maxHealth = target.getMaxHealth();
        this.absorptionBefore = target.getAbsorptionAmount();

        this.threadAtAttack = Thread.currentThread().getName();
        this.mainThread = mainThread;
        this.verboseAtHit = ProbeState.verbose();
        this.contextEnabled = ProbeConfig.context() || this.verboseAtHit;

        addTimeline("[HIGHEST] LivingAttackEvent: 理论=" + ProbeFormatter.f(attackAmount));
    }

    public void addTimeline(String line) {
        timeline.add(line);
    }

    public AmountWrite lastHurtWrite() {
        return hurtWrites.isEmpty() ? null : hurtWrites.get(hurtWrites.size() - 1);
    }

    public AmountWrite lastDamageWrite() {
        return damageWrites.isEmpty() ? null : damageWrites.get(damageWrites.size() - 1);
    }

    public boolean isModified() {
        return hurtSeen && Float.compare(hurtIn, hurtOut) != 0;
    }

    public boolean isCanceled() {
        return attackCanceled || hurtCanceled || damageCanceled;
    }

    public boolean isResolved() {
        return damageSeen || attackCanceled || hurtCanceled;
    }

    public boolean isAsync() {
        return !mainThread || crossThread;
    }

    public float healthDrop() {
        return healthBefore - healthAfter;
    }

    public float absorptionUsed() {
        return absorptionBefore - absorptionAfter;
    }

    public String targetLabel() {
        if (targetIsPlayer) {
            return targetType + "(" + targetName + ")";
        }
        return targetType + (customName != null ? "[" + customName + "]" : "");
    }

    public String sourceLabel() {
        if (causingLabel != null) {
            return causingLabel;
        }
        if (directEntity != null) {
            return directEntity;
        }
        return sourceId;
    }

    public String flagString() {
        return "弹射=" + yesNo(projectile)
                + " 魔法=" + yesNo(magic)
                + " 爆炸=" + yesNo(explosion)
                + " 火焰=" + yesNo(fire)
                + " 冰冻=" + yesNo(freezing)
                + " 闪电=" + yesNo(lightning)
                + " 穿甲=" + yesNo(bypassArmor)
                + " 穿无敌=" + yesNo(bypassInvulnerability)
                + " 穿抗性=" + yesNo(bypassResistance)
                + " 穿附魔=" + yesNo(bypassEnchantments)
                + " 穿效果=" + yesNo(bypassEffects)
                + " 穿冷却=" + yesNo(bypassCooldown)
                + " 穿盾=" + yesNo(bypassShield)
                + " 穿魔法=" + yesNo(magic);
    }

    private static String yesNo(boolean value) {
        return value ? "是" : "否";
    }

    private static String describe(Entity entity) {
        if (entity == null) {
            return null;
        }
        String type = entity.getType().getDescription().getString();
        String name = entity.getDisplayName().getString();
        String label = name != null && !name.equals(type) ? type + "[" + name + "]" : type;
        return label + "#" + entity.getId() + "(" + entity.getUUID() + ")";
    }

    private static String shortLabel(Entity entity) {
        if (entity == null) {
            return null;
        }
        if (entity instanceof Player) {
            return entity.getDisplayName().getString();
        }
        String type = entity.getType().getDescription().getString();
        String name = entity.getDisplayName().getString();
        return name != null && !name.equals(type) ? type + "[" + name + "]" : type;
    }

}
