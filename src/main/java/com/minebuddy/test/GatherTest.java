package com.minebuddy.test;

import com.minebuddy.action.ActionController;
import com.minebuddy.perception.PerceptionCollector;
import com.minebuddy.perception.PerceptionSnapshot;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.Comparator;
import java.util.Optional;

/**
 * 通用物品收集测试
 * 支持任意数量任意方块，简单寻路避障
 * 方法命名格式：testGet + 数量 + 物品ID，如testGet10Log()
 */
public class GatherTest {
    private static final GatherTest INSTANCE = new GatherTest();

    public static GatherTest getInstance() {
        return INSTANCE;
    }

    // 状态枚举
    private enum State {
        IDLE,           // 空闲
        FINDING_TARGET, // 找目标方块
        MOVING_TO_TARGET, // 走向目标（简单寻路）
        MINING,         // 挖掘目标
        WAITING_DROP,   // 等掉落捡取
        FINISHED        // 完成
    }

    private State currentState = State.IDLE;
    private int gatheredCount = 0;
    private int targetCount = 0;
    private String targetBlockKeyword = "";
    private BlockPos targetBlock = null;

    // 寻路状态
    private int stuckTimer = 0;
    private int collisionTimer = 0;
    private Vec3d lastPosition = null;
    private int miningTimer = 0;
    private int findTimer = 0;

    private final MinecraftClient client = MinecraftClient.getInstance();
    private final ActionController action = ActionController.getInstance();

    // ==================== 按命名规范的固定方法 ====================
    // 示例：testGet10Log() = 找10个木头
    public void testGet10Log() { testGet(10, "log"); }
    public void testGet20Log() { testGet(20, "log"); }
    public void testGet10OakLog() { testGet(10, "oak_log"); } // 10个橡木
    public void testGet10Stone() { testGet(10, "stone"); }
    public void testGet20Stone() { testGet(20, "stone"); }
    public void testGet5Dirt() { testGet(5, "dirt"); }
    public void testGet121Dirt() { testGet(121, "dirt"); } // 121个泥土
    public void testGet16Sand() { testGet(16, "sand"); } // 16个沙子
    public void testGet3IronOre() { testGet(3, "iron_ore"); }

    /**
     * 通用收集入口：testGet(数量, 方块ID关键词)
     * 命名格式：test + Get + 数量 + 物品ID
     * @param count 要收集的数量
     * @param itemId 方块ID关键词，支持模糊匹配，如"log"匹配所有木头，"stone"匹配石头
     */
    public void testGet(int count, String itemId) {
        if (currentState != State.IDLE && currentState != State.FINISHED) {
            sendMessage("§e已经在收集物品了！");
            return;
        }
        this.targetCount = count;
        this.targetBlockKeyword = itemId.toLowerCase();
        this.gatheredCount = 0;
        this.currentState = State.FINDING_TARGET;
        this.stuckTimer = 0;
        this.collisionTimer = 0;
        this.findTimer = 0;
        this.lastPosition = null;
        sendMessage("§a开始收集：" + count + "个 " + itemId);
        action.resetAll();
    }

    /**
     * 停止收集
     */
    public void stop() {
        currentState = State.IDLE;
        action.resetAll();
        sendMessage("§c已停止收集，已收集：" + gatheredCount + "/" + targetCount + "个 " + targetBlockKeyword);
    }

    /**
     * 每tick调用，执行状态机
     */
    public void tick() {
        if (currentState == State.IDLE || currentState == State.FINISHED) {
            return;
        }

        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null) {
            return;
        }

        PerceptionSnapshot snapshot = PerceptionCollector.getInstance().getLatestSnapshot();
        if (snapshot == null) {
            return;
        }

        // 更新卡住检测
        updateStuckDetection(player);

        switch (currentState) {
            case FINDING_TARGET:
                findTarget(snapshot, player);
                break;
            case MOVING_TO_TARGET:
                moveToTarget(player);
                break;
            case MINING:
                mineTarget(player);
                break;
            case WAITING_DROP:
                waitDrop(player);
                break;
        }
    }

    /**
     * 卡住检测：记录位置，判断是不是卡住了
     */
    private void updateStuckDetection(ClientPlayerEntity player) {
        Vec3d currentPos = player.getPos();
        if (lastPosition == null) {
            lastPosition = currentPos;
            stuckTimer = 0;
            collisionTimer = 0;
            return;
        }

        // 检测撞墙
        if (player.horizontalCollision) {
            collisionTimer++;
        } else {
            collisionTimer = 0;
        }

        // 检测位置没动（卡住）
        double movedDistance = currentPos.distanceTo(lastPosition);
        if (movedDistance < 0.1) {
            stuckTimer++;
        } else {
            stuckTimer = 0;
            lastPosition = currentPos;
        }
    }

    /**
     * 找目标方块
     */
    private void findTarget(PerceptionSnapshot snapshot, ClientPlayerEntity player) {
        findTimer++;

        // 从可见方块里找匹配的目标
        Optional<PerceptionSnapshot.VisibleBlock> closestTarget = snapshot.blocks().stream()
                .filter(b -> b.id().toLowerCase().contains(targetBlockKeyword))
                .filter(b -> b.distance() < 24) // 找24格内的
                .min(Comparator.comparingDouble(PerceptionSnapshot.VisibleBlock::distance));

        if (closestTarget.isPresent()) {
            PerceptionSnapshot.VisibleBlock target = closestTarget.get();
            targetBlock = new BlockPos(target.x(), target.y(), target.z());
            sendMessage("§7找到目标，距离：" + String.format("%.1f", target.distance()) + "格");
            currentState = State.MOVING_TO_TARGET;
            stuckTimer = 0;
            collisionTimer = 0;
            lastPosition = player.getPos();
            return;
        }

        // 没找到，转头找，转5秒没找到就往前走几步
        if (findTimer < 100) {
            action.turn(4f, 0f);
            if (findTimer % 40 == 0) {
                sendMessage("§7附近没找到" + targetBlockKeyword + "，正在转头找...");
            }
        } else {
            // 转了一圈没找到，往前走几步
            action.setMovement(1f, 0f);
            if (findTimer % 40 == 0) {
                sendMessage("§7没找到目标，往前走探索...");
            }
            // 走2秒再继续找
            if (findTimer > 140) {
                findTimer = 0;
            }
        }
    }

    /**
     * 移动到目标（简单寻路避障）
     */
    private void moveToTarget(ClientPlayerEntity player) {
        if (targetBlock == null || client.world.isAir(targetBlock)) {
            // 目标没了，重新找
            currentState = State.FINDING_TARGET;
            findTimer = 0;
            return;
        }

        Vec3d playerPos = player.getPos();
        Vec3d targetPos = targetBlock.toCenterPos();
        double distance = playerPos.distanceTo(targetPos);

        // 到4.5格以内就可以挖了
        if (distance < 4.5) {
            action.stopMovement();
            currentState = State.MINING;
            miningTimer = 0;
            sendMessage("§7到达目标旁边，开始挖掘（" + (gatheredCount + 1) + "/" + targetCount + "）");
            return;
        }

        // 看向目标
        action.lookAt(targetBlock);

        // 基础移动：向前走
        float forward = 1f;
        float strafe = 0f;

        // 简单避障逻辑
        if (collisionTimer > 20) {
            // 撞墙超过1秒，往右转15度，尝试绕开
            action.turn(15f, 0f);
            collisionTimer = 0;
            sendMessage("§7撞墙了，尝试绕开...");
        }

        if (stuckTimer > 30) {
            // 卡住超过1.5秒，跳一下，往后退一点
            action.jump();
            forward = -0.5f;
            stuckTimer = 0;
            sendMessage("§7卡住了，尝试跳过去...");
        } else if (player.horizontalCollision && action.isOnGround()) {
            // 撞墙在地上就跳
            action.jump();
        }

        // 距离远就疾跑
        if (distance > 8) {
            action.startSprint();
        } else {
            action.stopSprint();
        }

        action.setMovement(forward, strafe);
    }

    /**
     * 挖目标方块
     */
    private void mineTarget(ClientPlayerEntity player) {
        if (targetBlock == null || client.world.isAir(targetBlock)) {
            // 挖完了
            action.stopMining();
            gatheredCount++;
            sendMessage("§a挖掉了第" + gatheredCount + "个！");
            currentState = State.WAITING_DROP;
            return;
        }

        miningTimer++;
        // 超时保护，挖了10秒还没挖掉就换目标
        if (miningTimer > 200) {
            sendMessage("§c挖掘超时，换个目标");
            action.stopMining();
            currentState = State.FINDING_TARGET;
            findTimer = 0;
            return;
        }

        // 对准目标开始挖
        if (!action.isMining()) {
            action.lookAt(targetBlock);
            action.startMining(targetBlock);
        }
    }

    /**
     * 等掉落物捡起来
     */
    private void waitDrop(ClientPlayerEntity player) {
        // 等1秒（20tick）让掉落物吸过来
        if (player.age % 20 == 0) {
            if (gatheredCount >= targetCount) {
                // 收集够了
                currentState = State.FINISHED;
                action.resetAll();
                sendMessage("§6✅ 完成！一共收集了" + gatheredCount + "个 " + targetBlockKeyword + "！");
                return;
            }
            // 继续找下一个
            sendMessage("§7继续找下一个...");
            currentState = State.FINDING_TARGET;
            findTimer = 0;
        }
    }

    /**
     * 判断方块是否匹配目标
     */
    private boolean isTargetBlock(String blockId) {
        if (blockId == null) return false;
        return blockId.toLowerCase().contains(targetBlockKeyword);
    }

    /**
     * 发聊天栏消息
     */
    private void sendMessage(String msg) {
        if (client.player != null) {
            client.player.sendMessage(Text.literal("§8[MineBuddy] " + msg), false);
        }
    }

    public boolean isRunning() {
        return currentState != State.IDLE && currentState != State.FINISHED;
    }

    public int getGatheredCount() {
        return gatheredCount;
    }

    public int getTargetCount() {
        return targetCount;
    }

    public String getTargetBlockKeyword() {
        return targetBlockKeyword;
    }
}
