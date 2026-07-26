package com.minebuddy.mixin;

import com.minebuddy.perception.PerceptionCollector;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hook方块渲染 - 所有要渲染到屏幕上的方块都会经过这里
 * 我们在这里把方块加入感知缓冲区
 */
@Mixin(BlockRenderManager.class)
public abstract class BlockRenderManagerMixin {

    @Inject(method = "renderBlock", at = @At("HEAD"))
    private void onRenderBlock(BlockState state, BlockPos pos, BlockRenderView world,
                               MatrixStack matrices, VertexConsumer vertexConsumer,
                               boolean cull, CallbackInfoReturnable<Boolean> cir) {
        // 跳过空气方块
        if (state.isAir()) return;

        // 将正在渲染的方块加入感知缓冲区
        PerceptionCollector.getInstance().addBlock(pos, state);
    }
}
