# MineBuddy - Minecraft 通用客户端AI陪玩机器人

> 版本：0.0.1 | 神经符号架构 | 严格生存模式 | 零作弊

---

## 🎯 项目定位

MineBuddy 是一个**通用游戏客户端智能体框架**，不仅仅支持Minecraft，未来可扩展支持星露谷物语、泰拉瑞亚等任意游戏。

核心设计：**大脑层完全通用，与具体游戏解耦**——换游戏只需要重写"眼睛"（感知层）和"肌肉"（动作层）适配层。

---

## 🧠 设计哲学

### 1. 严格生存模式，不作弊
- ❌ 不透视、不传送、不飞行、不无敌
- ❌ 不直接读取内存中的全地图数据
- ✅ 和真人玩家完全一样：只能看到屏幕上渲染的内容
- ✅ 通过模拟键鼠输入操作游戏，反作弊无法检测

### 2. 神经符号架构
- **符号层（大脑）**：PDDL形式化规划 + 三级缓存高频行为树，保证逻辑严谨可解释
- **神经层（感知/预测）**：视觉感知 + 动作预测缓存，保证反应速度
- 优势：既有LLM的灵活规划能力，又有符号系统的确定性和低延迟

### 3. 性能分层设计
| 层级 | 更新频率 | 范围 | 用途 |
|------|----------|------|------|
| 反射层 | 每tick（50ms） | 5格半径 | 战斗、躲避掉落、紧急反应 |
| 常用层 | 每5tick（250ms） | 16格半径 | 挖掘、放置、移动、拾取 |
| 规划层 | 每20tick（1s） | 32格半径 | 路径规划、长期目标、LLM推理 |

### 4. 极致性能
- 常用动作响应 < 10ms
- 突发事件响应 < 50ms
- 达到甚至超过真人玩家反应速度
- 个人使用月成本 < 10元，全部基于开源技术栈

---

## 🏗️ 系统架构

```
┌─────────────────────────────────────────────────────────┐
│                     大脑层 (通用)                        │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────┐  │
│  │ PDDL 规划器 │  │ 三级行为树  │  │ 预测缓存系统    │  │
│  └─────────────┘  └─────────────┘  └─────────────────┘  │
└───────────────────────────┬─────────────────────────────┘
                            │
┌───────────────────────────┼─────────────────────────────┐
│                     适配层 (游戏相关)                    │
│  ┌─────────────────────────┴─────────────────────────┐  │
│  │                     感知层 (眼睛)                  │  │
│  │  渲染Hook → 可见方块/实体/掉落物/自身/世界状态     │  │
│  └───────────────────────────────────────────────────┘  │
│  ┌───────────────────────────────────────────────────┐  │
│  │                     动作层 (肌肉)                  │  │
│  │  键鼠模拟 → 移动/挖掘/放置/攻击/使用/合成/背包     │  │
│  └───────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
                            │
                    Minecraft 客户端
```

---

## 👁️ 感知层接口文档 v0.0.1

感知层通过Hook客户端渲染管线实现，**只收集正在渲染的内容**，和玩家屏幕上看到的100%一致：
- ✅ 自动面剔除：6个面都被挡住的方块不会被感知
- ✅ 自动视锥体裁剪：屏幕外的内容不会被感知
- ✅ 自动遮挡剔除：被其他方块挡住的内容不会被感知
- ✅ 零额外性能开销：本来就要渲染，只是顺手记录数据

### 感知快照结构 `PerceptionSnapshot`
每帧渲染完成后生成一次完整快照。

```java
public record PerceptionSnapshot(
    SelfState self,           // 自身状态
    HandState hand,           // 手持状态
    InventoryState inventory, // 背包状态
    List<VisibleBlock> blocks,     // 可见方块
    List<VisibleEntity> entities,  // 可见实体
    List<VisibleItem> items,       // 可见掉落物
    WorldState world,         // 世界状态
    GameState game            // 游戏状态
) {}
```

---

#### 1. 自身状态 `SelfState`
| 字段 | 类型 | 说明 |
|------|------|------|
| x, y, z | double | 世界坐标 |
| yaw, pitch | float | 水平朝向/俯仰角（度） |
| hp, maxHp | float | 当前生命/最大生命 |
| hunger | int | 饥饿值 0~20 |
| saturation | float | 饱和度 |
| isOnGround | boolean | 是否在地面上 |
| isSneaking | boolean | 是否潜行 |
| isSprinting | boolean | 是否疾跑 |
| isSwimming | boolean | 是否在游泳 |
| isBurning | boolean | 是否着火 |
| air | int | 氧气值（水下300，溺水时减少） |
| experienceLevel | int | 经验等级 |
| experienceProgress | float | 当前等级经验进度 0~1 |

---

#### 2. 手持状态 `HandState`
| 字段 | 类型 | 说明 |
|------|------|------|
| mainHand | ItemInfo | 主手物品 |
| offHand | ItemInfo | 副手物品 |
| selectedSlot | int | 当前选中的快捷栏槽位 0~8 |

---

#### 3. 物品信息 `ItemInfo`
| 字段 | 类型 | 说明 |
|------|------|------|
| id | String | 物品ID，如 `minecraft:diamond_pickaxe` |
| count | int | 堆叠数量 |
| damage | int | 当前耐久损耗 |
| maxDamage | int | 最大耐久 |
| enchantments | Map<String, Integer> | 附魔列表（键为附魔ID，值为等级） |

---

#### 4. 背包状态 `InventoryState`
| 字段 | 类型 | 说明 |
|------|------|------|
| hotbar | List<ItemInfo> | 快捷栏 9格（槽位0~8） |
| main | List<ItemInfo> | 主背包 27格（槽位9~35） |
| armor | List<ItemInfo> | 盔甲4格（头、胸、腿、脚） |
| offhand | List<ItemInfo> | 副手栏 1格 |
| cursorItem | ItemInfo | 鼠标正在拖动的物品 |

---

#### 5. 可见方块 `VisibleBlock`
**只有正在渲染的方块才会出现在列表中**
| 字段 | 类型 | 说明 |
|------|------|------|
| id | String | 方块ID，如 `minecraft:stone` |
| x, y, z | int | 方块坐标 |
| distance | double | 与玩家眼睛的距离（格） |
| state | String | 方块状态（朝向、含水、成熟度等） |
| visibleFaces | int | 可见面数量 1~6 |

---

#### 6. 可见实体 `VisibleEntity`
**只有正在渲染的实体才会出现在列表中**
| 字段 | 类型 | 说明 |
|------|------|------|
| entityId | int | 实体唯一ID |
| type | String | 实体类型ID，如 `minecraft:zombie` |
| x, y, z | double | 实体坐标 |
| distance | double | 与玩家眼睛的距离（格） |
| hp, maxHp | float | 当前生命/最大生命 |
| isHostile | boolean | 是否敌对生物 |
| isBaby | boolean | 是否幼年个体 |
| yaw, pitch | float | 实体朝向 |

---

#### 7. 可见掉落物 `VisibleItem`
**只有正在渲染的掉落物才会出现在列表中**
| 字段 | 类型 | 说明 |
|------|------|------|
| entityId | int | 实体唯一ID |
| id | String | 物品ID |
| x, y, z | double | 掉落物坐标 |
| distance | double | 与玩家眼睛的距离（格） |
| count | int | 堆叠数量 |
| age | int | 已存在tick数（6000tick=5分钟后消失） |

---

#### 8. 世界状态 `WorldState`
| 字段 | 类型 | 说明 |
|------|------|------|
| worldTime | long | 世界总时间（tick） |
| dayTime | long | 当天时间 0~24000（0=日出，6000=正午，18000=日落） |
| isDay | boolean | 是否白天（dayTime 1000~13000） |
| isNight | boolean | 是否夜晚 |
| weather | String | 天气：`CLEAR`/`RAIN`/`THUNDER` |
| lightLevel | int | 玩家位置方块光照等级 0~15 |
| dimension | String | 维度：`minecraft:overworld`/`minecraft:the_nether`/`minecraft:the_end` |
| difficulty | String | 难度：`peaceful`/`easy`/`normal`/`hard` |
| moonPhase | int | 月相 0~7（影响史莱姆生成） |
| canSeeSky | boolean | 玩家头顶是否能看到天空（判断是否在户外） |

---

#### 9. 游戏状态 `GameState`
| 字段 | 类型 | 说明 |
|------|------|------|
| isDead | boolean | 玩家是否死亡 |
| isGamePaused | boolean | 游戏是否暂停（打开ESC菜单） |
| openScreen | String | 当前打开的界面名称 |
| fps | int | 当前游戏帧率 |

---

## ⚙️ 感知层运行逻辑

### 每帧执行流程
```
GameRenderer.renderWorld 开始
    ↓
[HEAD] PerceptionCollector.beginFrame() → 清空所有缓冲区
    ↓
渲染所有方块 → BlockRenderManager.renderBlock 被调用
    ↓ 每个方块渲染前
[Mixin] PerceptionCollector.addBlock() → 加入方块缓冲区
    ↓
渲染所有实体 → EntityRenderer.render 被调用
    ↓ 每个实体渲染前
[Mixin] PerceptionCollector.addEntity() → 加入实体/掉落物缓冲区
    ↓
GameRenderer.renderWorld 结束
    ↓
[TAIL] PerceptionCollector.buildSnapshot() → 收集自身/背包/世界状态，生成完整快照
    ↓
快照存入 latestSnapshot，供大脑层读取
```

### 线程安全
- 渲染线程：只写缓冲区
- 逻辑线程（大脑/行为树）：只读最新快照
- 使用CopyOnWriteArrayList保证并发安全，无锁竞争

---

## 🚀 构建与运行

### 环境要求
- JDK 21+
- 不需要预先安装Minecraft，开发环境自动下载

### 编译
```bash
# 设置Java 21环境
export JAVA_HOME=/path/to/jdk21
export PATH=$JAVA_HOME/bin:$PATH

# 编译
./gradlew compileJava
```

### 运行游戏测试
```bash
./gradlew runClient
```
进入世界后，聊天栏会每秒显示绿色调试信息：
```
[MineBuddy] 感知正常 | 方块: 1234 | 实体: 5 | 掉落物: 2 | FPS: 144
```

### 构建mod jar
```bash
./gradlew build
```
输出文件在 `build/libs/minebuddy-0.0.1.jar`

---

## 📋 版本历史

### v0.0.1 (2026-07-26) - 感知层初版
- ✅ 完成感知层核心架构
- ✅ 实现方块渲染Hook，收集可见方块
- ✅ 实现实体渲染Hook，收集可见实体和掉落物
- ✅ 实现自身状态、手持状态、背包状态收集
- ✅ 实现世界时间、天气、光照、维度等状态收集
- ✅ 实现游戏状态收集
- ✅ 每秒在聊天栏输出感知统计，验证功能正常

---

## 📄 开源协议
MIT License - 详见 LICENSE 文件
