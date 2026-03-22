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
            .sized(0.3125F, 0.3125F)
            .clientTrackingRange(6)
            .updateInterval(1)
            .fireImmune()
            .build(UnfairMinecraftMod.MOD_ID + ":cursed_torch_fireball")
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
        }
    }
}
