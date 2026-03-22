package com.example.unfairminecraft;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.CreeperRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;

public class CreeperProjectileRenderer extends CreeperRenderer {
    public CreeperProjectileRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(net.minecraft.world.entity.monster.Creeper entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        poseStack.mulPose(Axis.ZP.rotationDegrees(90.0F));
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
        poseStack.popPose();
    }
}
