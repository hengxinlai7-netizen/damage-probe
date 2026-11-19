# 自检结果（damageprobe 1.2.1，隔离服 Forge 1.20.1-47.2.20）

- 结果：**`SELFTEST RESULT: pass=114 fail=0` / `SELFTEST DONE`**
- 运行 A（含 filter 切换）：`validation/selftest-121-runA.log`
- 运行 B（重启后默认值）：`validation/selftest-121-runB.log`
- 配置文件：`config/damageprobe-common.toml` 仍为 `playerRelatedOnly = true`

## 1. 默认 filter=players

启动时：

```text
SELFTEST CONFIG: … probe[effects=true modifiers=true filter=players playerRelatedOnly=true rejectedOnly=false] …
```

`/damageprobe filter status`：

```text
[伤害探针] filter=players (运行时覆盖=否, 配置=players)
```

## 2. 默认看不到“单参 addEffect 给僵尸”

`WhiteAttack` 式调用：

```java
target.addEffect(new MobEffectInstance(WEAKNESS, 200, 0)); // 无 source
```

默认 `playerRelatedOnly=true`，目标是非玩家、source 为空 → 记录在入口被过滤：

```text
SELFTEST PASS filter.defaultHidden | …
```

日志中没有 `damageprobe_nosource_default` 的 `[效果探针]` 行。

## 3. filter all 后可见，并标注无来源

```text
[伤害探针] filter=all (只记录玩家相关=关，立即生效；不写配置文件)
```

再次施加：

```text
[效果探针] tick=10 目标=Zombie[damageprobe_nosource_all]#21(…) 获得 minecraft:weakness 1级 200t 可见=是 图标=是 来源=无(单参 addEffect) 结果=已施加 调用点=com.hengx.damageprobe.test.SelfTest.runScenarios:241
```

断言：`SELFTEST PASS filter.allVisible`。

## 4. filter players 恢复默认

```text
[伤害探针] filter=players (只记录玩家相关=开，立即生效；不写配置文件)
```

随后再给另一个僵尸施加 weakness → 再次被过滤：

```text
SELFTEST PASS filter.restoredHidden | …
```

## 5. 重启后回到配置文件默认值

运行 B（同一 jar，重启，世界重建）：

```text
SELFTEST CONFIG: … probe[effects=true modifiers=true filter=players playerRelatedOnly=true rejectedOnly=false] …
```

配置文件未被命令改写：

```toml
[probe]
    playerRelatedOnly = true
```

结论：`filter all` 只写内存；重启后 `ProbeState.playerRelatedOnlyOverride` 为空，回到配置默认
`players`。

## 6. 命令断言

```text
SELFTEST FILTER: status=1 statusPlayers=true all=true players=true noSource=true/true/true
SELFTEST PASS filter.commands | checks=true current=true
```

同一次运行里还验证了 `filter.allVisible`、`filter.restoredHidden`；`filter.defaultHidden`
在切换前完成。

## 7. 旧功能

1.2.0 的 6 个效果/修饰符场景仍全部 PASS（正常施加、被拒绝、移除、修饰符 20→24、
高频 ×100、`[联动]`），完整输出见 `selftest-121-runA.log`。`AttributeMap#getInstance`
也已登记，所以 weakness/strength 这类效果自带的属性修饰符能正确显示目标实体。
