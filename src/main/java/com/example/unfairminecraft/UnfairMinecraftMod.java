package com.example.unfairminecraft;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(UnfairMinecraftMod.MOD_ID)
public class UnfairMinecraftMod {
    public static final String MOD_ID = "unfairminecraft";
    public static final Logger LOGGER = LogUtils.getLogger();

    public UnfairMinecraftMod() {
        ModEntities.ENTITY_TYPES.register(FMLJavaModLoadingContext.get().getModEventBus());
    }
}
