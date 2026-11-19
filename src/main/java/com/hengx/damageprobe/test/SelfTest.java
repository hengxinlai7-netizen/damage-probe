package com.hengx.damageprobe.test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.hengx.damageprobe.ProbeState;
import com.hengx.damageprobe.core.AmountWrite;
import com.hengx.damageprobe.core.HitRecord;
import com.hengx.damageprobe.core.ProbeEngine;
import com.hengx.damageprobe.core.ProbeFormatter;
import com.hengx.damageprobe.output.Output;
import com.hengx.damageprobe.probe.ProbeEventStore;
import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 服务端自检({@code -Ddamageprobe.selftest=true})。
 *
 * <p>覆盖：正常 / 抗性 / 改伤害 / 多段 / 吸收 / 指令 / 魔法 / 过滤 / 聊天 /
 * 乘 6.42 + 空写 / setHealth clamp。</p>
 */
public final class SelfTest {

    private static final Logger LOG = LoggerFactory.getLogger("DamageProbeSelfTest");
    private static final float EPS = 1.0E-4F;

    private MinecraftServer server;
    private ServerLevel level;
    private int tick;
    private boolean scenariosRun;
    private boolean asserted;
    private boolean done;
    private boolean playerFilterTested;
    private boolean playerTargetTested;
    private boolean filterChecksPassed;
    private int pass;
    private int fail;

    @SubscribeEvent
    public void onServerStarted(ServerStartedEvent event) {
        if (!ProbeState.selftest()) {
            return;
        }
        server = event.getServer();
        level = server.overworld();
        level.setDayTime(18000L);

        GameRules rules = level.getGameRules();
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        rules.getRule(GameRules.RULE_NATURAL_REGENERATION).set(false, server);
        rules.getRule(GameRules.RULE_MOBGRIEFING).set(false, server);

        LOG.info("SELFTEST START: mc=1.20.1 forge=47.2.20 probe=1.2.1 selftest=true");
        LOG.info("SELFTEST CONFIG: {}", ProbeState.describe());
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (!ProbeState.selftest() || event.phase != TickEvent.Phase.START) {
            return;
        }
        tick++;
        if (tick == 10 && !scenariosRun) {
            scenariosRun = true;
            runScenarios();
        }
        else if (tick == 12 && !asserted) {
            asserted = true;
            runAssertions();
        }
        else if (tick == 20 && !done) {
            done = true;
            LOG.info("SELFTEST RESULT: pass={} fail={}", pass, fail);
            LOG.info("SELFTEST DONE");
            if (server != null) {
                server.halt(false);
            }
        }
    }

    // ---------------------------------------------------------------- 场景

    private void runScenarios() {
        ProbeEngine.clearRecent();
        Zombie attacker = spawn("attacker", -5, 0, 100.0F);

        Zombie basic = spawn("damageprobe_basic", 0, 0, 100.0F);
        hit(basic, level.damageSources().mobAttack(attacker), 20.0F);

        Zombie resistance = spawn("damageprobe_resistance", 3, 0, 100.0F);
        resistance.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 600, 1));
        hit(resistance, level.damageSources().mobAttack(attacker), 20.0F);

        // 护甲段负值：17 护甲 + 0.4210526 韧性，把 20 减到 14(×0.70, -6.0)。
        Zombie armor = spawn("damageprobe_armor", 3, 8, 100.0F);
        if (armor.getAttribute(Attributes.ARMOR) != null) {
            armor.getAttribute(Attributes.ARMOR).setBaseValue(17.0D);
        }
        if (armor.getAttribute(Attributes.ARMOR_TOUGHNESS) != null) {
            armor.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(0.42105263157894735D);
        }
        hit(armor, level.damageSources().mobAttack(attacker), 20.0F);

        Zombie fixture = spawn("damageprobe_fixture_target", 6, 0, 100.0F);
        fixture.addTag("damageprobe_fixture");
        // 让这一条的 verboseAtHit=true,验证详细模式会逐阶段输出。
        ProbeState.setVerbose(true);
        hit(fixture, level.damageSources().mobAttack(attacker), 20.0F);
        ProbeState.setVerbose(false);

        Zombie multi = spawn("damageprobe_multi", 9, 0, 100.0F);
        hit(multi, level.damageSources().mobAttack(attacker), 6.0F);
        hit(multi, level.damageSources().mobAttack(attacker), 6.0F);

        Zombie absorption = spawn("damageprobe_absorption", 12, 0, 100.0F);
        absorption.setAbsorptionAmount(4.0F);
        // 详细模式验证"进入 hurt 10 → 最终 damage 6 → 吸收 4"这条阶梯。
        ProbeState.setVerbose(true);
        hit(absorption, level.damageSources().mobAttack(attacker), 10.0F);
        ProbeState.setVerbose(false);

        Zombie magic = spawn("damageprobe_magic", 0, 4, 50.0F);
        hit(magic, level.damageSources().magic(), 5.0F);

        // 乘 6.42 + 末尾空写：lastHurtWrite 必须是 NORMAL 的真实乘法，不能被 LOW 的空写覆盖。
        Zombie multiply = spawn("damageprobe_multiply", 0, 8, 1000.0F);
        multiply.addTag("damageprobe_multiply");
        ProbeState.setVerbose(true);
        hit(multiply, level.damageSources().mobAttack(attacker), 20.0F);
        ProbeState.setVerbose(false);

        // 托管血量：actuallyHurt 入参 3000，但 setHealth 只允许掉 10。
        // 1.20.1 的 MAX_HEALTH 上限是 1024，所以测试血量为 1024。
        ClampedZombie clamp = spawnClamped("damageprobe_clamp", 6, 8, 1024.0F);
        ProbeState.setVerbose(true);
        hit(clamp, level.damageSources().mobAttack(attacker), 3000.0F);
        ProbeState.setVerbose(false);

        // 过量击杀：目标只有 100 血，承受 3000 伤害 → 实扣 100，超出 2900。
        Zombie overkill = spawn("damageprobe_overkill", 9, 8, 100.0F);
        ProbeState.setVerbose(true);
        hit(overkill, level.damageSources().mobAttack(attacker), 3000.0F);
        ProbeState.setVerbose(false);

        Zombie command = spawn("damageprobe_cmd", 3, 4, 50.0F);
        runDamageCommand();

        // 总开关关闭时不应该产生记录。
        Zombie disabled = spawn("damageprobe_disabled", 12, 4, 50.0F);
        ProbeState.setEnabled(Boolean.FALSE);
        hit(disabled, level.damageSources().magic(), 5.0F);
        ProbeState.setEnabled(null);

        // playerOnly 过滤:僵尸来源应被丢弃,玩家来源应记录。
        Zombie mobSource = spawn("damageprobe_mobsource", 6, 4, 50.0F);
        Zombie playerSource = spawn("damageprobe_playersource", 9, 4, 50.0F);
        try {
            ProbeState.setTestPlayerOnly(Boolean.TRUE);
            hit(mobSource, level.damageSources().mobAttack(attacker), 5.0F);
            ProbeState.setTestPlayerOnly(Boolean.FALSE);
            FakePlayer fake = FakePlayerFactory.getMinecraft(level);
            hit(playerSource, level.damageSources().playerAttack(fake), 5.0F);
            playerFilterTested = true;
        }
        catch (Throwable t) {
            LOG.warn("SELFTEST PLAYER FILTER SKIP: {}", t.toString());
        }
        finally {
            ProbeState.setTestPlayerOnly(null);
            ProbeState.setVerbose(false);
        }

        // playerTargetOnly:只记录玩家目标。用一个可受伤的假玩家(不进 PlayerList)。
        try {
            Zombie nonPlayerTarget = spawn("damageprobe_nontarget", 15, 4, 50.0F);
            DamageableCapturingPlayer playerTarget = new DamageableCapturingPlayer(
                    level, new GameProfile(UUID.randomUUID(), "probe_player"));
            playerTarget.setHealth(20.0F);
            playerTarget.setGameMode(GameType.SURVIVAL);
            playerTarget.getAbilities().invulnerable = false;
            playerTarget.setInvulnerable(false);
            playerTarget.addTag("damageprobe_playertarget");
            playerTarget.invulnerableTime = 0;

            ProbeState.setTestPlayerTargetOnly(Boolean.TRUE);
            hit(nonPlayerTarget, level.damageSources().mobAttack(attacker), 5.0F);
            // FakePlayer 的 spawnInvulnerableTime 恒为 60(tick 被 override 成空),
            // 用带 bypasses_invulnerability 的 generic_kill 才能真正打到它。
            DamageSource playerTargetSource = level.damageSources().genericKill();
            boolean playerHurt = hit(playerTarget, playerTargetSource, 5.0F);
            LOG.info("SELFTEST PLAYER TARGET: hurt={} health={} invulnerable={} invulnerableTo={} spectator={} creative={}",
                    playerHurt, playerTarget.getHealth(), playerTarget.isInvulnerable(),
                    playerTarget.isInvulnerableTo(playerTargetSource),
                    playerTarget.isSpectator(), playerTarget.isCreative());
            playerTargetTested = true;
        }
        catch (Throwable t) {
            LOG.warn("SELFTEST PLAYER TARGET SKIP: {}", t.toString());
        }
        finally {
            ProbeState.setTestPlayerTargetOnly(null);
        }

        // ---- 效果探针 / 修饰符探针 ----
        // filter 热切换：默认 players 下，单参 addEffect(zombie) 应被过滤；filter all 后可见。
        Zombie noSourceDefault = spawn("damageprobe_nosource_default", 0, 12, 50.0F);
        boolean noSourceDefaultOk = noSourceDefault.addEffect(
                new MobEffectInstance(MobEffects.WEAKNESS, 200, 0));
        int filterStatusResult = server.getCommands().performPrefixedCommand(
                server.createCommandSourceStack(), "damageprobe filter status");
        boolean filterStatusPlayers = ProbeState.playerRelatedOnly()
                && ProbeState.describe().contains("filter=players");
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "damageprobe filter all");
        boolean filterAll = !ProbeState.playerRelatedOnly();
        Zombie noSourceAll = spawn("damageprobe_nosource_all", 3, 12, 50.0F);
        boolean noSourceAllOk = noSourceAll.addEffect(
                new MobEffectInstance(MobEffects.WEAKNESS, 200, 0));
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "damageprobe filter players");
        boolean filterPlayers = ProbeState.playerRelatedOnly();
        Zombie noSourceRestored = spawn("damageprobe_nosource_restored", 6, 12, 50.0F);
        boolean noSourceRestoredOk = noSourceRestored.addEffect(
                new MobEffectInstance(MobEffects.WEAKNESS, 200, 0));
        filterChecksPassed = filterStatusResult == 1 && filterStatusPlayers && filterAll && filterPlayers;

        CapturingPlayer effectTarget = new CapturingPlayer(level,
                new GameProfile(UUID.randomUUID(), "probe_effect_target"));
        effectTarget.setHealth(20.0F);
        boolean effectApplied = effectTarget.addEffect(
                new MobEffectInstance(MobEffects.DAMAGE_BOOST, 1200, 1), attacker);

        Zombie rejectTarget = spawn("damageprobe_effect_reject", 15, 8, 50.0F);
        boolean effectRejected = rejectTarget.addEffect(
                new MobEffectInstance(MobEffects.REGENERATION, 200, 0), effectTarget);

        CapturingPlayer removeTarget = new CapturingPlayer(level,
                new GameProfile(UUID.randomUUID(), "probe_effect_remove"));
        removeTarget.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 600, 0), effectTarget);
        boolean effectRemoved = removeTarget.removeEffect(MobEffects.DAMAGE_BOOST);

        CapturingPlayer modifierTarget = new CapturingPlayer(level,
                new GameProfile(UUID.randomUUID(), "probe_modifier_target"));
        AttributeInstance health = modifierTarget.getAttribute(Attributes.MAX_HEALTH);
        AttributeModifier healthModifier = new AttributeModifier(
                UUID.randomUUID(), "probe_test", 4.0D, AttributeModifier.Operation.ADDITION);
        if (health != null) {
            health.addTransientModifier(healthModifier);
        }

        // 高频：同一修饰符 100 次重刷（移除/添加），用于合并计数。
        CapturingPlayer hfTarget = new CapturingPlayer(level,
                new GameProfile(UUID.randomUUID(), "probe_modifier_hf"));
        AttributeInstance hfHealth = hfTarget.getAttribute(Attributes.MAX_HEALTH);
        AttributeModifier hfModifier = new AttributeModifier(
                UUID.randomUUID(), "probe_hf", 2.0D, AttributeModifier.Operation.ADDITION);
        if (hfHealth != null) {
            hfHealth.addTransientModifier(hfModifier);
            for (int i = 1; i < 100; i++) {
                hfHealth.removeModifier(hfModifier.getId());
                hfHealth.addTransientModifier(hfModifier);
            }
        }

        // 伤害联动：同一 tick 给目标加力量，再打一下。
        Zombie linkTarget = spawn("damageprobe_link", 12, 8, 200.0F);
        linkTarget.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 1200, 0), effectTarget);
        hit(linkTarget, level.damageSources().playerAttack(effectTarget), 5.0F);

        LOG.info("SELFTEST EFFECT/MOD: applied={} rejected={} removed={}",
                effectApplied, effectRejected, effectRemoved);
        LOG.info("SELFTEST FILTER: status={} statusPlayers={} all={} players={} noSource={}/{}/{}",
                filterStatusResult, filterStatusPlayers, filterAll, filterPlayers,
                noSourceDefaultOk, noSourceAllOk, noSourceRestoredOk);

        LOG.info("SELFTEST SCENARIOS: spawned=20 recordsSoFar={}", ProbeEngine.recentRecords().size());
    }

    private Zombie spawn(String tag, double dx, double dz, float health) {
        Zombie zombie = EntityType.ZOMBIE.create(level);
        if (zombie == null) {
            throw new IllegalStateException("无法创建测试僵尸");
        }
        configure(zombie, tag, dx, dz, health);
        return zombie;
    }

    private ClampedZombie spawnClamped(String tag, double dx, double dz, float health) {
        ClampedZombie zombie = new ClampedZombie(EntityType.ZOMBIE, level);
        configure(zombie, tag, dx, dz, health);
        return zombie;
    }

    private void configure(Zombie zombie, String tag, double dx, double dz, float health) {
        BlockPos spawn = level.getSharedSpawnPos();
        int x = spawn.getX() + (int) dx;
        int z = spawn.getZ() + (int) dz;
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);

        zombie.moveTo(x + 0.5D, y, z + 0.5D, 0.0F, 0.0F);
        zombie.setNoAi(true);
        zombie.setPersistenceRequired();
        zombie.setCustomName(Component.literal(tag));
        zombie.setCustomNameVisible(false);
        zombie.addTag("damageprobe_test");
        zombie.addTag(tag);
        zombie.setInvulnerable(false);
        zombie.setAbsorptionAmount(0.0F);
        if (zombie.getAttribute(Attributes.MAX_HEALTH) != null) {
            zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(health);
        }
        // 僵尸默认 2 点护甲会把 20 点伤害变成 19.68;自检要的是干净的数值。
        if (zombie.getAttribute(Attributes.ARMOR) != null) {
            zombie.getAttribute(Attributes.ARMOR).setBaseValue(0.0D);
        }
        zombie.setHealth(health);
        if (!level.addFreshEntity(zombie)) {
            LOG.warn("SELFTEST SPAWN FAILED: {}", tag);
        }
        LOG.info("SELFTEST SPAWN {} at {}", tag, zombie.blockPosition());
    }

    private boolean hit(LivingEntity target, DamageSource source, float amount) {
        target.invulnerableTime = 0; // 允许同一 tick 多次完整命中
        boolean result = target.hurt(source, amount);
        LOG.info("SELFTEST HIT {} source={} amount={} result={} health={}",
                target.getTags(), source.typeHolder().unwrapKey().map(k -> k.location().toString()).orElse("?"),
                amount, result, target.getHealth());
        return result;
    }

    private void runDamageCommand() {
        String command = "damage @e[type=minecraft:zombie,tag=damageprobe_cmd,limit=1] 7 minecraft:generic";
        try {
            int result = server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
            LOG.info("SELFTEST COMMAND '/{}' result={}", command, result);
        }
        catch (Throwable t) {
            LOG.warn("SELFTEST COMMAND FAILED: {}", command, t);
        }
    }

    // ---------------------------------------------------------------- 断言

    private void runAssertions() {
        ProbeEventStore.flushAll();
        List<HitRecord> recent = ProbeEngine.recentRecords();
        List<String> probeTexts = ProbeEventStore.recentTexts();
        LOG.info("SELFTEST ASSERT: recentRecords={} emitted={} open={} probeEvents={}",
                recent.size(), ProbeState.emittedCount(), ProbeEngine.openCount(), probeTexts.size());

        HitRecord basic = one("damageprobe_basic");
        check("basic.record", basic != null, "record=" + basic);
        if (basic != null) {
            check("basic.attack", near(basic.attackAmount, 20.0F), "attack=" + basic.attackAmount);
            check("basic.damage", near(basic.damageOut, 20.0F), "damage=" + basic.damageOut);
            check("basic.drop", near(basic.healthDrop(), 20.0F), "drop=" + basic.healthDrop());
            check("basic.health", near(basic.healthBefore, 100.0F) && near(basic.healthAfter, 80.0F),
                    "health=" + basic.healthBefore + "->" + basic.healthAfter);
            check("basic.thread", basic.mainThread && !basic.isAsync(), "thread=" + basic.threadAtAttack);
            check("basic.source", "minecraft:mob_attack".equals(basic.sourceId), "source=" + basic.sourceId);
            check("basic.actuallyIn", near(basic.actuallyIn, 20.0F), "actuallyIn=" + basic.actuallyIn);
            check("basic.actuallyOut", basic.actuallyCompleted && near(basic.actuallyOut, 20.0F),
                    "actuallyOut=" + basic.actuallyOut);
            check("basic.actuallyCalls", basic.actuallyCalls == 1, "calls=" + basic.actuallyCalls);
            check("basic.actuallyHealth",
                    near(basic.actuallyHealthBefore, 100.0F) && near(basic.actuallyHealthAfter, 80.0F),
                    "actuallyHealth=" + basic.actuallyHealthBefore + "->" + basic.actuallyHealthAfter);

            String summary = ProbeFormatter.summary(basic);
            check("basic.summary.format",
                    summary.contains("理论 20.0") && summary.contains("hurt出 20.0")
                            && summary.contains("damage 20.0") && summary.contains("实扣 20.0")
                            && summary.contains("掉血 20.0") && summary.contains("血 100.0→80.0/100.0")
                            && summary.contains("归属:") && summary.contains("线程=Server thread"),
                    summary);
            check("basic.attribution",
                    ProbeFormatter.attribution(basic) != null
                            && ProbeFormatter.attribution(basic).contains("护甲段×1.00(0.0)"),
                    "attr=" + ProbeFormatter.attribution(basic));
        }

        HitRecord resistance = one("damageprobe_resistance");
        check("resistance.record", resistance != null, "record=" + resistance);
        if (resistance != null) {
            check("resistance.hurtIn", near(resistance.hurtIn, 20.0F), "hurtIn=" + resistance.hurtIn);
            check("resistance.damage", near(resistance.damageOut, 12.0F), "damage=" + resistance.damageOut);
            check("resistance.drop", near(resistance.healthDrop(), 12.0F), "drop=" + resistance.healthDrop());
            check("resistance.actually", near(resistance.actuallyOut, 12.0F), "actually=" + resistance.actuallyOut);
            check("resistance.attribution",
                    ProbeFormatter.attribution(resistance) != null
                            && ProbeFormatter.attribution(resistance).contains("护甲段×0.60(-8.0)"),
                    "attr=" + ProbeFormatter.attribution(resistance));
            check("resistance.notModified", !resistance.isModified(), "writes=" + resistance.hurtWrites.size());
        }

        HitRecord armor = one("damageprobe_armor");
        check("armor.record", armor != null, "record=" + armor);
        if (armor != null) {
            check("armor.damage", near(armor.damageOut, 14.0F), "damage=" + armor.damageOut);
            check("armor.actually", near(armor.actuallyOut, 14.0F), "actually=" + armor.actuallyOut);
            check("armor.drop", near(armor.healthDrop(), 14.0F), "drop=" + armor.healthDrop());
            check("armor.attribution",
                    ProbeFormatter.attribution(armor) != null
                            && ProbeFormatter.attribution(armor).contains("护甲段×0.70(-6.0)"),
                    "attr=" + ProbeFormatter.attribution(armor));
        }

        HitRecord fixture = one("damageprobe_fixture_target");
        check("fixture.record", fixture != null, "record=" + fixture);
        if (fixture != null) {
            check("fixture.hurtIn", near(fixture.hurtIn, 20.0F), "hurtIn=" + fixture.hurtIn);
            check("fixture.hurtOut", near(fixture.hurtOut, 10.0F), "hurtOut=" + fixture.hurtOut);
            check("fixture.damage", near(fixture.damageOut, 10.0F), "damage=" + fixture.damageOut);
            check("fixture.drop", near(fixture.healthDrop(), 10.0F), "drop=" + fixture.healthDrop());
            check("fixture.actually", near(fixture.actuallyOut, 10.0F), "actually=" + fixture.actuallyOut);
            check("fixture.modified", fixture.isModified(), "modified=" + fixture.isModified());
            AmountWrite last = fixture.lastHurtWrite();
            check("fixture.caller", last != null && last.caller() != null
                            && last.caller().contains("DamageModifierFixture"),
                    "caller=" + (last != null ? last.caller() : "null"));
            check("fixture.verboseAtHit", fixture.verboseAtHit, "verboseAtHit=" + fixture.verboseAtHit);
            List<String> details = ProbeFormatter.details(fixture);
            String joined = String.join("\n", details);
            check("fixture.details.format",
                    joined.contains("[LOWEST]  LivingHurtEvent: 20.0 -> 10.0")
                            && joined.contains("改动点=")
                            && joined.contains("结算: 血 100.0 -> 90.0"),
                    "lines=" + details.size());
            check("fixture.details.truncate",
                    ProbeFormatter.truncate(details, 6).size() == 6
                            && ProbeFormatter.truncate(details, 6).stream().anyMatch(line -> line.contains("已省略")),
                    "truncated=" + ProbeFormatter.truncate(details, 6).size());
        }

        List<HitRecord> multi = byTag("damageprobe_multi");
        check("multi.count", multi.size() == 2, "count=" + multi.size());
        if (multi.size() == 2) {
            check("multi.first", near(multi.get(0).damageOut, 6.0F) && near(multi.get(0).healthDrop(), 6.0F)
                            && near(multi.get(0).healthBefore, 100.0F) && near(multi.get(0).healthAfter, 94.0F),
                    "first=" + multi.get(0).damageOut + "/" + multi.get(0).healthBefore + "->" + multi.get(0).healthAfter);
            check("multi.second", near(multi.get(1).damageOut, 6.0F) && near(multi.get(1).healthDrop(), 6.0F)
                            && near(multi.get(1).healthBefore, 94.0F) && near(multi.get(1).healthAfter, 88.0F),
                    "second=" + multi.get(1).damageOut + "/" + multi.get(1).healthBefore + "->" + multi.get(1).healthAfter);
            check("multi.ids", multi.get(0).id != multi.get(1).id,
                    "ids=" + multi.get(0).id + "," + multi.get(1).id);
        }

        HitRecord absorption = one("damageprobe_absorption");
        check("absorption.record", absorption != null, "record=" + absorption);
        if (absorption != null) {
            check("absorption.damage", near(absorption.damageOut, 6.0F), "damage=" + absorption.damageOut);
            check("absorption.drop", near(absorption.healthDrop(), 6.0F), "drop=" + absorption.healthDrop());
            check("absorption.actuallyIn", near(absorption.actuallyIn, 10.0F), "actuallyIn=" + absorption.actuallyIn);
            check("absorption.actuallyOut", near(absorption.actuallyOut, 6.0F), "actuallyOut=" + absorption.actuallyOut);
            check("absorption.before", near(absorption.absorptionBefore, 4.0F), "before=" + absorption.absorptionBefore);
            check("absorption.after", near(absorption.absorptionAfter, 0.0F), "after=" + absorption.absorptionAfter);
            check("absorption.used", near(absorption.absorptionUsed(), 4.0F), "used=" + absorption.absorptionUsed());
            String attr = ProbeFormatter.attribution(absorption);
            check("absorption.attribution", attr != null && attr.contains("护甲段×0.60(-4.0)"), "attr=" + attr);
        }

        HitRecord command = one("damageprobe_cmd");
        check("command.record", command != null, "record=" + command);
        if (command != null) {
            check("command.attack", near(command.attackAmount, 7.0F), "attack=" + command.attackAmount);
            check("command.damage", near(command.damageOut, 7.0F), "damage=" + command.damageOut);
            check("command.drop", near(command.healthDrop(), 7.0F), "drop=" + command.healthDrop());
            check("command.source", "minecraft:generic".equals(command.sourceId), "source=" + command.sourceId);
        }

        HitRecord magic = one("damageprobe_magic");
        check("magic.record", magic != null, "record=" + magic);
        if (magic != null) {
            check("magic.source", "minecraft:magic".equals(magic.sourceId), "source=" + magic.sourceId);
            check("magic.flag", magic.magic, "flags=" + magic.flagString());
            check("magic.damage", near(magic.damageOut, 5.0F), "damage=" + magic.damageOut);
        }

        HitRecord multiply = one("damageprobe_multiply");
        check("multiply.record", multiply != null, "record=" + multiply);
        if (multiply != null) {
            check("multiply.hurtOut", near(multiply.hurtOut, 128.4F), "hurtOut=" + multiply.hurtOut);
            check("multiply.damage", near(multiply.damageOut, 128.4F), "damage=" + multiply.damageOut);
            check("multiply.actuallyIn", near(multiply.actuallyIn, 20.0F), "actuallyIn=" + multiply.actuallyIn);
            check("multiply.actuallyOut", near(multiply.actuallyOut, 128.4F), "actuallyOut=" + multiply.actuallyOut);
            check("multiply.actuallyCalls", multiply.actuallyCalls == 1, "calls=" + multiply.actuallyCalls);
            check("multiply.drop", near(multiply.healthDrop(), 128.4F), "drop=" + multiply.healthDrop()
                    + " health=" + multiply.healthBefore + "->" + multiply.healthAfter);
            check("multiply.onlyRealWrite", multiply.hurtWrites.size() == 1,
                    "writes=" + multiply.hurtWrites.size());
            AmountWrite last = multiply.lastHurtWrite();
            check("multiply.caller", last != null && last.caller() != null
                            && last.caller().contains("DamageModifierFixture.onHurt"),
                    "caller=" + (last != null ? last.caller() : "null"));
            check("multiply.attribution",
                    ProbeFormatter.attribution(multiply) != null
                            && ProbeFormatter.attribution(multiply).contains("事件×6.42"),
                    "attr=" + ProbeFormatter.attribution(multiply));
        }

        HitRecord clamp = one("damageprobe_clamp");
        check("clamp.record", clamp != null, "record=" + clamp);
        if (clamp != null) {
            check("clamp.hurtIn", near(clamp.hurtIn, 3000.0F), "hurtIn=" + clamp.hurtIn);
            check("clamp.damage", near(clamp.damageOut, 3000.0F), "damage=" + clamp.damageOut);
            check("clamp.actuallyIn", near(clamp.actuallyIn, 3000.0F), "actuallyIn=" + clamp.actuallyIn);
            check("clamp.actuallyOut", near(clamp.actuallyOut, 10.0F), "actuallyOut=" + clamp.actuallyOut);
            check("clamp.actuallyCalls", clamp.actuallyCalls == 1, "calls=" + clamp.actuallyCalls);
            check("clamp.healthDrop", near(clamp.healthDrop(), 10.0F), "drop=" + clamp.healthDrop()
                    + " health=" + clamp.healthBefore + "->" + clamp.healthAfter);
            check("clamp.actuallyHealth",
                    near(clamp.actuallyHealthBefore, 1024.0F) && near(clamp.actuallyHealthAfter, 1014.0F),
                    "actuallyHealth=" + clamp.actuallyHealthBefore + "->" + clamp.actuallyHealthAfter);
            check("clamp.context", clamp.context != null && clamp.context.healthManaged.contains("ClampedZombie"),
                    "managed=" + (clamp.context != null ? clamp.context.healthManaged : "null"));
            String attr = ProbeFormatter.attribution(clamp);
            check("clamp.attribution", attr != null && attr.contains("实扣-2990.0"), "attr=" + attr);
            String summary = ProbeFormatter.summary(clamp);
            check("clamp.summary",
                    summary.contains("实扣 10.0") && summary.contains("掉血 10.0")
                            && summary.contains("实扣-2990.0"),
                    summary);
            String details = String.join("\n", ProbeFormatter.details(clamp));
            check("clamp.details", details.contains("[TAIL]   actuallyHurt: 实扣=10.0"),
                    "lines=" + ProbeFormatter.details(clamp).size());
        }

        HitRecord overkill = one("damageprobe_overkill");
        check("overkill.record", overkill != null, "record=" + overkill);
        if (overkill != null) {
            check("overkill.damage", near(overkill.damageOut, 3000.0F), "damage=" + overkill.damageOut);
            check("overkill.damageIn", near(overkill.damageIn, 3000.0F), "damageIn=" + overkill.damageIn);
            check("overkill.actuallyIn", near(overkill.actuallyIn, 3000.0F), "actuallyIn=" + overkill.actuallyIn);
            check("overkill.actuallyOut", near(overkill.actuallyOut, 100.0F), "actuallyOut=" + overkill.actuallyOut);
            check("overkill.health", near(overkill.healthBefore, 100.0F) && near(overkill.healthAfter, 0.0F),
                    "health=" + overkill.healthBefore + "->" + overkill.healthAfter);
            check("overkill.flag", overkill.overkill && overkill.dead && near(overkill.overkillAmount, 2900.0F),
                    "overkill=" + overkill.overkill + " amount=" + overkill.overkillAmount + " dead=" + overkill.dead);
            String attr = ProbeFormatter.attribution(overkill);
            check("overkill.attribution", attr != null && attr.contains("实扣 过量")
                            && !attr.contains("实扣-"),
                    "attr=" + attr);
            String summary = ProbeFormatter.summary(overkill);
            check("overkill.summary",
                    summary.contains("实扣 100.0(过量击杀,超出剩余血量 2900.0)")
                            && summary.contains("| 死亡")
                            && !summary.contains("实扣-"),
                    summary);
            String details = String.join("\n", ProbeFormatter.details(overkill));
            check("overkill.details",
                    details.contains("(过量击杀,超出剩余血量 2900.0)"),
                    "lines=" + ProbeFormatter.details(overkill).size());
        }

        // /damageprobe 命令本身:status / verbose 切换 / on-off 运行时开关。
        try {
            int statusResult = server.getCommands().performPrefixedCommand(
                    server.createCommandSourceStack(), "damageprobe status");
            boolean verboseBefore = ProbeState.verbose();
            int verboseResult = server.getCommands().performPrefixedCommand(
                    server.createCommandSourceStack(), "damageprobe verbose");
            boolean verboseAfter = ProbeState.verbose();
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "damageprobe verbose");

            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "damageprobe off");
            boolean off = !ProbeState.enabled();
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "damageprobe on");
            boolean on = ProbeState.enabled();
            ProbeState.setEnabled(null);

            check("command.damageprobe",
                    statusResult == 1 && verboseResult == 1 && verboseAfter != verboseBefore && off && on,
                    "status=" + statusResult + " verbose=" + verboseResult
                            + " " + verboseBefore + "->" + verboseAfter + " off=" + off + " on=" + on);

            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "damageprobe effects off");
            boolean effectsOff = !ProbeState.effectsEnabled();
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "damageprobe effects on");
            boolean effectsOn = ProbeState.effectsEnabled();
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "damageprobe modifiers off");
            boolean modifiersOff = !ProbeState.modifiersEnabled();
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "damageprobe modifiers on");
            boolean modifiersOn = ProbeState.modifiersEnabled();
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "damageprobe rejected");
            boolean rejectedOn = ProbeState.rejectedOnly();
            server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "damageprobe rejected");
            boolean rejectedOff = !ProbeState.rejectedOnly();
            check("command.probe",
                    effectsOff && effectsOn && modifiersOff && modifiersOn && rejectedOn && rejectedOff,
                    "effects=" + effectsOff + "/" + effectsOn
                            + " modifiers=" + modifiersOff + "/" + modifiersOn
                            + " rejected=" + rejectedOn + "/" + rejectedOff);
        }
        catch (Throwable t) {
            check("command.damageprobe", false, "exception=" + t);
        }
        finally {
            ProbeState.setVerbose(false);
            ProbeState.setEnabled(null);
        }

        if (playerFilterTested) {
            check("filter.mobSourceDropped", byTag("damageprobe_mobsource").isEmpty(),
                    "records=" + byTag("damageprobe_mobsource").size());
            HitRecord playerSource = one("damageprobe_playersource");
            check("filter.playerSourceKept", playerSource != null
                            && "minecraft:player_attack".equals(playerSource.sourceId),
                    "record=" + playerSource);
        }
        else {
            LOG.info("SELFTEST SKIP filter (fake player unavailable)");
        }

        check("enabled.off", byTag("damageprobe_disabled").isEmpty(),
                "records=" + byTag("damageprobe_disabled").size());

        if (playerTargetTested) {
            check("filter.nonPlayerTargetDropped", byTag("damageprobe_nontarget").isEmpty(),
                    "records=" + byTag("damageprobe_nontarget").size());
            HitRecord playerTarget = one("damageprobe_playertarget");
            check("filter.playerTargetKept", playerTarget != null && playerTarget.targetIsPlayer,
                    "record=" + playerTarget);
            if (playerTarget != null) {
                check("filter.playerTargetDamage", near(playerTarget.damageOut, 5.0F),
                        "damage=" + playerTarget.damageOut + " target=" + playerTarget.targetLabel());
                check("filter.playerTargetActually",
                        playerTarget.actuallyCalls == 1 && near(playerTarget.actuallyOut, 5.0F),
                        "calls=" + playerTarget.actuallyCalls + " actually=" + playerTarget.actuallyOut);
            }
        }
        else {
            LOG.info("SELFTEST SKIP playerTargetOnly (damageable fake player unavailable)");
        }

        check("all.mainThread", recent.stream().allMatch(r -> r.mainThread && !r.isAsync()),
                "async=" + recent.stream().filter(HitRecord::isAsync).count());
        check("emitted.count", ProbeState.emittedCount() >= 7, "emitted=" + ProbeState.emittedCount());

        // ---- 效果/修飾符探針 ----
        check("effect.applied", has(probeTexts, "probe_effect_target", "minecraft:strength", "结果=已施加"),
                "texts=" + probeTexts);
        check("effect.rejected", has(probeTexts, "damageprobe_effect_reject", "结果=被拒绝"),
                "texts=" + probeTexts);
        check("effect.removed", has(probeTexts, "probe_effect_remove", "结果=已移除"),
                "texts=" + probeTexts);
        check("modifier.added", has(probeTexts, "probe_modifier_target",
                        "minecraft:generic.max_health", "值=20.0→24.0", "结果=已添加"),
                "texts=" + probeTexts);
        check("modifier.hf", has(probeTexts, "probe_modifier_hf", "(×100)"),
                "texts=" + probeTexts);
        check("filter.commands", filterChecksPassed && ProbeState.playerRelatedOnly(),
                "checks=" + filterChecksPassed + " current=" + ProbeState.playerRelatedOnly());
        check("filter.defaultHidden", !has(probeTexts, "damageprobe_nosource_default"),
                "texts=" + probeTexts);
        check("filter.allVisible", has(probeTexts, "damageprobe_nosource_all", "来源=无(单参 addEffect)"),
                "texts=" + probeTexts);
        check("filter.restoredHidden", !has(probeTexts, "damageprobe_nosource_restored"),
                "texts=" + probeTexts);
        HitRecord link = one("damageprobe_link");
        check("link.damage", link != null, "record=" + link);
        check("link.effect", link != null && link.linked.stream()
                        .anyMatch(line -> line.contains("minecraft:strength")),
                "linked=" + (link != null ? link.linked : "null"));

        // 聊天栏路径:用假玩家捕获 sendSystemMessage,确认文本与颜色前缀。
        try {
            CapturingPlayer chatSink = new CapturingPlayer(level, new GameProfile(UUID.randomUUID(), "probe_chat"));
            Output.sendChatTo(chatSink, List.of(ProbeFormatter.summary(basic)), basic);
            check("chat.summary", chatSink.messages.size() == 1
                            && chatSink.messages.get(0).contains("[伤害探针]")
                            && chatSink.messages.get(0).contains("理论 20.0"),
                    "messages=" + chatSink.messages);

            chatSink.messages.clear();
            Output.sendChatTo(chatSink, ProbeFormatter.details(fixture), fixture);
            check("chat.details", chatSink.messages.size() > 3
                            && chatSink.messages.stream().anyMatch(m -> m.contains("改动点=")),
                    "lines=" + chatSink.messages.size());
        }
        catch (Throwable t) {
            check("chat.path", false, "exception=" + t);
        }
    }

    private List<HitRecord> byTag(String tag) {
        List<HitRecord> out = new ArrayList<>();
        for (HitRecord record : ProbeEngine.recentRecords()) {
            if (record.targetTags.contains(tag)) {
                out.add(record);
            }
        }
        return out;
    }

    private HitRecord one(String tag) {
        List<HitRecord> records = byTag(tag);
        return records.size() == 1 ? records.get(0) : null;
    }

    private void check(String name, boolean ok, String detail) {
        if (ok) {
            pass++;
        }
        else {
            fail++;
        }
        LOG.info("SELFTEST {} {} | {}", ok ? "PASS" : "FAIL", name, detail);
    }

    private static boolean near(float actual, float expected) {
        return Math.abs(actual - expected) <= EPS;
    }

    private static boolean has(List<String> texts, String... needles) {
        for (String text : texts) {
            boolean all = true;
            for (String needle : needles) {
                if (!text.contains(needle)) {
                    all = false;
                    break;
                }
            }
            if (all) {
                return true;
            }
        }
        return false;
    }

    /** 捕获聊天栏消息的假玩家(不进 PlayerList,只验证渲染/发送路径)。 */
    private static class CapturingPlayer extends FakePlayer {
        private final List<String> messages = new ArrayList<>();

        CapturingPlayer(ServerLevel level, GameProfile profile) {
            super(level, profile);
        }

        @Override
        public void sendSystemMessage(Component component) {
            messages.add(component.getString());
        }

        @Override
        public void sendSystemMessage(Component component, boolean actionBar) {
            messages.add(component.getString());
        }
    }

    /** FakePlayer 默认 isInvulnerableTo=true;这里放开,只用于验证 playerTargetOnly/玩家目标格式化。 */
    private static final class DamageableCapturingPlayer extends CapturingPlayer {
        DamageableCapturingPlayer(ServerLevel level, GameProfile profile) {
            super(level, profile);
        }

        @Override
        public boolean isInvulnerableTo(DamageSource source) {
            return false;
        }
    }
}
