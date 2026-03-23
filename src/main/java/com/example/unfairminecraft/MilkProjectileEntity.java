package com.example.unfairminecraft;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

public class MilkProjectileEntity extends Snowball {
    public MilkProjectileEntity(EntityType<? extends MilkProjectileEntity> entityType, Level level) {
        super(entityType, level);
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        Entity hit = result.getEntity();
        if (hit instanceof LivingEntity living) {
            living.push(this.getDeltaMovement().x * 0.35D, 0.12D, this.getDeltaMovement().z * 0.35D);
            living.hurtMarked = true;
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        serverLevel.sendParticles(ParticleTypes.SPLASH, this.getX(), this.getY(), this.getZ(), 18, 0.15D, 0.15D, 0.15D, 0.2D);
        serverLevel.playSound(null, this.blockPosition(), SoundEvents.GENERIC_SPLASH, SoundSource.HOSTILE, 1.0F, 0.8F);
        this.discard();
    }
}
