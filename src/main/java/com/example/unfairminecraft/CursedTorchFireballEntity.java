package com.example.unfairminecraft;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

public class CursedTorchFireballEntity extends SmallFireball {
    private static final double PROJECTILE_SPEED = 0.085D;
    private static final int MAX_LIFETIME_TICKS = 20 * 6;
    private UUID targetId;

    public CursedTorchFireballEntity(EntityType<? extends CursedTorchFireballEntity> entityType, Level level) {
        super(entityType, level);
        this.setNoGravity(true);
    }

    public void setTrackedTarget(ServerPlayer player) {
        this.targetId = player.getUUID();
    }

    @Override
    public void tick() {
        if (!this.level().isClientSide()) {
            if (this.tickCount >= MAX_LIFETIME_TICKS) {
                this.discard();
                return;
            }

            Vec3 desiredDirection = this.getDeltaMovement().lengthSqr() > 1.0E-4D
                ? this.getDeltaMovement().normalize()
                : new Vec3(0.0D, 0.0D, 1.0D);

            if (this.targetId != null && this.level() instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                ServerPlayer target = serverLevel.getServer().getPlayerList().getPlayer(this.targetId);
                if (target != null && target.isAlive()) {
                    desiredDirection = target.getEyePosition().subtract(this.position()).normalize();
                }
            }

            Vec3 currentVelocity = this.getDeltaMovement();
            Vec3 blendedVelocity = currentVelocity.scale(0.78D).add(desiredDirection.scale(0.24D));
            if (blendedVelocity.lengthSqr() < 1.0E-4D) {
                blendedVelocity = desiredDirection;
            }

            this.setDeltaMovement(blendedVelocity.normalize().scale(PROJECTILE_SPEED));
            this.hurtMarked = true;
        }

        super.tick();

        this.level().addParticle(ParticleTypes.SMALL_FLAME, this.getX(), this.getY(), this.getZ(), 0.0D, 0.0D, 0.0D);
        this.level().addParticle(ParticleTypes.SMOKE, this.getX(), this.getY(), this.getZ(), 0.0D, 0.0D, 0.0D);
    }

    @Override
    protected float getInertia() {
        return 1.0F;
    }

    @Override
    public boolean isNoGravity() {
        return true;
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (this.targetId != null) {
            tag.putUUID("TrackedTarget", this.targetId);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.hasUUID("TrackedTarget")) {
            this.targetId = tag.getUUID("TrackedTarget");
        }
    }
}
