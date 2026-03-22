package com.example.unfairminecraft;

import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = UnfairMinecraftMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModEntityAttributes {
    private ModEntityAttributes() {
    }

    @SubscribeEvent
    public static void onEntityAttributeCreation(EntityAttributeCreationEvent event) {
        AttributeSupplier.Builder creeperProjectile = Creeper.createAttributes();
        AttributeSupplier.Builder creeperShooter = Skeleton.createAttributes();

        event.put(ModEntities.CREEPER_PROJECTILE.get(), creeperProjectile.build());
        event.put(ModEntities.SKELETON_CREEPER_SHOOTER.get(), creeperShooter.build());
    }
}
