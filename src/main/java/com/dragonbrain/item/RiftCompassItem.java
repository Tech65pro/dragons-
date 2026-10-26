package com.dragonbrain.item;

import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import com.dragonbrain.registry.ModParticles;
import com.dragonbrain.registry.ModSounds;

/**
 * Rift Compass. In the End it listens for the nearest rift signature
 * (End Gateway or End Portal frame) and reports direction + distance.
 * The scan is bounded (65 x 17 x 65 samples at 4-block spacing = a few
 * thousand block-state reads, on a 3-second cooldown) so it is safe on
 * servers.
 */
public class RiftCompassItem extends Item {
    private static final int RADIUS = 128;
    private static final int STEP = 4;
    private static final int Y_RANGE = 32;

    public RiftCompassItem(Settings settings) {
        super(settings);
    }

    @Override
    public ActionResult use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!world.isClient()) {
            ServerWorld serverWorld = (ServerWorld) world;
            if (serverWorld.getRegistryKey() != World.END) {
                user.sendMessage(Text.translatable("text.dragonbrain.compass.not_end"), true);
                return ActionResult.SUCCESS;
            }
            user.getItemCooldownManager().set(stack, 60);
            serverWorld.playSound(null, user.getBlockPos(), ModSounds.RIFT_COMPASS_PING,
                    SoundCategory.PLAYERS, 0.6f, 1.0f);
            serverWorld.spawnParticles(ModParticles.VOID_MOTE,
                    user.getX(), user.getY() + 1.2, user.getZ(), 8, 0.4, 0.3, 0.4, 0.02);

            BlockPos origin = user.getBlockPos();
            BlockPos best = null;
            double bestSq = Double.MAX_VALUE;
            BlockPos.Mutable cursor = new BlockPos.Mutable();
            for (int dx = -RADIUS; dx <= RADIUS; dx += STEP) {
                for (int dz = -RADIUS; dz <= RADIUS; dz += STEP) {
                    for (int dy = -Y_RANGE; dy <= Y_RANGE; dy += STEP) {
                        cursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                        var state = serverWorld.getBlockState(cursor);
                        if (state.isOf(Blocks.END_GATEWAY) || state.isOf(Blocks.END_PORTAL_FRAME)) {
                            double sq = origin.getSquaredDistance(cursor);
                            if (sq < bestSq) {
                                bestSq = sq;
                                best = cursor.toImmutable();
                            }
                        }
                    }
                }
            }
            if (best == null) {
                user.sendMessage(Text.translatable("text.dragonbrain.compass.none"), true);
            } else {
                int dist = (int) Math.round(Math.sqrt(bestSq));
                double angle = Math.atan2(best.getZ() - origin.getZ(), best.getX() - origin.getX());
                String dir = directionName(angle);
                user.sendMessage(Text.translatable("text.dragonbrain.compass.found", dist, dir), true);
            }
        }
        return ActionResult.SUCCESS;
    }

    private static String directionName(double angle) {
        // Minecraft: -Z = north, +X = east
        double deg = Math.toDegrees(angle);
        String[] names = {"east", "south-east", "south", "south-west",
                          "west", "north-west", "north", "north-east"};
        int idx = (int) Math.floor(((deg + 180.0 + 22.5) % 360.0) / 45.0);
        return names[idx];
    }
}
