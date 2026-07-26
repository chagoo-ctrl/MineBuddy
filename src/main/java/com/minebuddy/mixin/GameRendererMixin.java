package com.minebuddy.mixin;

import com.minebuddy.perception.PerceptionCollector;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hook游戏渲染主循环 - 在每帧渲染前后触发感知收集
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    /**
     * 帧开始渲染前：清空感知缓冲区
     */
    @Inject(method = "renderWorld", at = @At("HEAD"))
    private void onRenderWorldStart(float tickDelta, long limitTime, CallbackInfo ci) {
        PerceptionCollector.getInstance().beginFrame();
    }

    /**
     * 世界渲染结束后：所有方块/实体都已经渲染过了，生成完整感知快照
     */
    @Inject(method = "renderWorld", at = @At("TAIL"))
    private void onRenderWorldEnd(float tickDelta, long limitTime, CallbackInfo ci) {
        PerceptionCollector.getInstance().buildSnapshot();
    }
}
