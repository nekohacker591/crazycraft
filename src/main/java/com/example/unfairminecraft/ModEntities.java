package com.example.unfairminecraft;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEntities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, UnfairMinecraftMod.MOD_ID);

    public static final RegistryObject<EntityType<CursedTorchFireballEntity>> CURSED_TORCH_FIREBALL = ENTITY_TYPES.register(
        "cursed_torch_fireball",
        () -> EntityType.Builder.<CursedTorchFireballEntity>of(CursedTorchFireballEntity::new, MobCategory.MISC)
            .sized(0.9F, 0.9F)
            .clientTrackingRange(6)
            .updateInterval(1)
            .fireImmune()
            .build(UnfairMinecraftMod.MOD_ID + ":cursed_torch_fireball")
    );


    public static final RegistryObject<EntityType<CreeperProjectileEntity>> CREEPER_PROJECTILE = ENTITY_TYPES.register(
        "creeper_projectile",
        () -> EntityType.Builder.<CreeperProjectileEntity>of(CreeperProjectileEntity::new, MobCategory.MONSTER)
            .sized(0.6F, 1.7F)
            .clientTrackingRange(8)
            .updateInterval(1)
            .build(UnfairMinecraftMod.MOD_ID + ":creeper_projectile")
    );

    public static final RegistryObject<EntityType<CreeperShooterSkeletonEntity>> SKELETON_CREEPER_SHOOTER = ENTITY_TYPES.register(
        "skeleton_creeper_shooter",
        () -> EntityType.Builder.<CreeperShooterSkeletonEntity>of(CreeperShooterSkeletonEntity::new, MobCategory.MONSTER)
            .sized(0.6F, 1.99F)
            .clientTrackingRange(8)
            .updateInterval(2)
            .build(UnfairMinecraftMod.MOD_ID + ":skeleton_creeper_shooter")
    );

    private ModEntities() {
    }

    @Mod.EventBusSubscriber(modid = UnfairMinecraftMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ClientEvents {
        private ClientEvents() {
        }

        @SubscribeEvent
        public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(CURSED_TORCH_FIREBALL.get(), CursedTorchFireballRenderer::new);
            event.registerEntityRenderer(CREEPER_PROJECTILE.get(), CreeperProjectileRenderer::new);
            event.registerEntityRenderer(SKELETON_CREEPER_SHOOTER.get(), net.minecraft.client.renderer.entity.SkeletonRenderer::new);
        }
    }
}
