package com.example.unfairminecraft;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

public class CursedTorchFireballRenderer extends EntityRenderer<CursedTorchFireballEntity> {
    private static final ResourceLocation TEXTURE = ResourceLocation.parse("textures/particle/flame.png");

    public CursedTorchFireballRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(CursedTorchFireballEntity entity, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        // Intentionally rendered through particles emitted by the entity itself.
    }

    @Override
    public ResourceLocation getTextureLocation(CursedTorchFireballEntity entity) {
        return TEXTURE;
    }
}
