package com.minebuddy.test;

import com.minebuddy.action.ActionController;
import com.minebuddy.perception.PerceptionCollector;
import com.minebuddy.perception.PerceptionSnapshot;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;

import java.util.Comparator;
import java.util.Optional;

/**
 * 杀羊测试
 * 功能：找到最近的羊，移动过去击杀，捡掉落物
 */
public class KillSheepTest {
    private static final KillSheepTest INSTANCE = new KillSheepTest();

    public static KillSheepTest getInstance() {
        return INSTANCE;
    }

    // 状态枚举
    private enum State {
        IDLE,           // 空闲
        FINDING_SHEEP,  // 找羊
        MOVING_TO_SHEEP,// 走向羊
        ATTACKING,      // 攻击羊
        WAITING_DROP,   // 等掉落物
        FINISHED        // 完成
    }

    private State currentState = State.IDLE;
    private int killedCount = 0;
    private int targetCount = 1;
    private int targetEntityId = -1;
    private Vec3d targetPos = null;

    // 状态计时器
    private int stuckTimer = 0;
    private int collisionTimer = 0;
    private Vec3d lastPosition = null;
    private int attackCooldown = 0;
    private int findTimer = 0;

    private final MinecraftClient client = MinecraftClient.getInstance();
    private final ActionController action = ActionController.getInstance();

    /**
     * 开始杀羊，默认杀1只
     */
    public void startKillSheep() {
        startKillSheep(1);
    }

    /**
     * 开始杀指定数量的羊
     */
    public void startKillSheep(int count) {
        if (currentState != State.IDLE && currentState != State.FINISHED) {
            sendMessage("§e已经在杀羊了！");
            return;
        }
        this.targetCount = count;
        this.killedCount = 0;
        this.currentState = State.FINDING_SHEEP;
        this.stuckTimer = 0;
        this.collisionTimer = 0;
        this.findTimer = 0;
        this.lastPosition = null;
        this.targetEntityId = -1;
        this.targetPos = null;
        sendMessage("§a开始杀羊，目标：" + count + "只");
        action.resetAll();
    }

    /**
     * 停止
     */
    public void stop() {
        currentState = State.IDLE;
        action.resetAll();
        sendMessage("§c已停止，已击杀：" + killedCount + "/" + targetCount + "只羊");
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
        // 攻击冷却
        if (attackCooldown > 0) attackCooldown--;

        switch (currentState) {
            case FINDING_SHEEP:
                findSheep(snapshot, player);
                break;
            case MOVING_TO_SHEEP:
                moveToSheep(snapshot, player);
                break;
            case ATTACKING:
                attackSheep(snapshot, player);
                break;
            case WAITING_DROP:
                waitDrop(player);
                break;
        }
    }

    /**
     * 卡住检测：和收集测试一样的逻辑
     */
    private void updateStuckDetection(ClientPlayerEntity player) {
        Vec3d currentPos = player.getPos();
        if (lastPosition == null) {
            lastPosition = currentPos;
            stuckTimer = 0;
            collisionTimer = 0;
            return;
        }
        if (player.horizontalCollision) {
            collisionTimer++;
        } else {
            collisionTimer = 0;
        }
        double movedDistance = currentPos.distanceTo(lastPosition);
        if (movedDistance < 0.1) {
            stuckTimer++;
        } else {
            stuckTimer = 0;
            lastPosition = currentPos;
        }
    }

    /**
     * 找羊
     */
    private void findSheep(PerceptionSnapshot snapshot, ClientPlayerEntity player) {
        findTimer++;
        // 从可见实体里找羊，16格内最近的
        Optional<PerceptionSnapshot.VisibleEntity> closestSheep = snapshot.entities().stream()
                .filter(e -> e.type().toLowerCase().contains("sheep"))
                .filter(e -> e.flatDistance() < 24)
                .min(Comparator.comparingDouble(PerceptionSnapshot.VisibleEntity::flatDistance));

        if (closestSheep.isPresent()) {
            PerceptionSnapshot.VisibleEntity sheep = closestSheep.get();
            targetEntityId = sheep.entityId();
            targetPos = new Vec3d(sheep.x(), sheep.y(), sheep.z());
            sendMessage("§7找到羊，距离：" + String.format("%.1f", sheep.flatDistance()) + "格，血量：" + String.format("%.0f", sheep.hp()) + "/" + String.format("%.0f", sheep.maxHp()));
            currentState = State.MOVING_TO_SHEEP;
            stuckTimer = 0;
            collisionTimer = 0;
            lastPosition = player.getPos();
            return;
        }

        // 没找到，转头找
        if (findTimer < 100) {
            action.turn(4f, 0f);
            if (findTimer % 40 == 0) {
                sendMessage("§7附近没找到羊，正在转头找...");
            }
        } else {
            // 转了一圈没找到，往前走
            action.setMovement(1f, 0f);
            if (findTimer % 40 == 0) {
                sendMessage("§7没找到羊，往前走探索...");
            }
            if (findTimer > 140) {
                findTimer = 0;
            }
        }
    }

    /**
     * 移动到羊旁边
     */
    private void moveToSheep(PerceptionSnapshot snapshot, ClientPlayerEntity player) {
        // 先找目标羊还在不在
        Optional<PerceptionSnapshot.VisibleEntity> sheepOpt = snapshot.entities().stream()
                .filter(e -> e.entityId() == targetEntityId)
                .findFirst();

        if (sheepOpt.isEmpty()) {
            // 羊不见了，可能跑了或者死了，重新找
            sendMessage("§7目标羊不见了，重新找...");
            currentState = State.FINDING_SHEEP;
            findTimer = 0;
            return;
        }

        PerceptionSnapshot.VisibleEntity sheep = sheepOpt.get();
        targetPos = new Vec3d(sheep.x(), sheep.y(), sheep.z());
        Vec3d playerPos = player.getPos();
        double distance = playerPos.distanceTo(targetPos);

        // 到3格以内就开始攻击
        if (distance < 3.0) {
            action.stopMovement();
            currentState = State.ATTACKING;
            attackCooldown = 0;
            sendMessage("§7到达攻击范围，开始攻击！");
            return;
        }

        // 看向羊
        action.lookAt(targetPos);
        float forward = 1f;
        float strafe = 0f;

        // 简单避障
        if (collisionTimer > 20) {
            action.turn(15f, 0f);
            collisionTimer = 0;
        }
        if (stuckTimer > 30) {
            action.jump();
            forward = -0.5f;
            stuckTimer = 0;
        } else if (player.horizontalCollision && action.isOnGround()) {
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
     * 攻击羊
     */
    private void attackSheep(PerceptionSnapshot snapshot, ClientPlayerEntity player) {
        // 找目标羊
        Optional<PerceptionSnapshot.VisibleEntity> sheepOpt = snapshot.entities().stream()
                .filter(e -> e.entityId() == targetEntityId)
                .findFirst();

        if (sheepOpt.isEmpty()) {
            // 羊死了！
            action.stopMovement();
            killedCount++;
            sendMessage("§a击杀了第" + killedCount + "只羊！");
            currentState = State.WAITING_DROP;
            return;
        }

        PerceptionSnapshot.VisibleEntity sheep = sheepOpt.get();
        targetPos = new Vec3d(sheep.x(), sheep.y(), sheep.z());
        Vec3d playerPos = player.getPos();
        double distance = playerPos.distanceTo(targetPos);

        // 如果羊跑远了，追上去
        if (distance > 3.5) {
            currentState = State.MOVING_TO_SHEEP;
            return;
        }

        // 看向羊
        action.lookAt(targetPos);

        // 攻击冷却好了就打一下（Minecraft攻击冷却大概10tick左右）
        if (attackCooldown <= 0) {
            action.attackEntity(targetEntityId);
            attackCooldown = 10; // 0.5秒打一下，等冷却
            if (sheep.hp() > 0) {
                sendMessage("§c攻击！羊剩余血量：" + String.format("%.0f", sheep.hp()));
            }
        }
    }

    /**
     * 等掉落物捡起来
     */
    private void waitDrop(ClientPlayerEntity player) {
        // 等1.5秒捡东西
        if (player.age % 30 == 0) {
            if (killedCount >= targetCount) {
                currentState = State.FINISHED;
                action.resetAll();
                sendMessage("§6✅ 完成！一共击杀了" + killedCount + "只羊！");
                return;
            }
            // 继续找下一只
            sendMessage("§7继续找下一只羊...");
            currentState = State.FINDING_SHEEP;
            findTimer = 0;
        }
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

    public int getKilledCount() {
        return killedCount;
    }

    public int getTargetCount() {
        return targetCount;
    }
}
