package com.example.unfairminecraft;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Mod.EventBusSubscriber(modid = UnfairMinecraftMod.MOD_ID)
public final class CreeperChaosManager {
    private static final String MODE_TAG = "UnfairCreeperMode";
    private static final String SHOOTER_TAG = "shooter";
    private static final String RUSH_TAG = "rush";
    private static final String TNT_TAG = "tnt";

    private CreeperChaosManager() {
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof Creeper creeper) || creeper instanceof CreeperProjectileEntity) {
            return;
        }

        CompoundTag data = creeper.getPersistentData();
        if (!data.contains(MODE_TAG)) {
            double roll = creeper.level().random.nextDouble();
            if (roll < 0.34D) {
                data.putString(MODE_TAG, SHOOTER_TAG);
            } else if (roll < 0.67D) {
                data.putString(MODE_TAG, RUSH_TAG);
            } else {
                data.putString(MODE_TAG, TNT_TAG);
            }
        }
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.level.isClientSide()) {
            return;
        }

        ServerLevel level = (ServerLevel) event.level;
        java.util.Set<Creeper> creepers = new java.util.HashSet<>();
        for (ServerPlayer player : level.players()) {
            creepers.addAll(level.getEntitiesOfClass(Creeper.class, player.getBoundingBox().inflate(32.0D), creeper -> !(creeper instanceof CreeperProjectileEntity)));
        }
        for (Creeper creeper : creepers) {
            runMode(level, creeper);
        }
    }

    private static void runMode(ServerLevel level, Creeper creeper) {
        net.minecraft.world.entity.player.Player nearest = level.getNearestPlayer(creeper, 20.0D);
        if (!(nearest instanceof ServerPlayer target) || !target.isAlive()) {
            return;
        }

        String mode = creeper.getPersistentData().getString(MODE_TAG);
        switch (mode) {
            case SHOOTER_TAG -> handleShooterMode(level, creeper, target);
            case TNT_TAG -> handleTntMode(level, creeper, target);
            default -> handleRushMode(level, creeper, target);
        }
    }

    private static void handleShooterMode(ServerLevel level, Creeper creeper, ServerPlayer target) {
        if (creeper.tickCount < 20 || creeper.distanceTo(target) > 18.0F) {
            return;
        }

        CreeperShooterSkeletonEntity shooter = ModEntities.SKELETON_CREEPER_SHOOTER.get().create(level);
        if (shooter == null) {
            return;
        }

        shooter.moveTo(creeper.getX(), creeper.getY(), creeper.getZ(), creeper.getYRot(), 0.0F);
        shooter.setTarget(target);
        level.addFreshEntity(shooter);
        creeper.discard();
    }

    private static void handleRushMode(ServerLevel level, Creeper creeper, ServerPlayer target) {
        creeper.getNavigation().moveTo(target, 1.55D);
        Vec3 rush = target.position().subtract(creeper.position()).normalize().scale(0.1D);
        creeper.setDeltaMovement(rush.x, Math.max(creeper.getDeltaMovement().y, 0.0D), rush.z);
        if (creeper.distanceTo(target) < 2.2F) {
            level.explode(creeper, creeper.getX(), creeper.getY(), creeper.getZ(), 3.8F, Level.ExplosionInteraction.MOB);
            creeper.discard();
        }
    }

    private static void handleTntMode(ServerLevel level, Creeper creeper, ServerPlayer target) {
        creeper.getNavigation().moveTo(target, 0.9D);
        if (creeper.tickCount % 50 != 0) {
            return;
        }

        PrimedTnt tnt = EntityType.TNT.create(level);
        if (tnt == null) {
            return;
        }

        Vec3 spawnPos = creeper.position().add(0.0D, 1.4D, 0.0D);
        Vec3 lob = target.getEyePosition().subtract(spawnPos).normalize().scale(0.55D).add(0.0D, 0.25D, 0.0D);
        tnt.moveTo(spawnPos.x, spawnPos.y, spawnPos.z, 0.0F, 0.0F);
        tnt.setDeltaMovement(lob);
        tnt.setFuse(30);
        level.addFreshEntity(tnt);
    }
}
