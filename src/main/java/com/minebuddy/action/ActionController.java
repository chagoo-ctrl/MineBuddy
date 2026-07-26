package com.minebuddy.action;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * 原子动作控制器 - 肌肉层（高效模式）
 * 单人/局域网无反作弊环境使用，直接调用游戏API，高效可靠
 * 线程安全：所有游戏操作自动提交到Minecraft主线程执行
 */
public class ActionController {
    private static final ActionController INSTANCE = new ActionController();
    private final MinecraftClient client;
    private final Executor mainExecutor;

    // 动作状态
    private boolean isMining = false;
    private BlockPos miningTarget = null;

    private ActionController() {
        this.client = MinecraftClient.getInstance();
        this.mainExecutor = client;
    }

    public static ActionController getInstance() {
        return INSTANCE;
    }

    // ==================== 工具方法 ====================

    private CompletableFuture<Void> runOnMainThread(Runnable task) {
        if (client.isOnThread()) {
            task.run();
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.runAsync(task, mainExecutor);
    }

    /**
     * 计算看向目标位置需要的角度
     */
    private float[] calculateLookAt(Vec3d eyePos, Vec3d targetPos) {
        double dx = targetPos.x - eyePos.x;
        double dy = targetPos.y - eyePos.y;
        double dz = targetPos.z - eyePos.z;
        double dHorizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (MathHelper.atan2(dz, dx) * 180 / Math.PI) - 90f;
        float pitch = (float) -(MathHelper.atan2(dy, dHorizontal) * 180 / Math.PI);
        return new float[]{yaw, pitch};
    }

    // ==================== 视角控制（直接瞬转，高效） ====================

    /**
     * 直接看向指定角度
     */
    public CompletableFuture<Void> lookAt(float yaw, float pitch) {
        return runOnMainThread(() -> {
            ClientPlayerEntity player = client.player;
            if (player == null) return;
            player.setYaw(yaw);
            player.setPitch(MathHelper.clamp(pitch, -90f, 90f));
            // 同步身体角度
            player.prevYaw = yaw;
            player.bodyYaw = yaw;
            player.headYaw = yaw;
        });
    }

    /**
     * 直接看向指定方块中心
     */
    public CompletableFuture<Void> lookAt(BlockPos pos) {
        return runOnMainThread(() -> {
            ClientPlayerEntity player = client.player;
            if (player == null) return;
            Vec3d eyePos = player.getEyePos();
            Vec3d targetPos = pos.toCenterPos();
            float[] angles = calculateLookAt(eyePos, targetPos);
            lookAt(angles[0], angles[1]);
        });
    }

    /**
     * 直接看向指定实体
     */
    public CompletableFuture<Void> lookAt(Entity entity) {
        return runOnMainThread(() -> {
            ClientPlayerEntity player = client.player;
            if (player == null) return;
            Vec3d eyePos = player.getEyePos();
            Vec3d targetPos = entity.getEyePos();
            float[] angles = calculateLookAt(eyePos, targetPos);
            lookAt(angles[0], angles[1]);
        });
    }

    /**
     * 看向任意坐标点
     */
    public CompletableFuture<Void> lookAt(Vec3d pos) {
        return runOnMainThread(() -> {
            ClientPlayerEntity player = client.player;
            if (player == null) return;
            Vec3d eyePos = player.getEyePos();
            float[] angles = calculateLookAt(eyePos, pos);
            lookAt(angles[0], angles[1]);
        });
    }

    /**
     * 相对转动视角
     */
    public CompletableFuture<Void> turn(float deltaYaw, float deltaPitch) {
        return runOnMainThread(() -> {
            ClientPlayerEntity player = client.player;
            if (player == null) return;
            player.setYaw(player.getYaw() + deltaYaw);
            player.setPitch(MathHelper.clamp(player.getPitch() + deltaPitch, -90f, 90f));
        });
    }

    // ==================== 移动控制（直接设置输入，高效） ====================

    /**
     * 设置移动输入
     * @param forward 前后：-1=后退，0=停，1=前进
     * @param strafe 左右：-1=右，0=停，1=左（Minecraft输入坐标系是反的）
     */
    public CompletableFuture<Void> setMovement(float forward, float strafe) {
        return runOnMainThread(() -> {
            ClientPlayerEntity player = client.player;
            if (player == null) return;
            player.input.movementForward = forward;
            player.input.movementSideways = strafe;
        });
    }

    /**
     * 停止所有移动
     */
    public CompletableFuture<Void> stopMovement() {
        return setMovement(0, 0);
    }

    /**
     * 设置蹲下状态
     */
    public CompletableFuture<Void> setSneak(boolean sneak) {
        return runOnMainThread(() -> {
            ClientPlayerEntity player = client.player;
            if (player == null) return;
            player.setSneaking(sneak);
        });
    }

    public CompletableFuture<Void> startSneak() { return setSneak(true); }
    public CompletableFuture<Void> stopSneak() { return setSneak(false); }

    /**
     * 设置疾跑状态
     */
    public CompletableFuture<Void> setSprint(boolean sprint) {
        return runOnMainThread(() -> {
            ClientPlayerEntity player = client.player;
            if (player == null) return;
            player.setSprinting(sprint);
        });
    }

    public CompletableFuture<Void> startSprint() { return setSprint(true); }
    public CompletableFuture<Void> stopSprint() { return setSprint(false); }

    /**
     * 跳跃一次
     */
    public CompletableFuture<Void> jump() {
        return runOnMainThread(() -> {
            ClientPlayerEntity player = client.player;
            if (player == null) return;
            player.jump();
        });
    }

    /**
     * 设置跳跃状态（持续跳，比如游泳/搭路）
     */
    public CompletableFuture<Void> setJumping(boolean jumping) {
        return runOnMainThread(() -> {
            ClientPlayerEntity player = client.player;
            if (player == null) return;
            if (jumping) {
                player.jump();
            }
        });
    }

    // ==================== 世界交互（直接调用游戏API，不用模拟按键） ====================

    /**
     * 开始挖掘方块（直接调用游戏挖掘逻辑，自动对准）
     */
    public CompletableFuture<Void> startMining(BlockPos pos) {
        return runOnMainThread(() -> {
            ClientPlayerEntity player = client.player;
            ClientPlayerInteractionManager interactionManager = client.interactionManager;
            if (player == null || interactionManager == null) return;

            // 先对准方块
            lookAt(pos).join();
            this.isMining = true;
            this.miningTarget = pos;

            // 直接调用游戏挖掘
            Direction side = Direction.UP;
            BlockHitResult hitResult = new BlockHitResult(pos.toCenterPos(), side, pos, false);
            interactionManager.attackBlock(pos, side);
            player.swingHand(Hand.MAIN_HAND);
        });
    }

    /**
     * 持续挖掘（每tick调用，保持挖掘进度）
     */
    private void tickMining() {
        if (!isMining || miningTarget == null) return;
        ClientPlayerInteractionManager interactionManager = client.interactionManager;
        if (interactionManager == null) return;

        // 持续挖掘
        interactionManager.updateBlockBreakingProgress(miningTarget, Direction.UP);
        client.player.swingHand(Hand.MAIN_HAND);

        // 挖完了自动停止
        if (client.world.isAir(miningTarget)) {
            stopMining();
        }
    }

    /**
     * 立即破坏方块（创造模式用）
     */
    public CompletableFuture<Void> breakBlockInstantly(BlockPos pos) {
        return runOnMainThread(() -> {
            ClientPlayerInteractionManager interactionManager = client.interactionManager;
            if (interactionManager == null) return;
            interactionManager.breakBlock(pos);
            isMining = false;
            miningTarget = null;
        });
    }

    /**
     * 停止挖掘
     */
    public CompletableFuture<Void> stopMining() {
        return runOnMainThread(() -> {
            ClientPlayerInteractionManager interactionManager = client.interactionManager;
            if (interactionManager != null) {
                interactionManager.cancelBlockBreaking();
            }
            this.isMining = false;
            this.miningTarget = null;
        });
    }

    /**
     * 在指定位置放置方块（主手）
     * @param pos 要放置的方块位置
     * @param side 放置面
     */
    public CompletableFuture<Void> placeBlock(BlockPos pos, Direction side) {
        return runOnMainThread(() -> {
            ClientPlayerEntity player = client.player;
            ClientPlayerInteractionManager interactionManager = client.interactionManager;
            if (player == null || interactionManager == null) return;

            // 看向放置位置
            Vec3d hitPos = pos.toCenterPos().add(Vec3d.of(side.getVector()).multiply(0.5d));
            float[] angles = calculateLookAt(player.getEyePos(), hitPos);
            lookAt(angles[0], angles[1]).join();

            // 直接交互放置
            BlockHitResult hitResult = new BlockHitResult(hitPos, side, pos, false);
            interactionManager.interactBlock(player, Hand.MAIN_HAND, hitResult);
            player.swingHand(Hand.MAIN_HAND);
        });
    }

    /**
     * 放置方块（自动选择放置面）
     */
    public CompletableFuture<Void> placeBlock(BlockPos pos) {
        return placeBlock(pos, Direction.UP);
    }

    /**
     * 右键交互方块（开门、开箱子、和村民交易等）
     */
    public CompletableFuture<Void> interactBlock(BlockPos pos, Direction side) {
        return runOnMainThread(() -> {
            ClientPlayerEntity player = client.player;
            ClientPlayerInteractionManager interactionManager = client.interactionManager;
            if (player == null || interactionManager == null) return;

            BlockHitResult hitResult = new BlockHitResult(pos.toCenterPos(), side, pos, false);
            interactionManager.interactBlock(player, Hand.MAIN_HAND, hitResult);
        });
    }

    /**
     * 攻击实体
     */
    public CompletableFuture<Void> attackEntity(Entity entity) {
        return runOnMainThread(() -> {
            ClientPlayerEntity player = client.player;
            ClientPlayerInteractionManager interactionManager = client.interactionManager;
            if (player == null || interactionManager == null) return;

            lookAt(entity).join();
            interactionManager.attackEntity(player, entity);
            player.swingHand(Hand.MAIN_HAND);
        });
    }

    /**
     * 根据实体ID攻击实体
     */
    public CompletableFuture<Void> attackEntity(int entityId) {
        return runOnMainThread(() -> {
            ClientPlayerEntity player = client.player;
            if (player == null || client.world == null) return;
            Entity entity = client.world.getEntityById(entityId);
            if (entity != null) {
                attackEntity(entity);
            }
        });
    }

    /**
     * 使用主手物品（右键，吃东西、喝药水、拉弓等）
     */
    public CompletableFuture<Void> useItem() {
        return runOnMainThread(() -> {
            ClientPlayerEntity player = client.player;
            ClientPlayerInteractionManager interactionManager = client.interactionManager;
            if (player == null || interactionManager == null) return;
            interactionManager.interactItem(player, Hand.MAIN_HAND);
        });
    }

    /**
     * 使用副手物品
     */
    public CompletableFuture<Void> useOffhandItem() {
        return runOnMainThread(() -> {
            ClientPlayerEntity player = client.player;
            ClientPlayerInteractionManager interactionManager = client.interactionManager;
            if (player == null || interactionManager == null) return;
            interactionManager.interactItem(player, Hand.OFF_HAND);
        });
    }

    // ==================== 物品操作（直接操作背包，不用模拟按键） ====================

    /**
     * 切换到指定快捷栏槽位
     * @param slot 0-8
     */
    public CompletableFuture<Void> selectHotbarSlot(int slot) {
        return runOnMainThread(() -> {
            ClientPlayerEntity player = client.player;
            if (player == null) return;
            player.getInventory().selectedSlot = MathHelper.clamp(slot, 0, 8);
        });
    }

    /**
     * 交换主副手物品
     */
    public CompletableFuture<Void> swapHands() {
        return runOnMainThread(() -> {
            ClientPlayerEntity player = client.player;
            if (player == null) return;
            ItemStack mainHand = player.getMainHandStack();
            ItemStack offHand = player.getOffHandStack();
            player.setStackInHand(Hand.MAIN_HAND, offHand);
            player.setStackInHand(Hand.OFF_HAND, mainHand);
        });
    }

    /**
     * 丢弃当前主手物品一个
     */
    public CompletableFuture<Void> dropItem() {
        return runOnMainThread(() -> {
            ClientPlayerEntity player = client.player;
            if (player == null) return;
            player.dropSelectedItem(false);
        });
    }

    /**
     * 丢弃当前主手整组物品
     */
    public CompletableFuture<Void> dropAllItem() {
        return runOnMainThread(() -> {
            ClientPlayerEntity player = client.player;
            if (player == null) return;
            player.dropSelectedItem(true);
        });
    }

    /**
     * 看向掉落物（走到碰撞箱自动拾取，移动由大脑层控制）
     */
    public CompletableFuture<Void> lookAtItem(ItemEntity itemEntity) {
        return lookAt(itemEntity);
    }

    /**
     * 背包物品转移：把背包槽位物品移到快捷栏
     */
    public CompletableFuture<Void> moveToHotbar(int inventorySlot, int hotbarSlot) {
        return runOnMainThread(() -> {
            ClientPlayerEntity player = client.player;
            if (player == null) return;
            ItemStack stack = player.getInventory().getStack(inventorySlot);
            player.getInventory().setStack(hotbarSlot, stack);
            player.getInventory().setStack(inventorySlot, ItemStack.EMPTY);
        });
    }

    // ==================== 状态查询 ====================

    public boolean isMining() { return isMining; }
    public BlockPos getMiningTarget() { return miningTarget; }
    public float getYaw() { return client.player != null ? client.player.getYaw() : 0; }
    public float getPitch() { return client.player != null ? client.player.getPitch() : 0; }
    public int getSelectedSlot() { return client.player != null ? client.player.getInventory().selectedSlot : 0; }
    public ItemStack getMainHandStack() { return client.player != null ? client.player.getMainHandStack() : ItemStack.EMPTY; }
    public boolean isOnGround() { return client.player != null && client.player.isOnGround(); }

    // ==================== 帧更新 ====================

    /**
     * 每tick调用，处理持续动作
     */
    public void tick() {
        ClientPlayerEntity player = client.player;
        if (player == null) return;

        // 持续挖掘
        if (isMining) {
            tickMining();
        }
    }

    /**
     * 重置所有动作
     */
    public CompletableFuture<Void> resetAll() {
        return runOnMainThread(() -> {
            stopMovement();
            stopMining();
            setSneak(false);
            setSprint(false);
            setJumping(false);
        });
    }
}
