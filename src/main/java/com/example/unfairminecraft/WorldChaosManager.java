package com.example.unfairminecraft;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.animal.Turtle;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.monster.EnderMan;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.projectile.DragonFireball;
import net.minecraft.world.entity.projectile.Fireball;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = UnfairMinecraftMod.MOD_ID)
public final class WorldChaosManager {
    private static final Map<UUID, Integer> SUN_LOOK = new HashMap<>();
    private static final Map<UUID, Integer> MOON_LOOK = new HashMap<>();
    private static final Map<UUID, Integer> ENDERMAN_COOLDOWN = new HashMap<>();
    private static final Map<UUID, Integer> DRAGON_COMMAND = new HashMap<>();

    private WorldChaosManager() {
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }

        if (event.getEntity() instanceof Turtle turtle) {
            turtle.setCustomName(Component.literal("Ninja Turtle"));
            turtle.setCustomNameVisible(true);
        }
    }

    @SubscribeEvent
    public static void onRightClickSheep(PlayerInteractEvent.EntityInteract event) {
        if (event.getLevel().isClientSide() || !(event.getTarget() instanceof Sheep sheep) || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (sheep.getPassengers().isEmpty()) {
            player.startRiding(sheep, true);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onLivingAttack(LivingAttackEvent event) {
        if (event.getEntity() instanceof EnderDragon && event.getSource().is(DamageTypeTags.IS_EXPLOSION)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide() || !(event.player instanceof ServerPlayer player) || player.tickCount % 10 != 0) {
            return;
        }

        ServerLevel level = player.serverLevel();
        handleSolarAndLunar(level, player);
        handleLavaHunters(level, player);
        handleEndermen(level, player);
        handleTurtles(level, player);
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.level.isClientSide() || (((ServerLevel) event.level).getGameTime() % 2L) != 0L) {
            return;
        }

        ServerLevel level = (ServerLevel) event.level;
        for (ServerPlayer player : level.players()) {
            handleMobFreeForAll(level, player);
            handleSheepBedMissiles(level, player);
            handleWithers(level, player);
            handleWardens(level, player);
            handleDragon(level, player);

            for (net.minecraft.world.entity.animal.horse.Horse horse : level.getEntitiesOfClass(net.minecraft.world.entity.animal.horse.Horse.class, player.getBoundingBox().inflate(20.0D))) {
                LivingEntity hostile = level.getEntitiesOfClass(LivingEntity.class, horse.getBoundingBox().inflate(8.0D), e -> e instanceof Monster && e.getVehicle() == null)
                    .stream().findFirst().orElse(null);
                if (hostile != null) {
                    horse.getNavigation().moveTo(hostile, 2.3D);
                    if (horse.distanceTo(hostile) < 1.5F) {
                        hostile.startRiding(horse, true);
                        level.explode(horse, horse.getX(), horse.getY(), horse.getZ(), 2.8F, Level.ExplosionInteraction.MOB);
                        horse.discard();
                    }
                }
            }
        }
    }

    private static void handleMobFreeForAll(ServerLevel level, ServerPlayer player) {
        for (Monster monster : level.getEntitiesOfClass(Monster.class, player.getBoundingBox().inflate(28.0D))) {
            LivingEntity current = monster.getTarget();
            if (current == null || !current.isAlive() || level.random.nextDouble() < 0.35D) {
                LivingEntity nearest = level.getEntitiesOfClass(LivingEntity.class, monster.getBoundingBox().inflate(12.0D), other -> other != monster && other.isAlive())
                    .stream()
                    .filter(other -> other instanceof Mob || other instanceof Player)
                    .min((a, b) -> Double.compare(a.distanceToSqr(monster), b.distanceToSqr(monster)))
                    .orElse(null);
                if (nearest != null) {
                    monster.setTarget(nearest);
                }
            }
        }
    }

    private static void handleSheepBedMissiles(ServerLevel level, ServerPlayer player) {
        for (Sheep sheep : level.getEntitiesOfClass(Sheep.class, player.getBoundingBox().inflate(24.0D), sheep -> sheep.getPassengers().contains(player))) {
            LivingEntity danger = level.getEntitiesOfClass(LivingEntity.class, sheep.getBoundingBox().inflate(24.0D), target -> target != sheep && target != player && (target instanceof Monster || target instanceof Chicken))
                .stream()
                .min((a, b) -> Double.compare(a.distanceToSqr(sheep), b.distanceToSqr(sheep)))
                .orElse(null);
            if (danger == null) {
                continue;
            }
            sheep.getNavigation().moveTo(danger, 2.7D);
            sheep.setDeltaMovement(danger.position().subtract(sheep.position()).normalize().scale(0.65D));
            if (sheep.distanceTo(danger) < 3.0F) {
                player.stopRiding();
                Vec3 launch = danger.position().subtract(player.position()).normalize().scale(1.35D).add(0.0D, 0.8D, 0.0D);
                player.setDeltaMovement(launch);
                player.hurtMarked = true;
            }
        }
    }

    private static void handleLavaHunters(ServerLevel level, ServerPlayer player) {
        BlockPos playerPos = player.blockPosition();
        int radius = 10;
        for (BlockPos pos : BlockPos.betweenClosed(playerPos.offset(-radius, -2, -radius), playerPos.offset(radius, 2, radius))) {
            if (!level.getBlockState(pos).is(Blocks.LAVA)) {
                continue;
            }
            Vec3 diff = Vec3.atCenterOf(playerPos).subtract(Vec3.atCenterOf(pos)).normalize();
            for (int step = 1; step <= 6; step++) {
                BlockPos path = pos.offset((int) Math.round(diff.x * step), 0, (int) Math.round(diff.z * step));
                if (level.getBlockState(path).canBeReplaced()) {
                    level.setBlockAndUpdate(path, Blocks.MAGMA_BLOCK.defaultBlockState());
                }
                for (Direction direction : Direction.Plane.HORIZONTAL) {
                    BlockPos side = path.relative(direction);
                    if (level.getBlockState(side).canBeReplaced()) {
                        level.setBlockAndUpdate(side, Blocks.LAVA.defaultBlockState());
                    }
                }
            }
            return;
        }
    }

    private static void handleSolarAndLunar(ServerLevel level, ServerPlayer player) {
        Vec3 look = player.getLookAngle();
        boolean canSeeSky = level.canSeeSky(player.blockPosition().above());
        if (!canSeeSky || look.y > -0.92D) {
            SUN_LOOK.put(player.getUUID(), 0);
            MOON_LOOK.put(player.getUUID(), 0);
            return;
        }

        if (level.isDay()) {
            int value = SUN_LOOK.getOrDefault(player.getUUID(), 0) + 10;
            if (value >= 40) {
                fireSkyBeam(level, player, true);
                value = 0;
            }
            SUN_LOOK.put(player.getUUID(), value);
        } else {
            int value = MOON_LOOK.getOrDefault(player.getUUID(), 0) + 10;
            if (value >= 40) {
                fireSkyBeam(level, player, false);
                value = 0;
            }
            MOON_LOOK.put(player.getUUID(), value);
        }
    }

    private static void fireSkyBeam(ServerLevel level, ServerPlayer player, boolean solar) {
        Vec3 direction = solar ? player.getLookAngle().normalize() : new Vec3(0.0D, -1.0D, 0.0D);
        Vec3 start = solar ? player.position().add(direction.scale(48.0D)) : player.position().add(0.0D, 48.0D, 0.0D);
        for (int step = 0; step < 16; step++) {
            Vec3 point = start.subtract(direction.scale(step * 3.0D));
            level.explode(null, point.x, point.y, point.z, solar ? 4.5F : 4.0F, Level.ExplosionInteraction.MOB);
        }
        player.sendSystemMessage(Component.literal(solar ? "Solar Beam!" : "Lunar Strike!"));
    }

    private static void handleEndermen(ServerLevel level, ServerPlayer player) {
        for (EnderMan enderman : level.getEntitiesOfClass(EnderMan.class, player.getBoundingBox().inflate(16.0D))) {
            if (level.dimension() == Level.END) {
                int cd = ENDERMAN_COOLDOWN.getOrDefault(enderman.getUUID(), 0);
                if (cd > 0) {
                    ENDERMAN_COOLDOWN.put(enderman.getUUID(), cd - 10);
                    continue;
                }
                teleportBehind(enderman, player);
                ENDERMAN_COOLDOWN.put(enderman.getUUID(), 100);
            } else if (enderman.isAngryAt(player)) {
                teleportBehind(enderman, player);
                player.addItem(new net.minecraft.world.item.ItemStack(Items.PLAYER_HEAD));
                player.hurt(level.damageSources().magic(), 1000.0F);
            }
        }
    }

    private static void teleportBehind(EnderMan enderman, ServerPlayer player) {
        Vec3 behind = player.position().subtract(player.getLookAngle().normalize().scale(1.5D));
        enderman.teleportTo(behind.x, behind.y, behind.z);
        enderman.setTarget(player);
    }

    private static void handleTurtles(ServerLevel level, ServerPlayer player) {
        for (Turtle turtle : level.getEntitiesOfClass(Turtle.class, player.getBoundingBox().inflate(10.0D))) {
            Vec3 spin = player.position().subtract(turtle.position()).normalize().scale(0.45D);
            turtle.setDeltaMovement(spin.x, 0.12D, spin.z);
            turtle.hurtMarked = true;
        }
    }

    private static void handleWithers(ServerLevel level, ServerPlayer player) {
        for (WitherBoss wither : level.getEntitiesOfClass(WitherBoss.class, player.getBoundingBox().inflate(64.0D))) {
            if (wither.tickCount % 2 == 0) {
                fireRandomBossProjectile(level, wither.position().add(0.0D, 2.8D, 0.0D), player.getEyePosition());
            }
            if (wither.tickCount % 40 == 0) {
                BlockPos pos = wither.blockPosition().below(3);
                for (BlockPos breakPos : BlockPos.betweenClosed(pos.offset(-1, -1, -1), pos.offset(1, 3, 1))) {
                    if (!level.isEmptyBlock(breakPos)) {
                        level.destroyBlock(breakPos, true);
                    }
                }
                wither.teleportTo(player.getX(), player.getY() - 4.0D, player.getZ());
                wither.setDeltaMovement(0.0D, 1.6D, 0.0D);
            }
        }
    }

    private static void fireRandomBossProjectile(ServerLevel level, Vec3 start, Vec3 target) {
        double roll = level.random.nextDouble();
        Vec3 dir = target.subtract(start).normalize();
        if (roll < 0.33D) {
            for (int i = 0; i < 10; i++) {
                PrimedTnt tnt = EntityType.TNT.create(level);
                if (tnt != null) {
                    tnt.moveTo(start.x, start.y, start.z, 0.0F, 0.0F);
                    tnt.setDeltaMovement(dir.scale(1.2D).add((level.random.nextDouble() - 0.5D) * 0.6D, 0.35D, (level.random.nextDouble() - 0.5D) * 0.6D));
                    tnt.setFuse(25);
                    level.addFreshEntity(tnt);
                }
            }
        } else if (roll < 0.66D) {
            WitherSkull skull = EntityType.WITHER_SKULL.create(level);
            if (skull != null) {
                skull.moveTo(start.x, start.y, start.z, 0.0F, 0.0F);
                skull.setDeltaMovement(dir.scale(2.0D));
                level.addFreshEntity(skull);
            }
        } else {
            Fireball fireball = EntityType.FIREBALL.create(level);
            if (fireball != null) {
                fireball.moveTo(start.x, start.y, start.z, 0.0F, 0.0F);
                fireball.setDeltaMovement(dir.scale(10.0D));
                level.addFreshEntity(fireball);
            }
        }
    }

    private static void handleWardens(ServerLevel level, ServerPlayer player) {
        for (Warden warden : level.getEntitiesOfClass(Warden.class, player.getBoundingBox().inflate(48.0D))) {
            warden.setTarget(player);
            if (warden.tickCount % 30 == 0) {
                Vec3 start = warden.position().add(0.0D, 2.0D, 0.0D);
                Vec3 dir = player.getEyePosition().subtract(start).normalize();
                for (int step = 2; step <= 24; step += 2) {
                    Vec3 hit = start.add(dir.scale(step));
                    level.explode(null, hit.x, hit.y, hit.z, 2.2F, Level.ExplosionInteraction.NONE);
                }
            }
        }
    }

    private static void handleDragon(ServerLevel level, ServerPlayer player) {
        for (EnderDragon dragon : level.getEntitiesOfClass(EnderDragon.class, player.getBoundingBox().inflate(128.0D))) {
            int cooldown = DRAGON_COMMAND.getOrDefault(dragon.getUUID(), 0);
            if (cooldown > 0) {
                DRAGON_COMMAND.put(dragon.getUUID(), cooldown - 10);
            } else {
                level.playSound(null, dragon.blockPosition(), SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 3.0F, 0.8F);
                for (EnderMan enderman : level.getEntitiesOfClass(EnderMan.class, player.getBoundingBox().inflate(48.0D))) {
                    enderman.setTarget(player);
                }
                DragonFireball fireball = EntityType.DRAGON_FIREBALL.create(level);
                if (fireball != null) {
                    Vec3 start = dragon.position().add(0.0D, 6.0D, 0.0D);
                    Vec3 dir = player.getEyePosition().subtract(start).normalize();
                    fireball.moveTo(start.x, start.y, start.z, 0.0F, 0.0F);
                    fireball.setDeltaMovement(dir.scale(10.0D));
                    level.addFreshEntity(fireball);
                }
                DRAGON_COMMAND.put(dragon.getUUID(), 60);
            }
        }
    }
}
