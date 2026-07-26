package com.minebuddy;

import com.minebuddy.action.ActionController;
import com.minebuddy.command.CommandRegistry;
import com.minebuddy.perception.PerceptionCollector;
import com.minebuddy.test.GatherTest;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

public class MineBuddyClient implements ClientModInitializer {
    private int tickCounter = 0;

    @Override
    public void onInitializeClient() {
        System.out.println("[MineBuddy] 客户端初始化中...");

        // 注册命令
        CommandRegistry.register();
        System.out.println("[MineBuddy] 命令注册完成，输入 /testget 查看用法");

        // 注册每tick事件
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // 每tick更新动作控制器
            ActionController.getInstance().tick();
            // 每tick更新收集测试状态机
            GatherTest.getInstance().tick();

            tickCounter++;
            // 每秒（20tick）输出一次运行统计，测试运行时不输出避免刷屏
            if (tickCounter % 20 == 0 && !GatherTest.getInstance().isRunning()) {
                PerceptionCollector collector = PerceptionCollector.getInstance();
                var snapshot = collector.getLatestSnapshot();
                if (snapshot != null && client.player != null) {
                    String msg = String.format("§a[MineBuddy] 运行正常 | 方块: %d | 实体: %d | 掉落物: %d | FPS: %d",
                            snapshot.blocks().size(),
                            snapshot.entities().size(),
                            snapshot.items().size(),
                            client.getCurrentFps()
                    );
                    client.player.sendMessage(Text.literal(msg), true);
                }
            }
        });

        System.out.println("[MineBuddy] 初始化完成！感知层和动作层已加载");
        System.out.println("[MineBuddy] 测试命令：/testget <数量> <物品ID>，例如 /testget 10 log");
    }
}
