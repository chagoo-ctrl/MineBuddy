package com.minebuddy.command;

import com.minebuddy.test.MiningTest;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.text.Text;

/**
 * 命令注册类
 */
public class CommandRegistry implements ClientCommandRegistrationCallback {

    public static void register() {
        ClientCommandRegistrationCallback.EVENT.register(new CommandRegistry());
    }

    @Override
    public void register(CommandDispatcher<FabricClientCommandSource> dispatcher, CommandRegistryAccess registryAccess) {
        // 注册 /testmine 命令
        dispatcher.register(ClientCommandManager.literal("testmine")
                .then(ClientCommandManager.literal("start")
                        .executes(context -> {
                            MiningTest.getInstance().start();
                            return 1;
                        })
                )
                .then(ClientCommandManager.literal("stop")
                        .executes(context -> {
                            MiningTest.getInstance().stop();
                            return 1;
                        })
                )
                .executes(context -> {
                    context.getSource().sendFeedback(Text.literal("§e用法：\n/testmine start - 开始自动挖10个木头\n/testmine stop - 停止"));
                    return 1;
                })
        );
    }
}
