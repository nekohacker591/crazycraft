package com.example.unfairminecraft;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.monster.AbstractSkeleton;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = UnfairMinecraftMod.MOD_ID)
public final class SkeletonChaosManager {
    private static final int RAPID_FIRE_TICKS = 3;
    private static final int SPECIAL_ATTACK_COOLDOWN = 20 * 8;
    private static final Map<UUID, Integer> RAPID_FIRE = new HashMap<>();
    private static final Map<UUID, Integer> SPECIAL_COOLDOWNS = new HashMap<>();

    private SkeletonChaosManager() {
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.level.isClientSide() || (((ServerLevel) event.level).getGameTime() & 1L) != 0L) {
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
        LivingEntity currentTarget = skeleton.getTarget();
        LivingEntity target = currentTarget != null && currentTarget.isAlive()
            ? currentTarget
            : level.getEntitiesOfClass(LivingEntity.class, skeleton.getBoundingBox().inflate(26.0D), entity -> entity != skeleton && entity.isAlive() && (entity instanceof Mob || entity instanceof net.minecraft.world.entity.player.Player))
                .stream()
                .min((left, right) -> Double.compare(left.distanceToSqr(skeleton), right.distanceToSqr(skeleton)))
                .orElse(null);
        if (target == null || !target.isAlive()) {
            return;
        }

        UUID id = skeleton.getUUID();
        RAPID_FIRE.put(id, Math.max(0, RAPID_FIRE.getOrDefault(id, 0) - 1));
        SPECIAL_COOLDOWNS.put(id, Math.max(0, SPECIAL_COOLDOWNS.getOrDefault(id, 0) - 1));

        skeleton.lookAt(target, 30.0F, 30.0F);
        skeleton.getNavigation().moveTo(target, 1.15D);

        if (RAPID_FIRE.get(id) == 0) {
            fireArrow(level, skeleton, target, 0.0D);
            RAPID_FIRE.put(id, RAPID_FIRE_TICKS);
        }

        if (SPECIAL_COOLDOWNS.get(id) == 0 && skeleton.tickCount > 40) {
            fireDuplicateBurst(level, skeleton, target);
            SPECIAL_COOLDOWNS.put(id, SPECIAL_ATTACK_COOLDOWN);
        }
    }

    private static void fireDuplicateBurst(ServerLevel level, AbstractSkeleton skeleton, LivingEntity target) {
        level.sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE, skeleton.getX(), skeleton.getY() + 1.2D, skeleton.getZ(), 12, 0.2D, 0.2D, 0.2D, 0.01D);
        level.playSound(null, skeleton.blockPosition(), SoundEvents.SKELETON_SHOOT, SoundSource.HOSTILE, 1.2F, 0.6F);
        fireArrow(level, skeleton, target, 0.0D);
        fireArrow(level, skeleton, target, 0.18D);
        fireArrow(level, skeleton, target, -0.18D);
    }

    private static void fireArrow(ServerLevel level, AbstractSkeleton skeleton, LivingEntity target, double spread) {
        Arrow arrow = EntityType.ARROW.create(level);
        if (arrow == null) {
            return;
        }

        Vec3 start = skeleton.position().add(0.0D, 1.45D, 0.0D);
        Vec3 aim = target.getEyePosition().subtract(start).normalize();
        Vec3 lateral = new Vec3(-aim.z, 0.0D, aim.x).normalize().scale(spread);
        Vec3 finalAim = aim.add(lateral).normalize();
        arrow.moveTo(start.x, start.y, start.z, skeleton.getYRot(), skeleton.getXRot());
        arrow.setOwner(skeleton);
        arrow.shoot(finalAim.x, finalAim.y, finalAim.z, 2.6F, 0.0F);
        level.addFreshEntity(arrow);
        level.playSound(null, skeleton.blockPosition(), SoundEvents.SKELETON_SHOOT, SoundSource.HOSTILE, 0.9F, 1.0F + (float) spread);
    }
}
