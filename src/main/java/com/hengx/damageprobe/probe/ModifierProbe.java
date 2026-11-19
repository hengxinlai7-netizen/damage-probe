package com.hengx.damageprobe.probe;

import java.util.ArrayDeque;
import java.util.Deque;

import com.hengx.damageprobe.core.ProbeEngine;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;

/** AttributeModifier 添加/移除的捕获与文本。 */
public final class ModifierProbe {

    private static final ThreadLocal<Deque<Call>> CALLS = ThreadLocal.withInitial(ArrayDeque::new);
    private static final ThreadLocal<Call> REMOVE_ALL_CALL = new ThreadLocal<>();

    private ModifierProbe() {
    }

    public static void begin(AttributeInstance instance, String api, AttributeModifier modifier) {
        if (instance == null || modifier == null || REMOVE_ALL_CALL.get() != null) {
            return;
        }
        CALLS.get().push(new Call(api, instance, modifier, instance.getValue(), -1));
    }

    public static void end(AttributeInstance instance, String api, AttributeModifier modifier) {
        if (instance == null || modifier == null || REMOVE_ALL_CALL.get() != null) {
            return;
        }
        Deque<Call> stack = CALLS.get();
        if (stack.isEmpty()) {
            return;
        }
        Call call = stack.pop();
        record(call, instance.getValue());
    }

    public static void beginRemoveAll(AttributeInstance instance) {
        if (instance == null) {
            return;
        }
        REMOVE_ALL_CALL.set(new Call("removeModifiers", instance, null,
                instance.getValue(), instance.getModifiers().size()));
    }

    public static void endRemoveAll(AttributeInstance instance) {
        Call call = REMOVE_ALL_CALL.get();
        if (call == null || instance == null) {
            return;
        }
        REMOVE_ALL_CALL.remove();
        record(call, instance.getValue());
    }

    private static void record(Call call, double after) {
        AttributeInstance instance = call.instance;
        LivingEntity owner = AttributeOwnerRegistry.owner(instance);
        if (owner != null && owner.level().isClientSide()) {
            return;
        }
        String target = owner != null ? ProbeText.entity(owner) : "未知";
        int targetId = owner != null ? owner.getId() : -1;
        boolean playerRelated = owner instanceof Player;
        String attr = ProbeText.attribute(instance.getAttribute());
        String site = ProbeText.callSite();

        ProbeEvent.Result result;
        String text;
        String key;
        if (call.modifier == null) {
            result = ProbeEvent.Result.ALL_REMOVED;
            int afterCount = instance.getModifiers().size();
            text = "目标=" + target + " 属性=" + attr
                    + " 修饰符=全部 结果=全部移除"
                    + " 值=" + fmt(call.before) + "→" + fmt(after)
                    + " 数量=" + call.countBefore + "→" + afterCount
                    + " 调用点=" + site;
            key = "removeModifiers|" + targetId + "|" + attr + "|" + call.countBefore + "|" + afterCount;
        }
        else {
            AttributeModifier modifier = call.modifier;
            boolean remove = call.api.startsWith("remove");
            result = remove ? ProbeEvent.Result.REMOVED : ProbeEvent.Result.APPLIED;
            text = "目标=" + target + " 属性=" + attr
                    + " 修饰符=" + modifier.getName()
                    + " uuid=" + modifier.getId()
                    + " amount=" + fmt(modifier.getAmount())
                    + " operation=" + modifier.getOperation()
                    + " 结果=" + (remove ? "已移除" : "已添加")
                    + " 值=" + fmt(call.before) + "→" + fmt(after)
                    + " 调用点=" + site;
            key = call.api + "|" + targetId + "|" + attr + "|" + modifier.getId()
                    + "|" + modifier.getOperation() + "|" + modifier.getAmount() + "|" + result;
        }

        ProbeEventStore.record(new ProbeEvent(ProbeEvent.Category.MODIFIER, ProbeEngine.serverTick(),
                targetId, -1, key, text, result, playerRelated));
    }

    private static String fmt(double value) {
        return ProbeFormatterBridge.f1(value);
    }

    private record Call(String api, AttributeInstance instance, AttributeModifier modifier,
                        double before, int countBefore) {
    }

    /** 避免 probe 包直接依赖 core 的完整格式化类，只借用 f1。 */
    private static final class ProbeFormatterBridge {
        private static String f1(double value) {
            return com.hengx.damageprobe.core.ProbeFormatter.f1(value);
        }
    }
}
