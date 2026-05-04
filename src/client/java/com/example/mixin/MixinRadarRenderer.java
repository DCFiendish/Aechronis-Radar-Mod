package com.example.mixin;

import com.example.RelationTracker;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import xaero.common.graphics.CustomRenderTypes;
import xaero.common.icon.XaeroIcon;
import xaero.hud.minimap.element.render.MinimapElementGraphics;
import xaero.hud.minimap.element.render.MinimapElementRenderInfo;
import xaero.hud.minimap.radar.render.element.RadarRenderer;
import xaeroplus.feature.render.DrawHelper;

@Mixin(value = RadarRenderer.class, remap = false)
public abstract class MixinRadarRenderer {

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

    // Inject at RETURN so renderIcon has already applied scale - we are now in post-scale space
    @Inject(method = "renderIcon", at = @At("RETURN"), remap = false)
    private void drawIconOverlay(
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

        // At RETURN, scale has been applied by renderIcon
        // Icon is -31 to +31 in post-scale space, draw slightly larger border
        float half = 34f;

        Matrix4f matrix = matrixStack.last().pose();
        VertexConsumer buf = minimapBufferSource.getBuffer(CustomRenderTypes.RADAR_NAME_BGS);
        DrawHelper.fillIntoExistingBuffer(matrix, buf, -half, -half, half, half, r, g, b, a);
    }
}
