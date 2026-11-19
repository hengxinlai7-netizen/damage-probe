# 伤害探针 (damageprobe) 1.2.1

Minecraft **1.20.1 / Forge 47.x** 调试模组：观测一次伤害从「意图」到「实际掉血」的全过程，
并能看出到底是**事件层倍数 / 护甲段 / actuallyHurt 实扣 / tick 结算**哪一段吃掉了伤害。
1.2.0 起还会记录 **MobEffect 与 AttributeModifier 的施加/移除**，并把同一 tick 的变动联动到命中记录。

只读事件 + 只读 Mixin，不修改任何伤害/效果/属性数值。

## 1.2.1 新增：热切换过滤

```
/damageprobe filter all         # playerRelatedOnly=false，记录所有
/damageprobe filter players     # playerRelatedOnly=true，恢复默认
/damageprobe filter status      # 显示当前设置/来源
```

- **纯运行时覆盖**，只改内存，不写回 `damageprobe-common.toml`。
- 优先级：**运行时命令 > 配置文件**。配置文件里的 `playerRelatedOnly` 仍是启动默认值。
- 重启后回到配置文件的值（默认 `players`）。
- 单参 `addEffect(instance)` 没有 source 时，记录改为
  `来源=无(单参 addEffect)`，不再是含糊的 `来源=无`。

## 1.2.0 新增

1. **`[效果探针]`**：捕获 `addEffect` / `forceAddEffect` / `removeEffect` / `removeAllEffects`，
   区分 **已施加 / 被拒绝(返回 false) / 已移除 / 全部移除**，记录来源、等级、持续时间、可见/图标、调用点。
2. **`[修饰符探针]`**：捕获 `addTransientModifier` / `addPermanentModifier` / `removeModifier` /
   `removeModifiers`，记录属性、name/uuid/amount/operation、**值 before→after**、调用点。
3. **目标实体反查**：Mixin `LivingEntity#getAttribute/getAttributes`，维护
   `WeakHashMap<AttributeInstance, LivingEntity>`；不必从栈里猜。
4. **防刷屏**：同一 `目标/效果或属性/修饰符/动作/结果` 在 20 tick 内合并为 `(×N)`；
   单批最多 32 行，超出补 `…省略 N 条`。
5. **伤害联动**：同一 tick 内发生在命中目标或攻击者身上的效果/修饰符，附在该命中记录下
   （`[联动]` 行）。

## 1.1.1 变更

1. **`减伤` 改名 `护甲段`**：它是 `damageIn / hurtOut` 的倍率 + `damageIn - hurtOut` 的差值，
   可正可负，不再用“减伤”这种暗示必定为负的标签。
2. **过量击杀识别**：`actuallyHurt` TAIL 判断 `isDeadOrDying() || health <= 0`，且
   `damageIn > 实扣` 时标记 `overkill`。摘要显示
   `实扣 9863.4(过量击杀,超出剩余血量 20222.6)`，归属显示 `实扣 过量`，不再出现误导性的
   `实扣-20222`。
3. **摘要加入血条**：`血 before→after/max`，一眼判断“实扣少是不是因为血不够”。

## 1.1.0 已有

1. **第 4 阶段 `actuallyHurt`**：HEAD 记录入参/扣血前血量，TAIL 记录调用后血量，
   `实扣 = healthBeforeActually - healthAfterActually`。被 `setHealth`/血量托管 clamp 时，
   这里会明显小于 `LivingDamageEvent` 的 amount。
2. **改动点只记录“值真的变了”的 `setAmount`**：神化 `AdventureEvents.onDamage` 那种
   `setAmount(当前值)` 的空写会被过滤，不会再覆盖真正的修改者。
3. **上下文快照**：攻击者攻击力/非默认属性/效果；目标护甲/韧性/血量/吸收/无敌帧/效果/
   是否托管血量；来源 id + 8 个 bypass tag。
4. **归属分析**：`事件×倍数 / 护甲段 / 实扣 / 结算`。
5. **输出分层**：`[output] summary/stages/context/attribution`；`/damageprobe verbose`
   临时打开 `stages + context`。

## 项目结构

```text
damage-probe/
├─ build.gradle / settings.gradle / gradle.properties
├─ src/main/java/com/hengx/damageprobe/
│  ├─ DamageProbe.java
│  ├─ ProbeConfig.java
│  ├─ ProbeState.java
│  ├─ command/ProbeCommand.java
│  ├─ core/
│  │  ├─ DamageProbeEvents.java      事件 + tick 钩子
│  │  ├─ ProbeEngine.java            记录生命周期 / actuallyHurt / 结算
│  │  ├─ HitRecord.java              一条命中的全部字段
│  │  ├─ ContextSnapshot.java        攻击者/目标/来源快照
│  │  ├─ AmountWrite.java
│  │  ├─ AmountCapture.java          改动点捕获 + 空写过滤
│  │  └─ ProbeFormatter.java         摘要/阶段/归属文本
│  ├─ mixin/
│  │  ├─ LivingHurtEventMixin.java
│  │  ├─ LivingDamageEventMixin.java
│  │  ├─ LivingEntityActuallyHurtMixin.java
│  │  ├─ PlayerActuallyHurtMixin.java
│  │  ├─ LivingEntityEffectMixin.java
│  │  ├─ LivingEntityAttributeOwnerMixin.java
│  │  └─ AttributeInstanceProbeMixin.java
│  ├─ probe/
│  │  ├─ ProbeEvent.java
│  │  ├─ ProbeEventStore.java        20 tick 合并 / 32 行上限 / 联动
│  │  ├─ ProbeOutput.java            日志 + 聊天栏
│  │  ├─ ProbeText.java
│  │  ├─ EffectProbe.java
│  │  ├─ ModifierProbe.java
│  │  └─ AttributeOwnerRegistry.java
│  ├─ output/Output.java
│  └─ test/
│     ├─ DamageModifierFixture.java  减半 / 乘 6.42 / 末尾空写
│     ├─ ClampedZombie.java          setHealth 只允许掉 10
│     └─ SelfTest.java
├─ src/main/resources/…
├─ scripts/run-selftest.ps1
└─ validation/
   ├─ RESULTS.md
   ├─ selftest-121-runA.log
   └─ selftest-121-runB.log
```

## 配置 `config/damageprobe-common.toml`

```toml
[general]
    enabled = true
    toChat = true
    toLog = true
    playerOnly = false
    playerTargetOnly = false
    # 阶段明细(stages)最多几行；摘要/上下文/归属是固定行数，不受此限制
    maxLinesPerHit = 24

[output]
    summary = true
    stages = false
    context = false
    attribution = true

[probe]
    effects = true
    modifiers = true
    # 只记录施加者/目标任一是玩家的
    playerRelatedOnly = true
    # 只记录被拒绝的施加
    rejectedOnly = false
    toChat = true
    toLog = true
```

命令（需要 OP，权限等级 2）：

```
/damageprobe verbose          # 切换详细模式 = stages + context
/damageprobe verbose on|off
/damageprobe on|off           # 运行时总开关
/damageprobe effects on|off   # 效果探针
/damageprobe modifiers on|off # 修饰符探针
/damageprobe rejected         # 只显示被拒绝的
/damageprobe filter all       # 运行时记录所有（覆盖 playerRelatedOnly）
/damageprobe filter players   # 运行时恢复只记录玩家相关
/damageprobe filter status    # 当前 filter/来源
/damageprobe status
```

## 阶段钩子

| 阶段 | 注入点 | 作用 |
| --- | --- | --- |
| `LivingAttackEvent` | `HIGHEST` / `LOWEST` | 建记录、理论伤害、血量前、是否取消 |
| `LivingHurtEvent` | `HIGHEST` | 记录进入 hurt 的值 + **上下文快照** |
| `LivingHurtEvent` | `LOWEST` | 记录所有 mod 改完后的值 + 真实 `setAmount` 写入 |
| `LivingDamageEvent` | `HIGHEST` / `LOWEST` | 护甲/附魔/抗性/吸收处理后的最终事件值 |
| `LivingEntity#actuallyHurt` | `HEAD` / `TAIL` | 扣血流程入参、调用后血量、**实扣** |
| `Player#actuallyHurt` | `HEAD` / `TAIL` | 玩家覆盖版本（`LivingEntity` 的注入不会覆盖它） |
| `ServerTickEvent` | `START` HIGHEST / `END` LOWEST | tick 计数；读最终血量，结算 `掉血` |

### 实际调用顺序（重要）

Forge 1.20.1 的真实顺序是：

```text
hurt(source, 20)
  LivingAttackEvent
  → actuallyHurt(source, amount)            ← HEAD 的 amount 是事件前值
      LivingHurtEvent  20 → 128.4
      护甲 / 附魔 / 抗性 / 吸收
      LivingDamageEvent 128.4
      setHealth(...)
  → actuallyHurt TAIL                         ← 读调用后血量
```

所以：

- `actuallyIn`（HEAD）= **LivingHurtEvent 之前**的入参；如果模组在 hurt 里乘 6.42，
  这里仍是 20，而 `hurt出` / `damage` 是 128.4。
- `实扣` 不是 `actuallyHurt` 的局部变量，而是 **TAIL 时的真实血量差**，这样 clamp/托管血量
  才能被抓到。
- `LivingDamageEvent` 的 amount **已经扣过黄心**，所以正常/吸收场景里
  `damage = 实扣 = 掉血`；黄心体现在 `hurt出 → damage` 的差值与 `吸收=`。

## 效果 / 修饰符探针

### 注入点（官方名 / SRG）

| 类 | 方法 | SRG |
| --- | --- | --- |
| `LivingEntity` | `addEffect(MobEffectInstance)` | `m_7292_`（final，委托给 2 参版本，不重复记录） |
| `LivingEntity` | `addEffect(MobEffectInstance, Entity)` | `m_147207_` |
| `LivingEntity` | `forceAddEffect(MobEffectInstance, Entity)` | `m_147215_` |
| `LivingEntity` | `removeEffect(MobEffect)` | `m_21195_` |
| `LivingEntity` | `removeAllEffects()` | **`m_21219_`**（m_21218_ 是内部效果位更新） |
| `LivingEntity` | `getAttribute(Attribute)` / `getAttributes()` | `m_21051_` / `m_21204_`（owner 反查） |
| `AttributeMap` | `getInstance(Attribute)` | `m_22146_`（MobEffect 内部属性修饰符也反查 owner） |
| `AttributeInstance` | `addTransientModifier` | `m_22118_` |
| `AttributeInstance` | `addPermanentModifier` | `m_22125_`（不是 m_22149_） |
| `AttributeInstance` | `removeModifier(AttributeModifier)` | `m_22130_` |
| `AttributeInstance` | `removeModifier(UUID)` | `m_22120_`（内部调用 m_22130_，由后者记录） |
| `AttributeInstance` | `removeModifiers()` | `m_22132_`（不是 m_22130_） |

### 目标实体反查

`AttributeInstance` 不持有 owner。`LivingEntityAttributeOwnerMixin` 在
`getAttribute` / `getAttributes` 返回时写入，`AttributeMapProbeMixin` 在
`AttributeMap#getInstance` 返回时补登（MobEffect 内部添加 `attack_damage` 修饰符走这里）：

```java
WeakHashMap<AttributeInstance, LivingEntity> OWNERS
WeakHashMap<AttributeMap, LivingEntity> MAP_OWNERS
```

mod 要加修饰符通常必须先从实体拿 `AttributeInstance`，所以这条路径能覆盖绝大多数情况；
拿不到时目标写 `未知`，但仍记录属性/修饰符/值/调用点。

### 合并与限流

- 键：`目标 + (效果id/等级/来源) 或 (属性id/修饰符uuid/operation/amount) + 动作 + 结果`。
- 20 tick 内重复只计数，安静 20 tick 后输出一次 `(×N)`。
- 单批最多 32 行，超出输出 `…省略 N 条`。
- `rejectedOnly=true` 时只保留 `被拒绝`。

### 与伤害联动

`Output` 在命中结算时取 `ProbeEventStore.linked(tick, hit.targetId, hit.causingEntityId)`，
只匹配 **事件 target 是该命中目标或攻击者** 的记录，附在命中记录下：

```text
[伤害探针]   [联动] 效果 目标=Player[...] 获得 minecraft:strength 2级 1200t ... 结果=已施加 ...
```

不会因为“攻击者是某效果的 source”就把“攻击者给别人加 buff”挂到它的每一次命中上。

## 改动点精准化

`LivingHurtEventMixin` / `LivingDamageEventMixin` 在 `setAmount` 的 HEAD 读取旧值，
`AmountCapture` 只在 `from != to` 时记录：

```java
if (Float.compare(from, to) == 0) {
    return; // 神化式空写：不覆盖真正的改动者
}
```

因此乘 6.42 的场景只输出：

```text
改动#1: 20.0 -> 128.4 (+108.4) @ com.hengx.damageprobe.test.DamageModifierFixture.onHurt:28 (首次)
[LOWEST]  LivingHurtEvent: 20.0 -> 128.4 (+108.4) 未取消  改动点=com.hengx.damageprobe.test.DamageModifierFixture.onHurt:28
```

同一个 `DamageModifierFixture` 在 LOW 还会执行 `setAmount(当前值)` 模拟神化空写；它没有
出现在 `改动#` 列表里，也没有成为 `改动点`。

## 上下文快照

`LivingHurtEvent HIGHEST` 抓一次，输出 3 行：

```text
上下文[攻击者]: attacker 攻击力=3.0 非默认属性=minecraft:generic.max_health=100.0 效果=无
上下文[目标]: 护甲=0.0 韧性=0.0 血=1024.0/1024.0 吸收=0.0 无敌帧=20 效果=无 血量托管=是(ClampedZombie#m_21153_)
上下文[来源]: minecraft:mob_attack [穿甲=否 穿附魔=否 穿抗性=否 穿效果=否 穿无敌=否 穿冷却=否 穿盾=否 穿魔法=否]
```

- 攻击者：`ATTACK_DAMAGE` 单列；其余属性遍历 `getSyncableAttributes()`，只输出偏离
  `Attribute#getDefaultValue()` 的项，最多 8 条，超出显示 `…+N`；效果最多 8 条。
- 目标：护甲、护甲韧性、最大生命、当前生命、吸收、`invulnerableTime`、效果，以及
  “血量托管”尽力判断（目标类自己声明了 `setHealth`/`getHealth` 的官方名或 SRG 名）。
- 来源：类型 id + `穿甲/穿附魔/穿抗性/穿效果/穿无敌/穿冷却/穿盾/穿魔法`。
  1.20.1 没有 `IS_MAGIC` tag，`穿魔法` 用 `minecraft:magic`/`indirect_magic` 近似。

## 归属分析

```
事件×倍数     = hurtOut / attackAmount
护甲段×倍率   = damageIn / hurtOut
护甲段差值    = damageIn - hurtOut      （可正可负；0 时写 护甲段×1.00(0.0)）
实扣          = actuallyOut - damageIn  （非击杀 clamp 时为负；过量击杀时显示"过量"）
结算          = healthDrop - actuallyOut
```

摘要示例（乘 6.42）：

```text
[伤害探针] Zombie[attacker] → Zombie[damageprobe_multiply] | 理论 20.0 | hurt出 128.4 | damage 128.4 | 实扣 128.4 | 掉血 128.4 | 血 1000.0→871.6/1000.0 | 归属: 事件×6.42(+108.4) 护甲段×1.00(0.0) 实扣0.0 结算0.0 | 线程=Server thread
```

clamp（非击杀）示例：

```text
[伤害探针] Zombie[attacker] → Zombie[damageprobe_clamp] | 理论 3000.0 | hurt出 3000.0 | damage 3000.0 | 实扣 10.0 | 掉血 10.0 | 血 1024.0→1014.0/1024.0 | 归属: 事件×1.00 护甲段×1.00(0.0) 实扣-2990.0 结算0.0 | 线程=Server thread
```

`实扣 -2990.0` 就是“这里被吞掉了 2990 点”。

过量击杀示例：

```text
[伤害探针] Zombie[attacker] → Zombie[damageprobe_overkill] | 理论 3000.0 | hurt出 3000.0 | damage 3000.0 | 实扣 100.0(过量击杀,超出剩余血量 2900.0) | 掉血 100.0 | 血 100.0→0.0/100.0 | 归属: 事件×1.00 护甲段×1.00(0.0) 实扣 过量 结算0.0 | 死亡 | 线程=Server thread
```

过量击杀的差额是“血不够”，不是被吞；归属明确写 `实扣 过量`，不出现 `实扣-2900`。

## 详细输出示例（stages + context）

```text
[伤害探针] === 命中记录 #8 ===
[伤害探针]   目标: Zombie[damageprobe_multiply] (uuid=…, pos=0.5,-60.0,8.5, dim=minecraft:overworld) 血 1000.0/1000.0
[伤害探针]   来源: minecraft:mob_attack [弹射=否 魔法=否 爆炸=否 火焰=否 冰冻=否 闪电=否 穿甲=否 穿无敌=否 穿抗性=否 穿附魔=否 穿效果=否 穿冷却=否 穿盾=否 穿魔法=否] …
[伤害探针]   线程: Server thread (主线程)
[伤害探针]   [HIGHEST] LivingAttackEvent: 理论=20.0
[伤害探针]   [HIGHEST] LivingHurtEvent: 进入=20.0
[伤害探针]   改动#1: 20.0 -> 128.4 (+108.4) @ com.hengx.damageprobe.test.DamageModifierFixture.onHurt:28 (首次)
[伤害探针]   [LOWEST]  LivingHurtEvent: 20.0 -> 128.4 (+108.4) 未取消  改动点=…onHurt:28
[伤害探针]   [HIGHEST] LivingDamageEvent: 进入=128.4
[伤害探针]   [LOWEST]  LivingDamageEvent: 实际=128.4 未取消
[伤害探针]   [HEAD]   actuallyHurt: 入参=20.0(事件前) 血=1000.0
[伤害探针]   [TAIL]   actuallyHurt: 实扣=128.4 血=871.6 调用=1次
[伤害探针]   结算: 血 1000.0 -> 871.6  掉血=128.4  吸收=0.0  实扣=128.4  线程=Server thread
[伤害探针] 上下文[攻击者]: attacker 攻击力=3.0 非默认属性=minecraft:generic.max_health=100.0 效果=无
[伤害探针] 上下文[目标]: 护甲=0.0 韧性=0.0 血=1000.0/1000.0 吸收=0.0 无敌帧=20 效果=无 血量托管=否
[伤害探针] 上下文[来源]: minecraft:mob_attack [穿甲=否 穿附魔=否 穿抗性=否 穿效果=否 穿无敌=否 穿冷却=否 穿盾=否 穿魔法=否]
```

## 构建

```powershell
$ws='C:\Users\hengx\Documents\deepseek-harness\default-workspace'
Get-Process java,javaw -ErrorAction SilentlyContinue | ForEach-Object { Stop-Process -Id $_.Id -Force -ErrorAction SilentlyContinue }
Start-Sleep -Seconds 4
$env:JAVA_HOME='C:\Program Files\Microsoft\jdk-21.0.8.9-hotspot'
$env:GRADLE_USER_HOME="$ws\.gradle-home"
Set-Location "$ws\damage-probe"
& "$ws\.tools\gradle-8.5\bin\gradle.bat" --offline --console=plain --no-daemon "-Dnet.minecraftforge.gradle.check.certs=false" "-Dorg.gradle.jvmargs=-Xmx3G -XX:MaxMetaspaceSize=1G -Dfile.encoding=UTF-8" build
```

产物 `build/libs/damageprobe-1.2.1.jar`。

交付 jar：101,656 bytes；SHA-256 `8ab7433dfa75a5918a5449ff01f39a76e94b0e9acd1f75cdd065c5bcdb810198`。1.2.0 已按要求删除。

## 自检

服务端加 `-Ddamageprobe.selftest=true`，第 10 tick 跑场景，第 12 tick 断言，第 20 tick
打印 `SELFTEST DONE` 并 `halt`。本次隔离服结果：

```text
SELFTEST RESULT: pass=114 fail=0
SELFTEST DONE
```

过滤器五步验证：

1. `filter status` 启动默认 `filter=players`。
2. 单参 `addEffect` 给僵尸加 weakness（无 source）→ 默认被 `playerRelatedOnly` 过滤，日志无该目标。
3. `/damageprobe filter all` → 再施加 → 出现 `来源=无(单参 addEffect)` 记录。
4. `/damageprobe filter players` → 再施加 → 又被过滤。
5. 重启服务器 → `SELFTEST CONFIG` 初始仍是 `filter=players`，配置文件仍为
   `playerRelatedOnly = true`，证明命令没有写回。

覆盖 6 个效果/修饰符场景 + 原伤害场景：

1. **正常施加**：给玩家 `minecraft:strength 2级 1200t` → `结果=已施加`，来源、可见/图标、调用点齐全。
2. **被拒绝**：给僵尸加 `minecraft:regeneration`（亡灵免疫）→ `结果=被拒绝`，同时 `addEffect` 返回 false。
3. **移除**：`removeEffect(strength)` → `结果=已移除`。
4. **修饰符**：`max_health +4 ADDITION` → `值=20.0→24.0`，目标实体为玩家。
5. **高频合并**：同一修饰符 100 次重刷 → 添加 `(×100)`、移除 `(×99)` 两行，未刷屏。
6. **伤害联动**：同一 tick 给目标/攻击者加力量 → 命中记录下出现 `[联动]` 行。

完整日志与逐项结果见 [validation/RESULTS.md](validation/RESULTS.md)；
过滤器前后两次启动见 [selftest-121-runA.log](validation/selftest-121-runA.log) 和
[selftest-121-runB.log](validation/selftest-121-runB.log)。

## 已知局限

- **`actuallyIn` 是事件前值**：`actuallyHurt` HEAD 在 `LivingHurtEvent` 之前，所以模组在
  hurt 里乘 6.42 时 `actuallyIn` 仍是 20，`hurt出`/`damage` 才是 128.4。这是 Forge
  1.20.1 的真实顺序；不要把它误读成“事件后的入参”。
- **`护甲段` 用 `damageIn`**：按需求用 `LivingDamageEvent` 入口值计算；如果另一个模组在
  `LivingDamageEvent` 内部再改 amount（`damageIn → damageOut`），这段差值不在“护甲段”里，
  摘要的 `damage` 列显示最终 `damageOut`。
- **`实扣` 是 TAIL 血量差**：如果模组在 `actuallyHurt` 返回后才延迟改血，实扣会在下一次
  tick 结算里体现；跨 tick 的延迟伤害可能归到“结算”段或下一段。
- **玩家覆盖**：`Player#actuallyHurt` 不调用 `super`，所以单独注入了 `Player`；如果某个
  模组实体又覆盖 `actuallyHurt` 且不调用 super，本探针看不到它的 actually 阶段。
- **直接调用 `actuallyHurt`**：没有 `LivingAttackEvent` 建立记录时，不会凭空建记录。
- **`LivingDamageEvent` 已扣黄心**：`damage/实扣/掉血` 通常相等，吸收量要看
  `hurt出→damage` 的差与 `吸收=`；不要按“实扣=hurt出”理解。
- **空写过滤**：只过滤 `from == to` 的 `setAmount`；如果模组用反射/AT 直接写 `amount`
  字段，仍只能给 LOWEST 栈兜底 `调用点=`，并标 `改动点=未捕获`。
- **LOWEST 同级顺序**：另一个模组的 LOWEST 监听器若在本探针之后注册并改值，本探针的
  `hurtOut` 会是它改之前的值。
- **上下文限制**：属性只列偏离默认值的最多 8 条，效果最多 8 条；“血量托管”只检测目标类
  自己声明的 `setHealth`/`getHealth`（官方名或 SRG 名），不覆盖所有血量管理实现。
- **效果探针的归属**：`addEffect(MobEffectInstance)` 委托给 2 参版本，所以只记录一次；
  `removeAllEffects` 记一条“全部移除”，不展开每个效果；`forceAddEffect` 的结果通过调用后
  `hasEffect` 反推，若目标本来就有该效果会被记为已施加。
- **修饰符探针的归属**：`removeModifier(UUID)` 通过内部的 `removeModifier(AttributeModifier)`
  记录；UUID 不存在时没有调用，因此不产生“未找到”记录。`addTransientModifier` 对重复 UUID
  会抛异常，异常路径没有 RETURN 记录。
- **修饰符的施加者未知**：`AttributeModifier` 没有 source；`playerRelatedOnly` 对修饰符只按
  目标是否为玩家判断，无法判断“谁施加”。
- **owner 反查**：只在 `LivingEntity#getAttribute/getAttributes`、`AttributeMap#getInstance`
  返回时登记；如果某模组保存了早于探针加载的 AttributeInstance，目标会写“未知”（仍然记录属性和值）。
- **合并的副作用**：20 tick 窗口内只保留第一份文本；如果同一 key 的等级/值发生变化但 key 未变，
  仍会合并。单批 32 行上限按“一次 flush”计，不是严格的每游戏 tick 计数。
- **联动范围**：同一 tick、事件 target 是命中目标或攻击者；同一 tick 内更早发生的无关 buff
  也可能被挂上，探针没有更细的“hit 调用时间窗”。
- **客户端**：客户端侧的 effect/modifier 事件直接跳过，避免单机双端重复。
- **filter 命令不持久化**：`filter all/players` 只写内存，重启回到配置文件
  `playerRelatedOnly`；这正是 1.2.1 的设计。
- **filter 只影响之后的事件**：已经在 `ProbeEventStore` 里 pending 的记录不会因为切换
  filter 被追溯过滤/恢复；命令在记录入口生效。
- **status 的 filter**：`/damageprobe status` 显示 `filter=all|players`；`filter status`
  额外显示“运行时覆盖/配置默认”的来源。
- **异步**：能检测并标红，但异步线程读 `health` 本身是数据竞争；聊天发送已 Marshal 回主线程。
- **实体显示名**：服务端没有客户端语言，实体类型用服务端 `en_us` 名称（`Zombie`、`Player`）。
- **聊天栏**：只发给权限 ≥2 的在线 OP；单人游戏发给房主。无 OP 在线时只有日志通道。
- **`穿魔法` 是近似**：1.20.1 没有 `IS_MAGIC`/`BYPASSES_MAGIC` tag，用
  `minecraft:magic` / `indirect_magic` 近似。
