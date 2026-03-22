package com.example.unfairminecraft;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = UnfairMinecraftMod.MOD_ID)
public final class BeeAmbushManager {
    private static final int SWARM_SIZE = 20;
    private static final double TRIGGER_RADIUS = 6.0D;
    private static final int COOLDOWN_TICKS = 20 * 15;
    private static final int ABDUCT_TICKS = 20 * 4;
    private static final int RELEASE_TICK = 20 * 3;
    private static final int BURST_TICKS = 8;
    private static final int ENGULF_TICKS = 34;
    private static final double PLAYER_LIFT_HEIGHT = 26.0D;
    private static final double BEE_SPEED = 2.45D;
    private static final double RETURN_SPEED = 1.5D;
    private static final int RETURN_TIMEOUT_TICKS = 20 * 10;
    private static final Map<HiveKey, Integer> HIVE_COOLDOWNS = new HashMap<>();
    private static final Map<UUID, SwarmState> ACTIVE_SWARMS = new HashMap<>();

    private BeeAmbushManager() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide() || !(event.player instanceof ServerPlayer player)) {
            return;
        }

        ServerLevel level = player.serverLevel();
        BlockPos playerPos = player.blockPosition();
        int radius = Mth.ceil(TRIGGER_RADIUS);

        for (BlockPos pos : BlockPos.betweenClosed(playerPos.offset(-radius, -2, -radius), playerPos.offset(radius, 2, radius))) {
            if (!(level.getBlockState(pos).getBlock() instanceof BeehiveBlock)) {
                continue;
            }

            HiveKey key = HiveKey.of(level, pos);
            if (HIVE_COOLDOWNS.getOrDefault(key, 0) > 0 || hasActiveSwarmAt(key)) {
                continue;
            }

            Vec3 hiveCenter = Vec3.atCenterOf(pos);
            if (hiveCenter.distanceTo(player.position()) > TRIGGER_RADIUS) {
                continue;
            }

            triggerHive(level, pos.immutable(), player);
            return;
        }
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.level.isClientSide()) {
            return;
        }

        ServerLevel level = (ServerLevel) event.level;
        tickCooldowns(level);
        tickSwarms(level);
    }

    private static void triggerHive(ServerLevel level, BlockPos hivePos, ServerPlayer target) {
        UUID swarmId = UUID.randomUUID();
        HiveKey hiveKey = HiveKey.of(level, hivePos);
        List<UUID> beeIds = new ArrayList<>();
        Vec3 hiveCenter = Vec3.atCenterOf(hivePos);

        level.playSound(null, hivePos, SoundEvents.BEEHIVE_EXIT, SoundSource.HOSTILE, 2.0F, 0.6F + level.random.nextFloat() * 0.15F);
        level.playSound(null, hivePos, SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 0.65F, 1.8F);
        level.sendParticles(ParticleTypes.POOF, hiveCenter.x, hiveCenter.y, hiveCenter.z, 26, 0.35D, 0.35D, 0.35D, 0.14D);
        level.sendParticles(ParticleTypes.SMOKE, hiveCenter.x, hiveCenter.y, hiveCenter.z, 32, 0.4D, 0.4D, 0.4D, 0.04D);
        level.sendParticles(ParticleTypes.CRIT, hiveCenter.x, hiveCenter.y, hiveCenter.z, 45, 0.55D, 0.55D, 0.55D, 0.18D);

        for (int i = 0; i < SWARM_SIZE; i++) {
            Bee bee = EntityType.BEE.create(level);
            if (bee == null) {
                continue;
            }

            Vec3 spawnPos = hiveCenter.add((level.random.nextDouble() - 0.5D) * 0.35D, 0.15D + (level.random.nextDouble() * 0.35D), (level.random.nextDouble() - 0.5D) * 0.35D);
            Vec3 outward = spawnPos.subtract(hiveCenter).normalize().scale(0.7D + level.random.nextDouble() * 0.5D);
            bee.moveTo(spawnPos.x, spawnPos.y, spawnPos.z, level.random.nextFloat() * 360.0F, 0.0F);
            bee.setDeltaMovement(outward);
            bee.setPersistenceRequired();
            bee.setNoGravity(true);
            bee.setInvulnerable(true);
            bee.setRemainingPersistentAngerTime(RETURN_TIMEOUT_TICKS);
            bee.setTarget(target);
            tagBee(bee.getPersistentData(), swarmId, hivePos);
            level.addFreshEntity(bee);
            beeIds.add(bee.getUUID());
        }

        ACTIVE_SWARMS.put(swarmId, new SwarmState(swarmId, hiveKey, target.getUUID(), beeIds, target.getY()));
        HIVE_COOLDOWNS.put(hiveKey, COOLDOWN_TICKS);
    }

    private static void tickCooldowns(ServerLevel level) {
        Iterator<Map.Entry<HiveKey, Integer>> iterator = HIVE_COOLDOWNS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<HiveKey, Integer> entry = iterator.next();
            if (!entry.getKey().dimension().equals(level.dimension().location().toString())) {
                continue;
            }

            int next = entry.getValue() - 1;
            if (next <= 0) {
                iterator.remove();
            } else {
                entry.setValue(next);
            }
        }
    }

    private static void tickSwarms(ServerLevel level) {
        Iterator<SwarmState> iterator = ACTIVE_SWARMS.values().iterator();
        while (iterator.hasNext()) {
            SwarmState swarm = iterator.next();
            if (!swarm.hiveKey.dimension().equals(level.dimension().location().toString())) {
                continue;
            }

            swarm.age++;
            ServerPlayer target = level.getServer().getPlayerList().getPlayer(swarm.targetId());
            List<Bee> bees = resolveBees(level, swarm);

            if (bees.isEmpty()) {
                iterator.remove();
                continue;
            }

            if (!swarm.returning()) {
                if (target == null || !target.isAlive()) {
                    swarm.setReturning(true);
                } else {
                    runAttackPhase(level, swarm, target, bees);
                }
            }

            if (swarm.returning()) {
                runReturnPhase(level, swarm, bees);
                if (swarm.age > RETURN_TIMEOUT_TICKS || bees.isEmpty()) {
                    for (Bee bee : bees) {
                        bee.discard();
                    }
                    iterator.remove();
                }
            }
        }
    }

    private static void runAttackPhase(ServerLevel level, SwarmState swarm, ServerPlayer target, List<Bee> bees) {
        Vec3 playerPos = target.position();
        Vec3 bodyCenter = playerPos.add(0.0D, target.getBbHeight() * 0.58D, 0.0D);
        double climbProgress = Math.min(1.0D, Math.max(0.0D, (swarm.age - ENGULF_TICKS) / (double) Math.max(1, RELEASE_TICK - ENGULF_TICKS)));
        Vec3 liftAnchor = bodyCenter.add(0.0D, 0.6D + (PLAYER_LIFT_HEIGHT * climbProgress), 0.0D);

        for (int i = 0; i < bees.size(); i++) {
            Bee bee = bees.get(i);
            Vec3 targetPos;

            if (swarm.age <= BURST_TICKS) {
                targetPos = bodyCenter.add(burstOffset(i, bees.size(), swarm.age));
            } else if (swarm.age <= ENGULF_TICKS) {
                targetPos = bodyCenter.add(engulfOffset(level, i, bees.size(), swarm.age, false));
            } else {
                targetPos = liftAnchor.add(engulfOffset(level, i, bees.size(), swarm.age, true));
            }

            driveBee(bee, targetPos, BEE_SPEED);
            bee.setTarget(target);
        }

        if (swarm.age == BURST_TICKS) {
            level.playSound(null, target.blockPosition(), SoundEvents.BEEHIVE_SHEAR, SoundSource.HOSTILE, 1.6F, 0.7F);
        }

        if (swarm.age <= ENGULF_TICKS) {
            target.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 8, 0, false, false, false));
            if (swarm.age % 6 == 0) {
                level.sendParticles(ParticleTypes.CRIT, bodyCenter.x, bodyCenter.y, bodyCenter.z, 14, 0.4D, 0.6D, 0.4D, 0.04D);
                level.playSound(null, target.blockPosition(), SoundEvents.BEE_LOOP_AGGRESSIVE, SoundSource.HOSTILE, 1.4F, 0.8F + level.random.nextFloat() * 0.2F);
            }

            Vec3 slapstick = new Vec3(
                (level.random.nextDouble() - 0.5D) * 0.9D,
                0.18D + (level.random.nextDouble() * 0.18D),
                (level.random.nextDouble() - 0.5D) * 0.9D
            );
            target.setDeltaMovement(slapstick);
        } else {
            Vec3 desired = new Vec3(
                playerPos.x + ((level.random.nextDouble() - 0.5D) * 0.35D),
                swarm.startY() + 8.0D + (PLAYER_LIFT_HEIGHT * climbProgress),
                playerPos.z + ((level.random.nextDouble() - 0.5D) * 0.35D)
            );
            Vec3 yank = desired.subtract(playerPos).scale(0.30D).add(
                (level.random.nextDouble() - 0.5D) * 0.12D,
                0.42D,
                (level.random.nextDouble() - 0.5D) * 0.12D
            );
            target.setDeltaMovement(yank);
        }

        target.hurtMarked = true;
        target.fallDistance = 0.0F;

        if (swarm.age >= RELEASE_TICK) {
            level.playSound(null, target.blockPosition(), SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.HOSTILE, 1.2F, 0.5F);
            target.setDeltaMovement(target.getDeltaMovement().x * 0.35D, -1.55D, target.getDeltaMovement().z * 0.35D);
            target.hurtMarked = true;
            swarm.setReturning(true);
        }

        if (swarm.age >= ABDUCT_TICKS) {
            swarm.setReturning(true);
        }
    }

    private static void runReturnPhase(ServerLevel level, SwarmState swarm, List<Bee> bees) {
        BlockPos hivePos = swarm.hiveKey.pos();
        Vec3 hiveCenter = Vec3.atCenterOf(hivePos).add(0.0D, 0.15D, 0.0D);
        int arrived = 0;

        for (Bee bee : bees) {
            driveBee(bee, hiveCenter, RETURN_SPEED);
            bee.setTarget(null);
            if (bee.position().distanceTo(hiveCenter) < 1.1D) {
                arrived++;
                bee.discard();
            }
        }

        if (arrived > 0) {
            level.sendParticles(ParticleTypes.POOF, hiveCenter.x, hiveCenter.y, hiveCenter.z, arrived * 2, 0.2D, 0.2D, 0.2D, 0.02D);
            level.getBlockEntity(hivePos, net.minecraft.world.level.block.entity.BlockEntityType.BEEHIVE)
                .ifPresent(beehive -> beehive.setChanged());
        }
    }

    private static List<Bee> resolveBees(ServerLevel level, SwarmState swarm) {
        List<Bee> bees = new ArrayList<>();
        Iterator<UUID> iterator = swarm.beeIds().iterator();
        while (iterator.hasNext()) {
            UUID beeId = iterator.next();
            Entity entity = level.getEntity(beeId);
            if (entity instanceof Bee bee && bee.isAlive()) {
                bees.add(bee);
            } else {
                iterator.remove();
            }
        }
        bees.sort(Comparator.comparing(Entity::getId));
        return bees;
    }

    private static void driveBee(Bee bee, Vec3 targetPos, double speed) {
        Vec3 current = bee.position();
        Vec3 delta = targetPos.subtract(current);
        if (delta.lengthSqr() < 1.0E-4D) {
            delta = new Vec3(0.0D, 0.08D, 0.0D);
        }
        Vec3 velocity = delta.normalize().scale(speed);
        bee.setDeltaMovement(velocity);
        bee.hurtMarked = true;
        bee.getLookControl().setLookAt(targetPos.x, targetPos.y, targetPos.z);
    }

    private static Vec3 burstOffset(int index, int count, int age) {
        double progress = age / (double) Math.max(1, BURST_TICKS);
        double angle = ((Math.PI * 2.0D) / Math.max(count, 1)) * index + (age * 0.85D);
        double radius = 3.8D - (progress * 2.9D) + ((index % 3) * 0.12D);
        double y = ((index % 5) - 2.0D) * 0.22D;
        return new Vec3(Math.cos(angle) * radius, y, Math.sin(angle) * radius);
    }

    private static Vec3 engulfOffset(ServerLevel level, int index, int count, int age, boolean lifting) {
        double spin = age * (lifting ? 0.42D : 0.78D);
        if (index < (count * 0.65D)) {
            double angle = spin + (index * 1.35D);
            double radius = lifting ? 0.22D + ((index % 4) * 0.07D) : 0.10D + ((index % 3) * 0.06D);
            double y = (Math.sin((age * 0.45D) + index) * 0.34D) + (((index % 5) - 2.0D) * 0.12D);
            return new Vec3(Math.cos(angle) * radius, y, Math.sin(angle) * radius);
        }

        double angle = ((Math.PI * 2.0D) / Math.max(count, 1)) * index + spin;
        double radius = lifting ? 0.95D + ((index % 4) * 0.14D) : 0.65D + ((index % 5) * 0.09D);
        double bob = lifting ? 0.55D : 0.32D;
        double y = (Math.sin((age * 0.32D) + index) * bob) + (((index % 4) - 1.5D) * 0.18D);
        return new Vec3(Math.cos(angle) * radius, y, Math.sin(angle) * radius)
            .add((level.random.nextDouble() - 0.5D) * 0.05D, 0.0D, (level.random.nextDouble() - 0.5D) * 0.05D);
    }

    private static boolean hasActiveSwarmAt(HiveKey key) {
        return ACTIVE_SWARMS.values().stream().anyMatch(swarm -> swarm.hiveKey().equals(key));
    }

    private static void tagBee(CompoundTag tag, UUID swarmId, BlockPos hivePos) {
        tag.putUUID("UnfairSwarmId", swarmId);
        tag.putInt("HiveX", hivePos.getX());
        tag.putInt("HiveY", hivePos.getY());
        tag.putInt("HiveZ", hivePos.getZ());
    }

    private static final class HiveKey {
        private final String dimension;
        private final BlockPos pos;

        private HiveKey(String dimension, BlockPos pos) {
            this.dimension = dimension;
            this.pos = pos;
        }

        private static HiveKey of(Level level, BlockPos pos) {
            return new HiveKey(level.dimension().location().toString(), pos.immutable());
        }

        private String dimension() {
            return dimension;
        }

        private BlockPos pos() {
            return pos;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (!(obj instanceof HiveKey other)) {
                return false;
            }
            return dimension.equals(other.dimension) && pos.equals(other.pos);
        }

        @Override
        public int hashCode() {
            return 31 * dimension.hashCode() + pos.hashCode();
        }
    }

    private static final class SwarmState {
        private final UUID swarmId;
        private final HiveKey hiveKey;
        private final UUID targetId;
        private final List<UUID> beeIds;
        private final double startY;
        private int age;
        private boolean returning;

        private SwarmState(UUID swarmId, HiveKey hiveKey, UUID targetId, List<UUID> beeIds, double startY) {
            this.swarmId = swarmId;
            this.hiveKey = hiveKey;
            this.targetId = targetId;
            this.beeIds = beeIds;
            this.startY = startY;
        }

        private UUID swarmId() {
            return swarmId;
        }

        private HiveKey hiveKey() {
            return hiveKey;
        }

        private UUID targetId() {
            return targetId;
        }

        private List<UUID> beeIds() {
            return beeIds;
        }

        private double startY() {
            return startY;
        }

        private boolean returning() {
            return returning;
        }

        private void setReturning(boolean value) {
            this.returning = value;
        }
    }
}
