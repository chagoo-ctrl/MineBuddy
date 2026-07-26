package com.minebuddy.perception;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 感知收集器 - 每帧收集所有渲染的方块/实体，生成感知快照
 * 由Mixin在渲染时调用addBlock/addEntity，帧结束时调用buildSnapshot生成快照
 */
public class PerceptionCollector {
    private static final PerceptionCollector INSTANCE = new PerceptionCollector();

    // 每帧临时缓冲区，帧开始时清空，渲染过程中填充
    private final Map<BlockPos, PerceptionSnapshot.VisibleBlock> blockBuffer = new ConcurrentHashMap<>();
    private final Map<Integer, PerceptionSnapshot.VisibleEntity> entityBuffer = new ConcurrentHashMap<>();
    private final Map<Integer, PerceptionSnapshot.VisibleItem> itemBuffer = new ConcurrentHashMap<>();

    // 最新的感知快照
    private volatile PerceptionSnapshot latestSnapshot;
    private long lastSnapshotTime = 0;
    private int frameCount = 0;

    private PerceptionCollector() {}

    public static PerceptionCollector getInstance() {
        return INSTANCE;
    }

    /**
     * 帧开始时调用，清空缓冲区
     */
    public void beginFrame() {
        blockBuffer.clear();
        entityBuffer.clear();
        itemBuffer.clear();
    }

    /**
     * 添加一个正在渲染的方块（由Mixin调用）
     * 自动去重：同一个方块多面渲染只记录一次
     */
    public void addBlock(BlockPos pos, BlockState state) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        if (blockBuffer.containsKey(pos)) return; // 已记录过，跳过

        Vec3d eyePos = client.player.getEyePos();
        double distance = Math.sqrt(pos.getSquaredDistance(eyePos));

        String blockId = Registries.BLOCK.getId(state.getBlock()).toString();
        // 简单计算可见面数量（后面可以优化）
        int visibleFaces = 1;

        blockBuffer.put(pos, new PerceptionSnapshot.VisibleBlock(
                blockId,
                pos.getX(), pos.getY(), pos.getZ(),
                Math.round(distance * 100.0) / 100.0,
                state.toString(),
                visibleFaces
        ));
    }

    /**
     * 添加一个正在渲染的实体（由Mixin调用）
     * 自动去重：同一个实体多次渲染只记录一次
     */
    public void addEntity(Entity entity) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;
        if (entity == client.player) return; // 跳过自己

        Vec3d eyePos = client.player.getEyePos();
        double distance = Math.sqrt(entity.squaredDistanceTo(eyePos));
        String entityType = Registries.ENTITY_TYPE.getId(entity.getType()).toString();

        // 掉落物单独处理
        if (entity instanceof ItemEntity itemEntity) {
            if (itemBuffer.containsKey(entity.getId())) return;
            ItemStack stack = itemEntity.getStack();
            String itemId = Registries.ITEM.getId(stack.getItem()).toString();
            itemBuffer.put(entity.getId(), new PerceptionSnapshot.VisibleItem(
                    entity.getId(),
                    itemId,
                    entity.getX(), entity.getY(), entity.getZ(),
                    Math.round(distance * 100.0) / 100.0,
                    stack.getCount(),
                    itemEntity.getItemAge()
            ));
            return;
        }

        if (entityBuffer.containsKey(entity.getId())) return;

        // 普通实体
        float hp = 0, maxHp = 0;
        boolean isHostile = entity instanceof Monster;
        boolean isBaby = false;

        if (entity instanceof LivingEntity living) {
            hp = living.getHealth();
            maxHp = living.getMaxHealth();
            isBaby = living.isBaby();
        }

        entityBuffer.put(entity.getId(), new PerceptionSnapshot.VisibleEntity(
                entity.getId(),
                entityType,
                entity.getX(), entity.getY(), entity.getZ(),
                Math.round(distance * 100.0) / 100.0,
                hp, maxHp,
                isHostile,
                isBaby,
                entity.getYaw(), entity.getPitch()
        ));
    }

    /**
     * 帧结束时调用，生成完整感知快照
     */
    public PerceptionSnapshot buildSnapshot() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) {
            return null;
        }

        frameCount++;
        long now = System.currentTimeMillis();

        // 1. 收集自身状态
        var player = client.player;
        PerceptionSnapshot.SelfState self = new PerceptionSnapshot.SelfState(
                player.getX(), player.getY(), player.getZ(),
                player.getYaw(), player.getPitch(),
                player.getHealth(), player.getMaxHealth(),
                player.getHungerManager().getFoodLevel(),
                player.getHungerManager().getSaturationLevel(),
                player.isOnGround(),
                player.isSneaking(),
                player.isSprinting(),
                player.isSwimming(),
                player.isOnFire(),
                player.getAir(),
                player.experienceLevel,
                player.experienceProgress
        );

        // 2. 收集手持状态
        var mainHand = getItemInfo(player.getMainHandStack());
        var offHand = getItemInfo(player.getOffHandStack());
        PerceptionSnapshot.HandState hand = new PerceptionSnapshot.HandState(
                mainHand, offHand, player.getInventory().selectedSlot
        );

        // 3. 收集背包
        List<PerceptionSnapshot.ItemInfo> hotbar = new ArrayList<>();
        List<PerceptionSnapshot.ItemInfo> main = new ArrayList<>();
        List<PerceptionSnapshot.ItemInfo> armor = new ArrayList<>();
        for (int i = 0; i < 9; i++) hotbar.add(getItemInfo(player.getInventory().getStack(i)));
        for (int i = 9; i < 36; i++) main.add(getItemInfo(player.getInventory().getStack(i)));
        for (int i = 0; i < 4; i++) armor.add(getItemInfo(player.getInventory().armor.get(i)));
        var cursor = getItemInfo(player.currentScreenHandler.getCursorStack());
        PerceptionSnapshot.InventoryState inventory = new PerceptionSnapshot.InventoryState(
                hotbar, main, armor, List.of(offHand), cursor
        );

        // 4. 收集世界状态
        var world = client.world;
        BlockPos playerPos = player.getBlockPos();
        long dayTime = world.getTimeOfDay() % 24000;
        String weather = world.isThundering() ? "THUNDER" : world.isRaining() ? "RAIN" : "CLEAR";
        String dimension = world.getRegistryKey().getValue().toString();
        int light = world.getLightLevel(LightType.BLOCK, playerPos);
        PerceptionSnapshot.WorldState worldState = new PerceptionSnapshot.WorldState(
                world.getTime(),
                dayTime,
                world.isDay(),
                !world.isDay(),
                weather,
                light,
                dimension,
                world.getDifficulty().getName(),
                world.getMoonPhase(),
                world.isSkyVisible(playerPos)
        );

        // 5. 收集游戏状态
        String openScreen = player.currentScreenHandler == client.player.playerScreenHandler ?
                "INVENTORY" : player.currentScreenHandler.getClass().getSimpleName();
        int fps = client.getCurrentFps();
        PerceptionSnapshot.GameState gameState = new PerceptionSnapshot.GameState(
                player.isDead(),
                client.isPaused(),
                openScreen,
                fps
        );

        // 6. 组装快照
        PerceptionSnapshot snapshot = new PerceptionSnapshot(
                self,
                hand,
                inventory,
                new ArrayList<>(blockBuffer.values()),
                new ArrayList<>(entityBuffer.values()),
                new ArrayList<>(itemBuffer.values()),
                worldState,
                gameState
        );

        this.latestSnapshot = snapshot;
        this.lastSnapshotTime = now;
        return snapshot;
    }

    public PerceptionSnapshot getLatestSnapshot() {
        return latestSnapshot;
    }

    public long getLastSnapshotTime() {
        return lastSnapshotTime;
    }

    public int getFrameCount() {
        return frameCount;
    }

    /**
     * 工具方法：ItemStack转ItemInfo
     */
    private PerceptionSnapshot.ItemInfo getItemInfo(ItemStack stack) {
        if (stack.isEmpty()) {
            return new PerceptionSnapshot.ItemInfo("minecraft:air", 0, 0, 0, java.util.Map.of());
        }
        String id = Registries.ITEM.getId(stack.getItem()).toString();
        return new PerceptionSnapshot.ItemInfo(
                id,
                stack.getCount(),
                stack.getDamage(),
                stack.getMaxDamage(),
                java.util.Map.of() // 附魔后面再加
        );
    }
}
