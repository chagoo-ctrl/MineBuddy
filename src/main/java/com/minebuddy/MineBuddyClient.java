package com.minebuddy;

import com.minebuddy.perception.PerceptionCollector;
import com.minebuddy.perception.PerceptionSnapshot;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MineBuddy - Minecraft通用AI陪玩客户端
 * 感知层：从渲染流提取世界状态
 * 大脑层：PDDL规划 + 三级缓存行为树
 * 肌肉层：模拟键鼠输入
 */
public class MineBuddyClient implements ClientModInitializer {
    public static final String MOD_ID = "minebuddy";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private int tickCounter = 0;

    @Override
    public void onInitializeClient() {
        LOGGER.info("MineBuddy 感知层已加载！");

        // 每20tick（1秒）打印一次感知统计，验证功能正常
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;

            tickCounter++;
            if (tickCounter % 20 == 0) {
                PerceptionSnapshot snapshot = PerceptionCollector.getInstance().getLatestSnapshot();
                if (snapshot != null) {
                    // 发送调试信息到聊天栏
                    client.player.sendMessage(Text.literal(
                            String.format("§a[MineBuddy] 感知正常 | 方块: %d | 实体: %d | 掉落物: %d | FPS: %d",
                                    snapshot.blocks().size(),
                                    snapshot.entities().size(),
                                    snapshot.items().size(),
                                    snapshot.game().fps())
                    ), true);
                }
            }
        });
    }
}
