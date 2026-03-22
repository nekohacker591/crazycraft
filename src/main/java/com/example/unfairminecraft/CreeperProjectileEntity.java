package com.example.unfairminecraft;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.MoverType;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class CreeperProjectileEntity extends Creeper {
    private static final double PROJECTILE_SPEED = 0.72D;
    private static final int MAX_LIFETIME_TICKS = 50;

    public CreeperProjectileEntity(EntityType<? extends CreeperProjectileEntity> entityType, Level level) {
        super(entityType, level);
        this.setNoAi(true);
        this.setNoGravity(true);
    }

    public void shoot(Vec3 direction) {
        this.setDeltaMovement(direction.normalize().scale(PROJECTILE_SPEED));
        this.setXRot(90.0F);
    }

    @Override
    public void tick() {
        this.setNoAi(true);
        this.setNoGravity(true);
        this.baseTick();

        Vec3 velocity = this.getDeltaMovement();
        if (!this.level().isClientSide()) {
            this.move(MoverType.SELF, velocity);
        }

        float yaw = (float) (Mth.atan2(velocity.z, velocity.x) * (180.0D / Math.PI)) - 90.0F;
        this.setYRot(yaw);
        this.yRotO = yaw;
        this.yHeadRot = yaw;
        this.yBodyRot = yaw;
        this.setXRot(90.0F);

        if (this.level().isClientSide()) {
            this.level().addParticle(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + 0.25D, this.getZ(), 0.0D, 0.0D, 0.0D);
            return;
        }

        if (this.tickCount >= MAX_LIFETIME_TICKS || this.horizontalCollision || this.verticalCollision || this.onGround()) {
            impact();
        }
    }

    private void impact() {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            this.discard();
            return;
        }

        if (serverLevel.random.nextDouble() < 0.8D) {
            Creeper creeper = EntityType.CREEPER.create(serverLevel);
            if (creeper != null) {
                creeper.moveTo(this.getX(), this.getY(), this.getZ(), this.getYRot(), 0.0F);
                serverLevel.addFreshEntity(creeper);
            }
        } else {
            serverLevel.explode(null, this.getX(), this.getY(), this.getZ(), 3.2F, Level.ExplosionInteraction.MOB);
        }

        this.discard();
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, BlockState state, net.minecraft.core.BlockPos pos) {
    }
}
