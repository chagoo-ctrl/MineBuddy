package com.minebuddy.mixin;

import com.minebuddy.perception.PerceptionCollector;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hook实体渲染 - 所有要渲染到屏幕上的实体（生物、掉落物、矿车等）都会经过这里
 * 我们在这里把实体加入感知缓冲区
 */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin<T extends Entity> {

    @Inject(method = "render", at = @At("HEAD"))
    private void onRenderEntity(T entity, float yaw, float tickDelta,
                                MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                int light, CallbackInfo ci) {
        // 将正在渲染的实体加入感知缓冲区（自动区分普通实体和掉落物）
        PerceptionCollector.getInstance().addEntity(entity);
    }
}
