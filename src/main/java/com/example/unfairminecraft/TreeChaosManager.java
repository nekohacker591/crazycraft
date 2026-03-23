package com.example.unfairminecraft;

import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = UnfairMinecraftMod.MOD_ID)
public final class TreeChaosManager {
    private TreeChaosManager() {
    }

    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (event.getLevel().isClientSide()) {
            return;
        }

        ItemStack stack = event.getEntity().getMainHandItem();
        if (!stack.isEmpty()) {
            return;
        }

        if (event.getLevel().getBlockState(event.getPos()).is(BlockTags.LOGS)) {
            event.getEntity().hurt(event.getLevel().damageSources().generic(), 2.0F);
        }
    }
}
