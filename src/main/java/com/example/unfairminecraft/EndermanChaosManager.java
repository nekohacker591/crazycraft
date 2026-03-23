package com.example.unfairminecraft;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = UnfairMinecraftMod.MOD_ID)
public final class EndermanChaosManager {
    private static final Map<UUID, Integer> ENDERMAN_COOLDOWN = new HashMap<>();

    private EndermanChaosManager() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide() || !(event.player instanceof ServerPlayer player) || player.tickCount % 10 != 0) {
            return;
        }

        ServerLevel level = player.serverLevel();
        for (EnderMan enderman : level.getEntitiesOfClass(EnderMan.class, player.getBoundingBox().inflate(20.0D))) {
            if (!enderman.isAngryAt(player)) {
                continue;
            }

            int cooldown = ENDERMAN_COOLDOWN.getOrDefault(enderman.getUUID(), 0);
            if (cooldown > 0) {
                ENDERMAN_COOLDOWN.put(enderman.getUUID(), cooldown - 10);
                continue;
            }

            teleportBehind(enderman, player);
            if (enderman.distanceTo(player) < 2.5F) {
                decapitate(level, enderman, player);
            }
            ENDERMAN_COOLDOWN.put(enderman.getUUID(), 60);
        }
    }

    private static void teleportBehind(EnderMan enderman, ServerPlayer player) {
        Vec3 behind = player.position().subtract(player.getLookAngle().normalize().scale(1.5D));
        enderman.teleportTo(behind.x, behind.y, behind.z);
        enderman.setTarget(player);
        enderman.level().playSound(null, enderman.blockPosition(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.HOSTILE, 1.4F, 0.9F);
    }

    private static void decapitate(ServerLevel level, EnderMan enderman, ServerPlayer player) {
        ItemStack head = new ItemStack(Items.PLAYER_HEAD);
        head.getOrCreateTag().putString("SkullOwner", player.getGameProfile().getName());
        enderman.setItemSlot(EquipmentSlot.MAINHAND, head);
        enderman.setDropChance(EquipmentSlot.MAINHAND, 1.0F);
        level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_SMALL_FALL, SoundSource.HOSTILE, 1.2F, 0.5F);
        player.hurt(ModDamageTypes.source(level, ModDamageTypes.ENDERMAN_DECAPITATION, enderman), 1000.0F);
    }
}
