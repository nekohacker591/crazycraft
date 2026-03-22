package com.example.unfairminecraft;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.ambient.Bat;
import net.minecraft.world.entity.monster.Blaze;
import net.minecraft.world.entity.monster.Ghast;
import net.minecraft.world.entity.monster.Spider;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.SmallFireball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Iterator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = UnfairMinecraftMod.MOD_ID)
public final class MobChaosManager {
    private static final Map<UUID, Integer> CHICKEN_FUSES = new HashMap<>();
    private static final Map<UUID, Integer> VILLAGER_INSULTS = new HashMap<>();
    private static final Map<UUID, Integer> BLAZE_COOLDOWNS = new HashMap<>();
    private static final Map<UUID, Integer> GHAST_ATTACKS = new HashMap<>();
    private static final String ZOMBIE_GEAR_TAG = "UnfairZombieGear";

    private MobChaosManager() {
    }

    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide()) {
            return;
        }

        Entity entity = event.getEntity();
        if (entity instanceof Zombie zombie) {
            armZombie(zombie);
        } else if (entity instanceof Spider spider) {
            spider.setCustomName(Component.literal("Spider Man"));
            spider.setCustomNameVisible(true);
        } else if (entity instanceof Bat bat) {
            bat.setCustomName(Component.literal("Batman"));
            bat.setCustomNameVisible(true);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide() || !(event.player instanceof ServerPlayer player)) {
            return;
        }

        if (player.tickCount % 5 != 0) {
            return;
        }

        ServerLevel level = player.serverLevel();
        if (player.isInWater()) {
            player.setDeltaMovement(player.getDeltaMovement().x * 0.4D, -0.24D, player.getDeltaMovement().z * 0.4D);
            player.setAirSupply(Math.max(0, player.getAirSupply() - 15));
            player.hurtMarked = true;
        }

        triggerNearbyChickens(level, player);
        triggerVillagers(level, player);
        triggerSpiders(level, player);
        triggerCows(level, player);
        triggerBats(level, player);
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.level.isClientSide() || (((ServerLevel) event.level).getGameTime() % 3L) != 0L) {
            return;
        }

        ServerLevel level = (ServerLevel) event.level;
        tickChickenFuses(level);
        tickCombatMobs(level);
    }

    private static void triggerNearbyChickens(ServerLevel level, ServerPlayer player) {
        for (Chicken chicken : level.getEntitiesOfClass(Chicken.class, player.getBoundingBox().inflate(6.0D))) {
            CHICKEN_FUSES.putIfAbsent(chicken.getUUID(), 20);
            chicken.setYRot(chicken.getYRot() + 70.0F);
            chicken.yBodyRot = chicken.getYRot();
            chicken.yHeadRot = chicken.getYRot();
            level.playSound(null, chicken.blockPosition(), SoundEvents.CHICKEN_HURT, SoundSource.HOSTILE, 1.2F, 0.6F + level.random.nextFloat() * 0.3F);
            level.playSound(null, chicken.blockPosition(), SoundEvents.CHICKEN_DEATH, SoundSource.HOSTILE, 0.9F, 1.6F);
        }
    }

    private static void triggerVillagers(ServerLevel level, ServerPlayer player) {
        String[] insults = {
            "nice armor, did a skeleton dress you?",
            "i've seen dirt blocks with better survival odds",
            "you fight like an unplugged redstone lamp"
        };
        for (Villager villager : level.getEntitiesOfClass(Villager.class, player.getBoundingBox().inflate(10.0D))) {
            int cooldown = VILLAGER_INSULTS.getOrDefault(villager.getUUID(), 0);
            if (cooldown > 0) {
                VILLAGER_INSULTS.put(villager.getUUID(), cooldown - 1);
                continue;
            }
            String insult = insults[level.random.nextInt(insults.length)];
            player.sendSystemMessage(Component.literal("<Villager> " + insult));
            player.hurt(level.damageSources().magic(), 1.0F);
            VILLAGER_INSULTS.put(villager.getUUID(), 10);
        }
    }

    private static void triggerSpiders(ServerLevel level, ServerPlayer player) {
        for (Spider spider : level.getEntitiesOfClass(Spider.class, player.getBoundingBox().inflate(14.0D))) {
            if (spider.tickCount % 30 != 0) {
                continue;
            }
            BlockPos webPos = player.blockPosition();
            if (level.getBlockState(webPos).canBeReplaced()) {
                level.setBlockAndUpdate(webPos, Blocks.COBWEB.defaultBlockState());
            }
            level.playSound(null, spider.blockPosition(), SoundEvents.SPIDER_AMBIENT, SoundSource.HOSTILE, 1.0F, 0.7F);
        }
    }

    private static void triggerCows(ServerLevel level, ServerPlayer player) {
        for (Cow cow : level.getEntitiesOfClass(Cow.class, player.getBoundingBox().inflate(12.0D))) {
            if (cow.tickCount % 35 != 0) {
                continue;
            }
            Vec3 start = cow.position().add(0.0D, 1.2D, 0.0D);
            Vec3 delta = player.getEyePosition().subtract(start).normalize().scale(0.85D);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.SPLASH, start.x, start.y, start.z, 24, 0.1D, 0.1D, 0.1D, 0.15D);
            player.push(delta.x * 0.18D, 0.05D, delta.z * 0.18D);
            player.swing(InteractionHand.MAIN_HAND, true);
            level.playSound(null, cow.blockPosition(), SoundEvents.GENERIC_SPLASH, SoundSource.HOSTILE, 0.9F, 0.8F);
        }
    }

    private static void triggerBats(ServerLevel level, ServerPlayer player) {
        for (Bat bat : level.getEntitiesOfClass(Bat.class, player.getBoundingBox().inflate(8.0D))) {
            if (bat.tickCount % 40 != 0) {
                continue;
            }
            player.sendSystemMessage(Component.literal("<Batman> i'm bat man"));
            bat.setTarget(player);
        }
    }

    private static void tickChickenFuses(ServerLevel level) {
        Iterator<Map.Entry<UUID, Integer>> iterator = CHICKEN_FUSES.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Integer> entry = iterator.next();
            Entity entity = level.getEntity(entry.getKey());
            if (!(entity instanceof Chicken chicken) || !chicken.isAlive()) {
                iterator.remove();
                continue;
            }

            int next = entry.getValue() - 3;
            if (next <= 0) {
                level.explode(chicken, chicken.getX(), chicken.getY(), chicken.getZ(), 3.0F, Level.ExplosionInteraction.MOB);
                chicken.discard();
                iterator.remove();
            } else {
                chicken.setYRot(chicken.getYRot() + 90.0F);
                entry.setValue(next);
            }
        }
    }

    private static void tickCombatMobs(ServerLevel level) {
        Set<Entity> nearby = new HashSet<>();
        for (ServerPlayer player : level.players()) {
            nearby.addAll(level.getEntities(player, player.getBoundingBox().inflate(40.0D), entity -> entity instanceof Blaze || entity instanceof Ghast));
        }

        for (Entity entity : nearby) {
            if (entity instanceof Blaze blaze) {
                tickBlaze(level, blaze);
            } else if (entity instanceof Ghast ghast) {
                tickGhast(level, ghast);
            }
        }
    }

    private static void tickBlaze(ServerLevel level, Blaze blaze) {
        net.minecraft.world.entity.player.Player nearest = level.getNearestPlayer(blaze, 24.0D);
        if (!(nearest instanceof ServerPlayer target) || !target.isAlive()) {
            return;
        }

        int cooldown = BLAZE_COOLDOWNS.getOrDefault(blaze.getUUID(), 0);
        if (cooldown > 0) {
            BLAZE_COOLDOWNS.put(blaze.getUUID(), cooldown - 1);
            return;
        }

        SmallFireball fireball = EntityType.SMALL_FIREBALL.create(level);
        if (fireball != null) {
            Vec3 start = blaze.position().add(0.0D, 1.2D, 0.0D);
            Vec3 dir = target.getEyePosition().subtract(start).normalize();
            fireball.moveTo(start.x, start.y, start.z, blaze.getYRot(), blaze.getXRot());
            fireball.setDeltaMovement(dir.scale(0.9D));
            level.addFreshEntity(fireball);
            level.playSound(null, blaze.blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.HOSTILE, 1.0F, 1.3F);
        }
        BLAZE_COOLDOWNS.put(blaze.getUUID(), 3);
    }

    private static void tickGhast(ServerLevel level, Ghast ghast) {
        net.minecraft.world.entity.player.Player nearest = level.getNearestPlayer(ghast, 48.0D);
        if (!(nearest instanceof ServerPlayer target) || !target.isAlive()) {
            return;
        }

        int phase = GHAST_ATTACKS.getOrDefault(ghast.getUUID(), 0);
        if (phase <= 0) {
            Vec3 toPlayer = target.position().subtract(ghast.position()).normalize();
            Vec3 strafe = new Vec3(-toPlayer.z, 0.0D, toPlayer.x).scale(level.random.nextBoolean() ? 0.75D : -0.75D);
            ghast.setDeltaMovement(strafe.x, 0.05D, strafe.z);
            ghast.hurtMarked = true;
            GHAST_ATTACKS.put(ghast.getUUID(), 20);
            return;
        }

        if (phase == 10) {
            SmallFireball fireball = EntityType.SMALL_FIREBALL.create(level);
            if (fireball != null) {
                Vec3 start = ghast.position().add(0.0D, 2.2D, 0.0D);
                Vec3 dir = target.getEyePosition().subtract(start).normalize();
                fireball.moveTo(start.x, start.y, start.z, ghast.getYRot(), ghast.getXRot());
                fireball.setDeltaMovement(dir.scale(1.05D));
                level.addFreshEntity(fireball);
                level.playSound(null, ghast.blockPosition(), SoundEvents.GHAST_SHOOT, SoundSource.HOSTILE, 1.5F, 1.0F);
            }
        }

        GHAST_ATTACKS.put(ghast.getUUID(), phase - 1);
    }

    private static void armZombie(Zombie zombie) {
        if (zombie.getPersistentData().getBoolean(ZOMBIE_GEAR_TAG)) {
            return;
        }

        zombie.setItemSlot(EquipmentSlot.HEAD, enchanted(Items.NETHERITE_HELMET));
        zombie.setItemSlot(EquipmentSlot.CHEST, enchanted(Items.NETHERITE_CHESTPLATE));
        zombie.setItemSlot(EquipmentSlot.LEGS, enchanted(Items.NETHERITE_LEGGINGS));
        zombie.setItemSlot(EquipmentSlot.FEET, enchanted(Items.NETHERITE_BOOTS));
        zombie.setItemSlot(EquipmentSlot.MAINHAND, enchanted(Items.NETHERITE_SWORD));
        zombie.getPersistentData().putBoolean(ZOMBIE_GEAR_TAG, true);
    }

    private static ItemStack enchanted(net.minecraft.world.item.Item item) {
        ItemStack stack = new ItemStack(item);
        Map<Enchantment, Integer> enchantments = new HashMap<>();
        for (Enchantment enchantment : ForgeRegistries.ENCHANTMENTS.getValues()) {
            if (enchantment.canEnchant(stack)) {
                enchantments.put(enchantment, enchantment.getMaxLevel());
            }
        }
        EnchantmentHelper.setEnchantments(enchantments, stack);
        return stack;
    }
}
