package com.minebuddy.perception;

import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import java.util.List;
import java.util.Map;

/**
 * 感知快照 - 每一帧生成一次，包含AI能"看到"的所有世界信息
 * 只包含渲染层可见的内容，和玩家屏幕上看到的完全一致
 */
public record PerceptionSnapshot(
        // 自身状态
        SelfState self,
        // 手持状态
        HandState hand,
        // 背包状态
        InventoryState inventory,
        // 可见方块列表（正在渲染的）
        List<VisibleBlock> blocks,
        // 可见实体列表（正在渲染的）
        List<VisibleEntity> entities,
        // 可见掉落物列表（正在渲染的）
        List<VisibleItem> items,
        // 世界状态
        WorldState world,
        // 游戏状态
        GameState game
) {
    /**
     * 自身状态
     */
    public record SelfState(
            double x, double y, double z,
            float yaw, float pitch,
            float hp, float maxHp,
            int hunger, float saturation,
            boolean isOnGround,
            boolean isSneaking,
            boolean isSprinting,
            boolean isSwimming,
            boolean isBurning,
            int air,
            int experienceLevel,
            float experienceProgress
    ) {}

    /**
     * 手持状态
     */
    public record HandState(
            ItemInfo mainHand,
            ItemInfo offHand,
            int selectedSlot
    ) {}

    /**
     * 物品信息
     */
    public record ItemInfo(
            String id,
            int count,
            int damage,
            int maxDamage,
            Map<String, Integer> enchantments
    ) {}

    /**
     * 背包状态
     */
    public record InventoryState(
            List<ItemInfo> hotbar,    // 快捷栏 9格
            List<ItemInfo> main,      // 主背包 27格
            List<ItemInfo> armor,     // 盔甲 4格
            List<ItemInfo> offhand,   // 副手 1格
            ItemInfo cursorItem       // 鼠标拖动的物品
    ) {}

    /**
     * 可见方块（正在渲染的方块）
     */
    public record VisibleBlock(
            String id,
            int x, int y, int z,
            double distance,
            String state,           // 方块状态JSON
            int visibleFaces        // 可见面数量
    ) {}

    /**
     * 可见实体（正在渲染的实体）
     */
    public record VisibleEntity(
            int entityId,
            String type,
            double x, double y, double z,
            double distance,
            float hp, float maxHp,
            boolean isHostile,
            boolean isBaby,
            float yaw, float pitch
    ) {}

    /**
     * 可见掉落物（正在渲染的掉落物）
     */
    public record VisibleItem(
            int entityId,
            String id,
            double x, double y, double z,
            double distance,
            int count,
            int age                  // 已存在tick数，6000tick后消失
    ) {}

    /**
     * 世界状态
     */
    public record WorldState(
            long worldTime,
            long dayTime,           // 0~24000
            boolean isDay,
            boolean isNight,
            String weather,         // CLEAR/RAIN/THUNDER
            int lightLevel,
            String dimension,       // OVERWORLD/NETHER/END
            String difficulty,
            int moonPhase,
            boolean canSeeSky
    ) {}

    /**
     * 游戏状态
     */
    public record GameState(
            boolean isDead,
            boolean isGamePaused,
            String openScreen,      // 当前打开的GUI
            int fps
    ) {}
}
