package com.example.mixin;

import com.example.RelationTracker;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaero.common.icon.XaeroIcon;
import xaero.hud.minimap.element.render.MinimapElementGraphics;
import xaero.hud.minimap.element.render.MinimapElementRenderInfo;
import xaero.hud.minimap.element.render.MinimapElementRenderer;
import xaero.hud.minimap.radar.render.element.RadarRenderContext;
import xaero.hud.minimap.radar.render.element.RadarRenderer;
import xaeroplus.feature.render.DrawHelper;

@Mixin(value = RadarRenderer.class, remap = false)
public abstract class MixinRadarRenderer extends MinimapElementRenderer<Entity, RadarRenderContext> {

    @Shadow private RenderType dotsRenderType;
    @Shadow private MultiBufferSource.BufferSource minimapBufferSource;

    private static final ThreadLocal<Entity> currentEntity = new ThreadLocal<>();

    @Inject(
        method = "renderElement(Lnet/minecraft/world/entity/Entity;ZZDFDDLxaero/hud/minimap/element/render/MinimapElementRenderInfo;Lxaero/hud/minimap/element/render/MinimapElementGraphics;Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;)Z",
        at = @At("HEAD"),
        remap = true
    )
    private void captureEntity(
        Entity e, boolean highlighted, boolean outOfBounds,
        double optionalDepth, float optionalScale, double partialX, double partialY,
        MinimapElementRenderInfo renderInfo, MinimapElementGraphics guiGraphics,
        MultiBufferSource.BufferSource vanillaBufferSource,
        CallbackInfoReturnable<Boolean> cir
    ) {
        currentEntity.set(e);
    }

    @Inject(method = "renderIcon", at = @At("HEAD"), remap = false)
    private void drawIconBackground(
        XaeroIcon entityIcon, double optionalScale, double figureScale,
        float offY, boolean cave, PoseStack matrixStack, CallbackInfo ci
    ) {
        Entity e = currentEntity.get();
        if (!(e instanceof Player player)) return;
        if (player == Minecraft.getInstance().player) return;

        int relation = RelationTracker.getRelation(player);
        int argbColor = RelationTracker.getColor(relation);

        if ((argbColor >> 24 & 0xFF) == 0) return;

        float r = ((argbColor >> 16) & 0xFF) / 255f;
        float g = ((argbColor >> 8) & 0xFF) / 255f;
        float b = (argbColor & 0xFF) / 255f;
        float a = ((argbColor >> 24) & 0xFF) / 255f;

        double clampedScale = Math.max(1.0, figureScale * optionalScale);
        float half = (float)(31.0 / clampedScale) + 3f;

        Matrix4f matrix = matrixStack.last().pose();
        VertexConsumer buf = minimapBufferSource.getBuffer(dotsRenderType);
        DrawHelper.fillIntoExistingBuffer(matrix, buf, -half, -half, half, half, r, g, b, a);
    }
}
