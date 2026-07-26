package com.minebuddy;
import com.minebuddy.action.ActionController;
import com.minebuddy.command.CommandRegistry;
import com.minebuddy.perception.PerceptionCollector;
import com.minebuddy.perception.PerceptionSnapshot;
import com.minebuddy.test.MiningTest;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.text.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * MineBuddy - Minecraft通用AI陪玩客户端
 * 感知层：从渲染流提取世界状态
 * 大脑层：PDDL规划 + 三级缓存行为树
 * 肌肉层：直接调用游戏API（高效模式）
 */
public class MineBuddyClient implements ClientModInitializer {
    public static final String MOD_ID = "minebuddy";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    private int tickCounter = 0;
    @Override
    public void onInitializeClient() {
        LOGGER.info("MineBuddy 已加载！");
        LOGGER.info(" - 感知层：渲染管线钩子");
        LOGGER.info(" - 动作层：原子动作库（高效模式）");
        LOGGER.info(" - 测试命令：/testmine start/stop");

        // 注册命令
        CommandRegistry.register();

        // 每客户端tick更新动作状态
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // 更新动作控制器（持续挖掘等）
            ActionController.getInstance().tick();
            // 更新挖木头测试状态机
            MiningTest.getInstance().tick();

            if (client.player == null) return;
            tickCounter++;
            // 每20tick（1秒）打印一次感知统计，验证功能正常（测试运行时不打印）
            if (tickCounter % 20 == 0 && !MiningTest.getInstance().isRunning()) {
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
