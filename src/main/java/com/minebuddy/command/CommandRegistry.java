package com.minebuddy.command;

import com.minebuddy.test.GatherTest;
import com.minebuddy.test.KillSheepTest;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
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
        // 注册 /testget 命令：/testget <数量> <物品ID>
        dispatcher.register(ClientCommandManager.literal("testget")
                .then(ClientCommandManager.argument("count", IntegerArgumentType.integer(1, 64))
                        .then(ClientCommandManager.argument("itemId", StringArgumentType.string())
                                .executes(context -> {
                                    int count = IntegerArgumentType.getInteger(context, "count");
                                    String itemId = StringArgumentType.getString(context, "itemId");
                                    GatherTest.getInstance().testGet(count, itemId);
                                    return 1;
                                })
                        )
                )
                .then(ClientCommandManager.literal("stop")
                        .executes(context -> {
                            GatherTest.getInstance().stop();
                            return 1;
                        })
                )
                .executes(context -> {
                    context.getSource().sendFeedback(Text.literal("§e用法：\n" +
                            "/testget <数量> <物品ID> - 自动收集指定数量的方块\n" +
                            "/testget stop - 停止收集\n" +
                            "示例：\n" +
                            "/testget 10 log - 收集10个木头\n" +
                            "/testget 20 stone - 收集20个石头\n" +
                            "/testget 5 dirt - 收集5个泥土\n" +
                            "/testget 3 iron_ore - 收集3个铁矿石"));
                    return 1;
                })
        );

        // 注册 /killsheep 命令：/killsheep [数量]，默认杀1只
        dispatcher.register(ClientCommandManager.literal("killsheep")
                .then(ClientCommandManager.argument("count", IntegerArgumentType.integer(1, 64))
                        .executes(context -> {
                            int count = IntegerArgumentType.getInteger(context, "count");
                            KillSheepTest.getInstance().startKillSheep(count);
                            return 1;
                        })
                )
                .then(ClientCommandManager.literal("stop")
                        .executes(context -> {
                            KillSheepTest.getInstance().stop();
                            return 1;
                        })
                )
                .executes(context -> {
                    KillSheepTest.getInstance().startKillSheep(1);
                    return 1;
                })
        );
    }
}
