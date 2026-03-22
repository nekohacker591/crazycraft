package com.example.unfairminecraft;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BeehiveBlock;
import net.minecraft.world.level.block.entity.BeehiveBlockEntity;
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
    private static final double PLAYER_LIFT_HEIGHT = 26.0D;
    private static final double BEE_SPEED = 1.6D;
    private static final double RETURN_SPEED = 1.2D;
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

        for (int i = 0; i < SWARM_SIZE; i++) {
            Bee bee = EntityType.BEE.create(level);
            if (bee == null) {
                continue;
            }

            Vec3 spawnPos = Vec3.atCenterOf(hivePos).add((level.random.nextDouble() - 0.5D) * 0.7D, 0.2D, (level.random.nextDouble() - 0.5D) * 0.7D);
            bee.moveTo(spawnPos.x, spawnPos.y, spawnPos.z, level.random.nextFloat() * 360.0F, 0.0F);
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
        double climbProgress = Math.min(1.0D, swarm.age / (double) RELEASE_TICK);
        double liftY = playerPos.y + 0.35D + (PLAYER_LIFT_HEIGHT * climbProgress / RELEASE_TICK * 2.0D);
        Vec3 abductAnchor = new Vec3(playerPos.x, Math.max(playerPos.y + 1.2D, liftY), playerPos.z);

        for (int i = 0; i < bees.size(); i++) {
            Bee bee = bees.get(i);
            Vec3 offset = ringOffset(i, bees.size(), swarm.age);
            driveBee(bee, abductAnchor.add(offset), BEE_SPEED);
            bee.setTarget(target);
        }

        Vec3 desired = new Vec3(playerPos.x, swarm.startY() + 6.0D + (PLAYER_LIFT_HEIGHT * climbProgress), playerPos.z);
        Vec3 pull = desired.subtract(playerPos).scale(0.24D).add(0.0D, 0.18D, 0.0D);
        target.setDeltaMovement(pull.x, Math.max(pull.y, 0.35D), pull.z);
        target.hurtMarked = true;
        target.fallDistance = 0.0F;

        if (swarm.age >= RELEASE_TICK) {
            target.setDeltaMovement(target.getDeltaMovement().x, -1.2D, target.getDeltaMovement().z);
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
        Vec3 velocity = targetPos.subtract(current).normalize().scale(speed);
        bee.setDeltaMovement(velocity);
        bee.hurtMarked = true;
        bee.getLookControl().setLookAt(targetPos.x, targetPos.y, targetPos.z);
    }

    private static Vec3 ringOffset(int index, int count, int age) {
        double angle = ((Math.PI * 2.0D) / Math.max(count, 1)) * index + (age * 0.25D);
        double radius = 1.4D + ((index % 5) * 0.18D);
        double y = ((index % 4) - 1.5D) * 0.35D;
        return new Vec3(Math.cos(angle) * radius, y, Math.sin(angle) * radius);
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
