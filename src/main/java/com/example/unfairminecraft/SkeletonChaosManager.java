package com.example.unfairminecraft;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = UnfairMinecraftMod.MOD_ID)
public final class SkeletonChaosManager {
    private static final int RAPID_FIRE_TICKS = 3;
    private static final int SPECIAL_ATTACK_COOLDOWN = 20 * 8;
    private static final int SPECIAL_DURATION = 20;
    private static final Map<UUID, Integer> RAPID_FIRE = new HashMap<>();
    private static final Map<UUID, Integer> SPECIAL_COOLDOWNS = new HashMap<>();
    private static final Map<UUID, Integer> SPECIAL_PHASES = new HashMap<>();

    private SkeletonChaosManager() {
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.level.isClientSide()) {
            return;
        }

        ServerLevel level = (ServerLevel) event.level;
        java.util.Set<AbstractSkeleton> skeletons = new java.util.HashSet<>();
        for (ServerPlayer player : level.players()) {
            skeletons.addAll(level.getEntitiesOfClass(AbstractSkeleton.class, player.getBoundingBox().inflate(32.0D)));
        }
        for (AbstractSkeleton skeleton : skeletons) {
            if (skeleton instanceof CreeperShooterSkeletonEntity) {
                continue;
            }
            tickSkeleton(level, skeleton);
        }
    }

    private static void tickSkeleton(ServerLevel level, AbstractSkeleton skeleton) {
        net.minecraft.world.entity.player.Player nearest = level.getNearestPlayer(skeleton, 26.0D);
        if (!(nearest instanceof ServerPlayer target) || !target.isAlive()) {
            return;
        }

        UUID id = skeleton.getUUID();
        RAPID_FIRE.put(id, Math.max(0, RAPID_FIRE.getOrDefault(id, 0) - 1));
        SPECIAL_COOLDOWNS.put(id, Math.max(0, SPECIAL_COOLDOWNS.getOrDefault(id, 0) - 1));

        if (SPECIAL_PHASES.containsKey(id)) {
            runSpecial(level, skeleton, target, id);
            return;
        }

        skeleton.lookAt(target, 30.0F, 30.0F);
        skeleton.getNavigation().moveTo(target, 1.15D);

        if (RAPID_FIRE.get(id) == 0) {
            fireArrow(level, skeleton, target, false);
            RAPID_FIRE.put(id, RAPID_FIRE_TICKS);
        }

        if (SPECIAL_COOLDOWNS.get(id) == 0 && skeleton.tickCount > 40) {
            SPECIAL_PHASES.put(id, 0);
            SPECIAL_COOLDOWNS.put(id, SPECIAL_ATTACK_COOLDOWN);
            skeleton.setNoGravity(true);
        }
    }

    private static void runSpecial(ServerLevel level, AbstractSkeleton skeleton, ServerPlayer target, UUID id) {
        int phase = SPECIAL_PHASES.getOrDefault(id, 0) + 1;
        if (phase >= SPECIAL_DURATION) {
            skeleton.setNoGravity(false);
            skeleton.setDeltaMovement(0.0D, -1.15D, 0.0D);
            level.explode(null, target.getX(), target.getY(), target.getZ(), 2.4F, Level.ExplosionInteraction.NONE);
            SPECIAL_PHASES.remove(id);
            return;
        }

        double angle = phase * 0.85D;
        double radius = 3.2D + (phase * 0.12D);
        Vec3 pos = target.position().add(Math.cos(angle) * radius, 5.0D + (Math.sin(phase * 0.3D) * 1.5D), Math.sin(angle) * radius);
        skeleton.setPos(pos.x, pos.y, pos.z);
        skeleton.lookAt(target, 180.0F, 180.0F);
        level.playSound(null, skeleton.blockPosition(), SoundEvents.PHANTOM_FLAP, SoundSource.HOSTILE, 0.45F, 1.5F);

        if (phase % 4 == 0) {
            fireArrow(level, skeleton, target, true);
        }

        SPECIAL_PHASES.put(id, phase);
    }

    private static void fireArrow(ServerLevel level, AbstractSkeleton skeleton, ServerPlayer target, boolean rainingDown) {
        Arrow arrow = EntityType.ARROW.create(level);
        if (arrow == null) {
            return;
        }

        Vec3 start = skeleton.position().add(0.0D, rainingDown ? 0.4D : 1.45D, 0.0D);
        Vec3 aim = target.getEyePosition().subtract(start).normalize();
        double speed = rainingDown ? 1.8D : 2.25D;
        arrow.moveTo(start.x, start.y, start.z, skeleton.getYRot(), skeleton.getXRot());
        arrow.setOwner(skeleton);
        arrow.setDeltaMovement(aim.scale(speed));
        level.addFreshEntity(arrow);
        level.playSound(null, skeleton.blockPosition(), SoundEvents.SKELETON_SHOOT, SoundSource.HOSTILE, 0.9F, rainingDown ? 0.65F : 1.0F);
    }
}
