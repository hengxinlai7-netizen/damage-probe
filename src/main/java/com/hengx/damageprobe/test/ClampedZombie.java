package com.hengx.damageprobe.test;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;

/**
 * 自检专用"托管血量"实体：覆盖 {@code setHealth}，把单次掉血上限压到 10。
 *
 * <p>这模拟"伤害上限/血量托管"类模组：{@code LivingHurtEvent}/{@code LivingDamageEvent}
 * 里看到的 amount 是 3000，{@code actuallyHurt} 入参也是 3000，但最后 {@code setHealth}
 * 只扣 10。探针在 actuallyHurt 的 TAIL 读血量，因此能给出"实扣 -2990"。</p>
 */
public final class ClampedZombie extends Zombie {

    private static final float MAX_DROP = 10.0F;

    public ClampedZombie(EntityType<? extends Zombie> type, Level level) {
        super(type, level);
    }

    @Override
    public void setHealth(float health) {
        float current = getHealth();
        if (health < current - MAX_DROP) {
            health = current - MAX_DROP;
        }
        super.setHealth(health);
    }
}
