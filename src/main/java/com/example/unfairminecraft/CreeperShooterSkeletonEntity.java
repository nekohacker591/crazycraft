package com.example.unfairminecraft;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class CreeperShooterSkeletonEntity extends Skeleton {
    private static final int SHOOT_COOLDOWN_TICKS = 28;
    private int shootCooldown;

    public CreeperShooterSkeletonEntity(EntityType<? extends CreeperShooterSkeletonEntity> entityType, Level level) {
        super(entityType, level);
        this.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
    }

    @Override
    public void tick() {
        this.setNoAi(true);
        this.baseTick();
        if (this.level().isClientSide() || !(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }

        if (this.shootCooldown > 0) {
            this.shootCooldown--;
        }

        net.minecraft.world.entity.player.Player nearest = serverLevel.getNearestPlayer(this, 24.0D);
        if (!(nearest instanceof ServerPlayer target) || !target.isAlive()) {
            return;
        }

        this.lookAt(target, 30.0F, 30.0F);
        net.minecraft.world.phys.Vec3 drift = target.position().subtract(this.position()).normalize().scale(0.08D);
        this.move(net.minecraft.world.entity.MoverType.SELF, drift);
        this.setDeltaMovement(drift);

        if (this.shootCooldown <= 0) {
            CreeperProjectileEntity projectile = ModEntities.CREEPER_PROJECTILE.get().create(serverLevel);
            if (projectile != null) {
                Vec3 launchPos = this.position().add(0.0D, 1.35D, 0.0D);
                Vec3 direction = target.getEyePosition().subtract(launchPos).normalize();
                projectile.moveTo(launchPos.x, launchPos.y, launchPos.z, this.getYRot(), 90.0F);
                projectile.shoot(direction);
                serverLevel.addFreshEntity(projectile);
                serverLevel.playSound(null, this.blockPosition(), SoundEvents.SKELETON_SHOOT, SoundSource.HOSTILE, 1.0F, 0.75F);
                this.shootCooldown = SHOOT_COOLDOWN_TICKS;
            }
        }
    }
}
