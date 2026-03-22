package com.example.unfairminecraft;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TorchBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@Mod.EventBusSubscriber(modid = UnfairMinecraftMod.MOD_ID)
public final class TorchAmbushManager {
    private static final double TRIGGER_RADIUS = 10.0D;
    private static final int FIRE_INTERVAL_TICKS = 20 * 5;
    private static final Map<TorchKey, Integer> TORCH_COOLDOWNS = new HashMap<>();

    private TorchAmbushManager() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide() || !(event.player instanceof ServerPlayer player)) {
            return;
        }

        ServerLevel level = player.serverLevel();
        BlockPos playerPos = player.blockPosition();
        int radius = Mth.ceil(TRIGGER_RADIUS);

        for (BlockPos pos : BlockPos.betweenClosed(playerPos.offset(-radius, -2, -radius), playerPos.offset(radius, 3, radius))) {
            BlockState state = level.getBlockState(pos);
            if (!isAngryTorch(state)) {
                continue;
            }

            TorchKey key = TorchKey.of(level, pos);
            if (TORCH_COOLDOWNS.getOrDefault(key, 0) > 0) {
                continue;
            }

            Vec3 spawnPos = getTorchMuzzle(state, pos);
            if (spawnPos.distanceTo(player.getEyePosition()) > TRIGGER_RADIUS) {
                continue;
            }

            fireTorch(level, pos.immutable(), state, player);
            TORCH_COOLDOWNS.put(key, FIRE_INTERVAL_TICKS);
        }
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.level.isClientSide()) {
            return;
        }

        ServerLevel level = (ServerLevel) event.level;
        Iterator<Map.Entry<TorchKey, Integer>> iterator = TORCH_COOLDOWNS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<TorchKey, Integer> entry = iterator.next();
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

    private static void fireTorch(ServerLevel level, BlockPos torchPos, BlockState state, ServerPlayer target) {
        Vec3 muzzle = getTorchMuzzle(state, torchPos);
        Vec3 initialDirection = target.getEyePosition().subtract(muzzle).normalize();

        CursedTorchFireballEntity fireball = ModEntities.CURSED_TORCH_FIREBALL.get().create(level);
        if (fireball == null) {
            return;
        }

        fireball.moveTo(muzzle.x, muzzle.y, muzzle.z, 0.0F, 0.0F);
        fireball.setDeltaMovement(initialDirection.scale(0.085D));
        fireball.setTrackedTarget(target);
        level.addFreshEntity(fireball);

        level.playSound(null, torchPos, SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 0.65F, 1.5F + (level.random.nextFloat() * 0.15F));
        level.sendParticles(ParticleTypes.FLAME, muzzle.x, muzzle.y, muzzle.z, 10, 0.05D, 0.05D, 0.05D, 0.02D);
        level.sendParticles(ParticleTypes.SMOKE, muzzle.x, muzzle.y, muzzle.z, 8, 0.04D, 0.04D, 0.04D, 0.01D);
    }

    private static boolean isAngryTorch(BlockState state) {
        Block block = state.getBlock();
        return block == Blocks.TORCH || block == Blocks.WALL_TORCH || block == Blocks.SOUL_TORCH || block == Blocks.SOUL_WALL_TORCH;
    }

    private static Vec3 getTorchMuzzle(BlockState state, BlockPos pos) {
        Vec3 center = Vec3.atCenterOf(pos).add(0.0D, 0.1D, 0.0D);
        if (state.getBlock() instanceof WallTorchBlock && state.hasProperty(WallTorchBlock.FACING)) {
            Direction facing = state.getValue(WallTorchBlock.FACING);
            return center.add(facing.getStepX() * 0.28D, 0.08D, facing.getStepZ() * 0.28D);
        }
        if (state.getBlock() instanceof TorchBlock) {
            return center.add(0.0D, 0.18D, 0.0D);
        }
        return center;
    }

    private record TorchKey(String dimension, BlockPos pos) {
        private static TorchKey of(Level level, BlockPos pos) {
            return new TorchKey(level.dimension().location().toString(), pos.immutable());
        }
    }
}
