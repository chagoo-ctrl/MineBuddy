package com.minebuddy;
import com.minebuddy.action.ActionController;
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
        LOGGER.info("MineBuddy 已加载！");
        LOGGER.info(" - 感知层：渲染管线钩子");
        LOGGER.info(" - 动作层：原子动作库");
        // 每客户端tick更新动作状态
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // 更新动作控制器（平滑视角、挖掘状态等）
            ActionController.getInstance().tick();

            if (client.player == null) return;
            tickCounter++;
            // 每20tick（1秒）打印一次感知统计，验证功能正常
            if (tickCounter % 20 == 0) {
                PerceptionSnapshot snapshot = PerceptionCollector.getInstance().getLatestSnapshot();
                if (snapshot != null) {
                    // 发送调试信息到聊天栏
                    client.player.sendMessage(Text.literal(
                            String.format("§a[MineBuddy] 运行正常 | 方块: %d | 实体: %d | 掉落物: %d | FPS: %d",
                                    snapshot.blocks().size(),
                                    snapshot.entities().size(),
                                    snapshot.items().size(),
                                    snapshot.game().fps())
                    ), false);
                }
            }
        });
    }
}
