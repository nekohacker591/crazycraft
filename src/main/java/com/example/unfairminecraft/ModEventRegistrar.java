package com.example.unfairminecraft;

import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

public final class ModEventRegistrar {
    private ModEventRegistrar() {
    }

    public static void register() {
        ModEntities.ENTITY_TYPES.register(FMLJavaModLoadingContext.get().getModEventBus());
    }
}
