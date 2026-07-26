package com.minebuddy.action;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.glfw.GLFW;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * 原子动作控制器 - 肌肉层
 * 所有操作均模拟真人键鼠输入，和物理按键完全一致，反作弊无法检测
 * 线程安全：所有游戏操作自动提交到Minecraft主线程执行
 */
public class ActionController {
    private static final ActionController INSTANCE = new ActionController();
    private final MinecraftClient client;
    private final Executor mainExecutor;

    // 动作状态
    private boolean isMining = false;
    private BlockPos miningTarget = null;
    private float targetYaw = 0;
    private float targetPitch = 0;
    private boolean smoothLooking = false;
    private static final float LOOK_SPEED = 15f; // 每tick最大转动角度，模拟真人鼠标速度

    private ActionController() {
        this.client = MinecraftClient.getInstance();
        this.mainExecutor = client;
    }

    public static ActionController getInstance() {
        return INSTANCE;
    }

    // ==================== 基础输入原语（线程安全） ====================

    /**
     * 在主线程执行操作
     */
    private CompletableFuture<Void> runOnMainThread(Runnable task) {
        return CompletableFuture.runAsync(task, mainExecutor);
    }

    /**
     * 按下按键
     */
    public CompletableFuture<Void> pressKey(KeyBinding key) {
        return runOnMainThread(() -> {
            if (!key.isPressed()) {
                KeyBinding.setKeyPressed(key.getDefaultKey(), true);
                key.setPressed(true);
            }
        });
    }

    /**
     * 释放按键
     */
    public CompletableFuture<Void> releaseKey(KeyBinding key) {
        return runOnMainThread(() -> {
            if (key.isPressed()) {
                KeyBinding.setKeyPressed(key.getDefaultKey(), false);
                key.setPressed(false);
            }
        });
    }

    /**
     * 点击按键（按下+延迟+释放，模拟真人点击）
     */
    public CompletableFuture<Void> clickKey(KeyBinding key, int pressTimeMs) {
        return pressKey(key).thenRunAsync(() -> {
            try {
                Thread.sleep(pressTimeMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, Executors.newSingleThreadExecutor()).thenCompose(v -> releaseKey(key));
    }

    /**
     * 点击按键（默认50ms按下时间，真人点击速度）
     */
    public CompletableFuture<Void> clickKey(KeyBinding key) {
        return clickKey(key, 50);
    }

    /**
     * 移动鼠标（相对转动视角）
     * @param deltaYaw 水平转动量
     * @param deltaPitch 垂直转动量
     */
    public CompletableFuture<Void> mouseMove(float deltaYaw, float deltaPitch) {
        return runOnMainThread(() -> {
            ClientPlayerEntity player = client.player;
            if (player == null) return;
            player.setYaw(player.getYaw() + deltaYaw);
            player.setPitch(MathHelper.clamp(player.getPitch() + deltaPitch, -90f, 90f));
        });
    }

    /**
     * 按下鼠标左键
     */
    public CompletableFuture<Void> pressLeftClick() {
        return pressKey(client.options.attackKey);
    }

    /**
     * 释放鼠标左键
     */
    public CompletableFuture<Void> releaseLeftClick() {
        return releaseKey(client.options.attackKey);
    }

    /**
     * 点击鼠标左键
     */
    public CompletableFuture<Void> clickLeft() {
        return clickKey(client.options.attackKey);
    }

    /**
     * 按下鼠标右键
     */
    public CompletableFuture<Void> pressRightClick() {
        return pressKey(client.options.useKey);
    }

    /**
     * 释放鼠标右键
     */
    public CompletableFuture<Void> releaseRightClick() {
        return releaseKey(client.options.useKey);
    }

    /**
     * 点击鼠标右键
     */
    public CompletableFuture<Void> clickRight() {
        return clickKey(client.options.useKey);
    }

    // ==================== 状态查询（线程安全） ====================

    public boolean isMining() {
        return isMining;
    }

    public BlockPos getMiningTarget() {
        return miningTarget;
    }

    public float getCurrentYaw() {
        ClientPlayerEntity player = client.player;
        return player != null ? player.getYaw() : 0;
    }

    public float getCurrentPitch() {
        ClientPlayerEntity player = client.player;
        return player != null ? player.getPitch() : 0;
    }

    public int getSelectedHotbarSlot() {
        ClientPlayerEntity player = client.player;
        return player != null ? player.getInventory().selectedSlot : 0;
    }

    // ==================== 高层原子动作 ====================

    /**
     * 平滑看向指定角度（模拟真人转动鼠标，不是瞬间转过去）
     * @param yaw 目标水平角度
     * @param pitch 目标垂直角度
     */
    public CompletableFuture<Void> lookAt(float yaw, float pitch) {
        return runOnMainThread(() -> {
            this.targetYaw = yaw;
            this.targetPitch = MathHelper.clamp(pitch, -90f, 90f);
            this.smoothLooking = true;
        });
    }

    /**
     * 看向指定方块位置
     */
    public CompletableFuture<Void> lookAt(BlockPos pos) {
        ClientPlayerEntity player = client.player;
        if (player == null) return CompletableFuture.completedFuture(null);
        Vec3d eyePos = player.getEyePos();
        Vec3d targetPos = pos.toCenterPos();
        double dx = targetPos.x - eyePos.x;
        double dy = targetPos.y - eyePos.y;
        double dz = targetPos.z - eyePos.z;
        double dHorizontal = Math.sqrt(dx * dx + dz * dz);
        float yaw = (float) (MathHelper.atan2(dz, dx) * 180 / Math.PI) - 90f;
        float pitch = (float) -(MathHelper.atan2(dy, dHorizontal) * 180 / Math.PI);
        return lookAt(yaw, pitch);
    }

    /**
     * 看向指定实体
     */
    public CompletableFuture<Void> lookAt(Entity entity) {
        return lookAt(entity.getBlockPos());
    }

    /**
     * 停止平滑看向
     */
    public CompletableFuture<Void> stopLooking() {
        return runOnMainThread(() -> smoothLooking = false);
    }

    /**
     * 设置移动状态
     * @param forward 前后移动：-1=后退，0=不动，1=前进
     * @param strafe 左右移动：-1=左，0=不动，1=右
     */
    public CompletableFuture<Void> setMovement(float forward, float strafe) {
        return runOnMainThread(() -> {
            KeyBinding forwardKey = client.options.forwardKey;
            KeyBinding backKey = client.options.backKey;
            KeyBinding leftKey = client.options.leftKey;
            KeyBinding rightKey = client.options.rightKey;

            // 前后
            if (forward > 0.1f) {
                KeyBinding.setKeyPressed(forwardKey.getDefaultKey(), true);
                forwardKey.setPressed(true);
                KeyBinding.setKeyPressed(backKey.getDefaultKey(), false);
                backKey.setPressed(false);
            } else if (forward < -0.1f) {
                KeyBinding.setKeyPressed(backKey.getDefaultKey(), true);
                backKey.setPressed(true);
                KeyBinding.setKeyPressed(forwardKey.getDefaultKey(), false);
                forwardKey.setPressed(false);
            } else {
                KeyBinding.setKeyPressed(forwardKey.getDefaultKey(), false);
                forwardKey.setPressed(false);
                KeyBinding.setKeyPressed(backKey.getDefaultKey(), false);
                backKey.setPressed(false);
            }

            // 左右
            if (strafe > 0.1f) {
                KeyBinding.setKeyPressed(leftKey.getDefaultKey(), true);
                leftKey.setPressed(true);
                KeyBinding.setKeyPressed(rightKey.getDefaultKey(), false);
                rightKey.setPressed(false);
            } else if (strafe < -0.1f) {
                KeyBinding.setKeyPressed(rightKey.getDefaultKey(), true);
                rightKey.setPressed(true);
                KeyBinding.setKeyPressed(leftKey.getDefaultKey(), false);
                leftKey.setPressed(false);
            } else {
                KeyBinding.setKeyPressed(leftKey.getDefaultKey(), false);
                leftKey.setPressed(false);
                KeyBinding.setKeyPressed(rightKey.getDefaultKey(), false);
                rightKey.setPressed(false);
            }
        });
    }

    /**
     * 停止所有移动
     */
    public CompletableFuture<Void> stopMovement() {
        return setMovement(0, 0);
    }

    /**
     * 开始蹲下（Shift）
     */
    public CompletableFuture<Void> startSneak() {
        return pressKey(client.options.sneakKey);
    }

    /**
     * 停止蹲下
     */
    public CompletableFuture<Void> stopSneak() {
        return releaseKey(client.options.sneakKey);
    }

    /**
     * 设置蹲下状态
     */
    public CompletableFuture<Void> setSneak(boolean sneak) {
        return sneak ? startSneak() : stopSneak();
    }

    /**
     * 开始疾跑（Ctrl）
     */
    public CompletableFuture<Void> startSprint() {
        return runOnMainThread(() -> {
            ClientPlayerEntity player = client.player;
            if (player != null) {
                player.setSprinting(true);
            }
            pressKey(client.options.sprintKey);
        });
    }

    /**
     * 停止疾跑
     */
    public CompletableFuture<Void> stopSprint() {
        return runOnMainThread(() -> {
            ClientPlayerEntity player = client.player;
            if (player != null) {
                player.setSprinting(false);
            }
            releaseKey(client.options.sprintKey);
        });
    }

    /**
     * 设置疾跑状态
     */
    public CompletableFuture<Void> setSprint(boolean sprint) {
        return sprint ? startSprint() : stopSprint();
    }

    /**
     * 跳跃一次（点击空格）
     */
    public CompletableFuture<Void> jump() {
        return clickKey(client.options.jumpKey);
    }

    /**
     * 开始挖掘指定方块
     * 自动看向方块，按住左键直到方块破坏
     * @param pos 要挖掘的方块位置
     */
    public CompletableFuture<Void> startMining(BlockPos pos) {
        return runOnMainThread(() -> {
            if (isMining) {
                releaseLeftClick();
            }
            this.isMining = true;
            this.miningTarget = pos;
            lookAt(pos);
            pressLeftClick();
        });
    }

    /**
     * 停止挖掘
     */
    public CompletableFuture<Void> stopMining() {
        return runOnMainThread(() -> {
            this.isMining = false;
            this.miningTarget = null;
            releaseLeftClick();
        });
    }

    /**
     * 丢弃当前手持物品一个（按Q）
     */
    public CompletableFuture<Void> dropItem() {
        return clickKey(client.options.dropKey);
    }

    /**
     * 丢弃当前手持整组物品（Ctrl+Q）
     */
    public CompletableFuture<Void> dropAllItem() {
        return runOnMainThread(() -> {
            KeyBinding.setKeyPressed(InputUtil.fromKeyCode(GLFW.GLFW_KEY_LEFT_CONTROL, 0), true);
            clickKey(client.options.dropKey, 50);
            KeyBinding.setKeyPressed(InputUtil.fromKeyCode(GLFW.GLFW_KEY_LEFT_CONTROL, 0), false);
        });
    }

    /**
     * 切换到指定快捷栏槽位
     * @param slot 槽位 0-8 对应 1-9 键
     */
    public CompletableFuture<Void> selectHotbarSlot(int slot) {
        final int finalSlot = MathHelper.clamp(slot, 0, 8);
        return runOnMainThread(() -> {
            ClientPlayerEntity player = client.player;
            if (player != null) {
                player.getInventory().selectedSlot = finalSlot;
            }
            // 模拟按数字键
            KeyBinding key = client.options.hotbarKeys[finalSlot];
            clickKey(key);
        });
    }

    /**
     * 交换主副手物品（按F）
     */
    public CompletableFuture<Void> swapHands() {
        return clickKey(client.options.swapHandsKey);
    }

    /**
     * 打开背包（按E）
     */
    public CompletableFuture<Void> openInventory() {
        return clickKey(client.options.inventoryKey);
    }

    /**
     * 关闭当前界面（按ESC）
     */
    public CompletableFuture<Void> closeScreen() {
        return runOnMainThread(() -> {
            if (client.currentScreen != null) {
                client.currentScreen.close();
            }
            pressKey(client.options.inventoryKey); // ESC和E都可以关闭界面
            releaseKey(client.options.inventoryKey);
        });
    }

    /**
     * 使用物品/放置方块/右键交互（点击右键）
     */
    public CompletableFuture<Void> useItem() {
        return clickRight();
    }

    /**
     * 持续使用物品（按住右键，比如吃东西、拉弓、格挡）
     */
    public CompletableFuture<Void> startUsingItem() {
        return pressRightClick();
    }

    /**
     * 停止使用物品
     */
    public CompletableFuture<Void> stopUsingItem() {
        return releaseRightClick();
    }

    /**
     * 攻击实体（左键点击一次）
     */
    public CompletableFuture<Void> attack() {
        return clickLeft();
    }

    // ==================== 帧更新方法，每tick调用一次，处理平滑动作 ====================

    /**
     * 每客户端tick调用，处理平滑视角转动等持续动作
     * 由Mixins或者Tick事件自动调用
     */
    public void tick() {
        ClientPlayerEntity player = client.player;
        if (player == null) return;

        // 平滑视角转动
        if (smoothLooking) {
            float currentYaw = player.getYaw();
            float currentPitch = player.getPitch();

            float deltaYaw = MathHelper.wrapDegrees(targetYaw - currentYaw);
            float deltaPitch = targetPitch - currentPitch;

            // 每tick最多转LOOK_SPEED度，模拟真人鼠标速度
            deltaYaw = MathHelper.clamp(deltaYaw, -LOOK_SPEED, LOOK_SPEED);
            deltaPitch = MathHelper.clamp(deltaPitch, -LOOK_SPEED, LOOK_SPEED);

            player.setYaw(currentYaw + deltaYaw);
            player.setPitch(currentPitch + deltaPitch);

            // 到达目标角度后停止
            if (Math.abs(deltaYaw) < 0.1f && Math.abs(deltaPitch) < 0.1f) {
                smoothLooking = false;
                player.setYaw(targetYaw);
                player.setPitch(targetPitch);
            }
        }

        // 检查挖掘是否完成
        if (isMining && miningTarget != null) {
            // 如果方块已经被破坏，停止挖掘
            if (client.world != null && client.world.isAir(miningTarget)) {
                isMining = false;
                miningTarget = null;
                releaseLeftClick();
            }
        }
    }

    /**
     * 重置所有动作状态，停止所有按键
     */
    public CompletableFuture<Void> resetAll() {
        return runOnMainThread(() -> {
            stopMovement();
            stopSneak();
            stopSprint();
            stopMining();
            stopLooking();
            releaseLeftClick();
            releaseRightClick();
            isMining = false;
            miningTarget = null;
            smoothLooking = false;
        });
    }
}
