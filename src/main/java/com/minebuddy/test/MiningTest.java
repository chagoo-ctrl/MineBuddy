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
 * 简单挖木头测试：自动找木头、走过去、挖掉，挖10个停止
 * 简单状态机实现，验证原子动作库
 */
public class MiningTest {
    private static final MiningTest INSTANCE = new MiningTest();

    public static MiningTest getInstance() {
        return INSTANCE;
    }

    // 状态枚举
    private enum State {
        IDLE,           // 空闲
        FINDING_TREE,   // 找木头
        MOVING_TO_TREE, // 走向木头
        MINING,         // 挖木头
        WAITING_DROP,   // 等掉落捡起来
        FINISHED        // 完成
    }

    private State currentState = State.IDLE;
    private int minedCount = 0;
    private static final int TARGET_COUNT = 10;
    private BlockPos targetLog = null;
    private int miningTimer = 0;

    private final MinecraftClient client = MinecraftClient.getInstance();
    private final ActionController action = ActionController.getInstance();

    /**
     * 开始测试
     */
    public void start() {
        if (currentState != State.IDLE && currentState != State.FINISHED) {
            sendMessage("§e已经在挖木头了！");
            return;
        }
        minedCount = 0;
        currentState = State.FINDING_TREE;
        sendMessage("§a开始自动挖木头，目标：" + TARGET_COUNT + "个");
        action.resetAll();
    }

    /**
     * 停止测试
     */
    public void stop() {
        currentState = State.IDLE;
        action.resetAll();
        sendMessage("§c已停止挖木头，已挖：" + minedCount + "个");
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

        switch (currentState) {
            case FINDING_TREE:
                findTree(snapshot, player);
                break;
            case MOVING_TO_TREE:
                moveToTree(player);
                break;
            case MINING:
                mineTree(player);
                break;
            case WAITING_DROP:
                waitDrop(player);
                break;
        }
    }

    /**
     * 找最近的木头方块
     */
    private void findTree(PerceptionSnapshot snapshot, ClientPlayerEntity player) {
        Vec3d playerPos = player.getPos();

        // 从可见方块里找木头（原木）
        Optional<PerceptionSnapshot.VisibleBlock> closestLog = snapshot.blocks().stream()
                .filter(b -> isLog(b.id()))
                .filter(b -> b.distance() < 16) // 只找16格内的
                .min(Comparator.comparingDouble(PerceptionSnapshot.VisibleBlock::distance));

        if (closestLog.isPresent()) {
            PerceptionSnapshot.VisibleBlock log = closestLog.get();
            targetLog = new BlockPos(log.x(), log.y(), log.z());
            sendMessage("§7找到木头，位置：" + targetLog.toShortString() + "，距离：" + String.format("%.1f", log.distance()) + "格");
            currentState = State.MOVING_TO_TREE;
        } else {
            // 没找到木头，原地转一圈找找
            action.turn(3f, 0f);
            if (player.age % 40 == 0) {
                sendMessage("§7附近没找到木头，正在转头找...");
            }
        }
    }

    /**
     * 走向木头
     */
    private void moveToTree(ClientPlayerEntity player) {
        if (targetLog == null || client.world.isAir(targetLog)) {
            // 木头没了，重新找
            currentState = State.FINDING_TREE;
            return;
        }

        Vec3d playerPos = player.getPos();
        Vec3d targetPos = targetLog.toCenterPos();
        double distance = playerPos.distanceTo(targetPos);

        // 到4.5格以内就可以开始挖了
        if (distance < 4.5) {
            action.stopMovement();
            currentState = State.MINING;
            miningTimer = 0;
            sendMessage("§7到达木头旁边，开始挖掘");
            return;
        }

        // 看向木头
        action.lookAt(targetLog);

        // 向前走，距离远就疾跑
        if (distance > 8) {
            action.startSprint();
        } else {
            action.stopSprint();
        }
        action.setMovement(1f, 0f);

        // 遇到障碍跳一下
        if (player.horizontalCollision && action.isOnGround()) {
            action.jump();
        }
    }

    /**
     * 挖木头
     */
    private void mineTree(ClientPlayerEntity player) {
        if (targetLog == null || client.world.isAir(targetLog)) {
            // 挖完了
            action.stopMining();
            minedCount++;
            sendMessage("§a挖掉了第" + minedCount + "个木头！");
            currentState = State.WAITING_DROP;
            return;
        }

        miningTimer++;
        // 超时保护，挖了10秒还没挖掉就重新找
        if (miningTimer > 200) {
            sendMessage("§c挖木头超时，重新找目标");
            action.stopMining();
            currentState = State.FINDING_TREE;
            return;
        }

        // 对准木头开始挖
        if (!action.isMining()) {
            action.lookAt(targetLog);
            action.startMining(targetLog);
        }
    }

    /**
     * 等掉落物捡起来
     */
    private void waitDrop(ClientPlayerEntity player) {
        // 等1秒（20tick）让掉落物吸过来
        if (player.age % 20 == 0) {
            if (minedCount >= TARGET_COUNT) {
                // 挖够了
                currentState = State.FINISHED;
                action.resetAll();
                sendMessage("§6✅ 完成！一共挖了" + minedCount + "个木头！");
                return;
            }
            // 继续找下一个
            sendMessage("§7继续找下一个木头...");
            currentState = State.FINDING_TREE;
        }
    }

    /**
     * 判断是不是原木
     */
    private boolean isLog(String blockId) {
        if (blockId == null) return false;
        // 所有原木：橡木、白桦、云杉、丛林、金合欢、深色橡木、樱花、红树林
        return blockId.contains("log") || blockId.contains("wood") || blockId.contains("stem");
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

    public int getMinedCount() {
        return minedCount;
    }
}
