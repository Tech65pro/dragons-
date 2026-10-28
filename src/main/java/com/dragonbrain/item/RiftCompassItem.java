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
import com.dragonbrain.worldgen.ModWorldgen;

/**
 * Rift Compass. What it does, precisely:
 * <ul>
 *   <li>Only works in the End; 5-second cooldown, one ping per use.</li>
 *   <li>Primary target: the nearest DragonBrain structure (Ancient End
 *       Ruins, Abandoned Observatory, Resonance Vault, Sentinel Sanctuary),
 *       found with the vanilla structure locator
 *       {@link ServerWorld#locateStructure} — this inspects structure
 *       starts only, never force-loads full terrain, and is the same
 *       mechanism {@code /locate} uses.</li>
 *   <li>Fallback: the nearest End Gateway / End Portal Frame found by a
 *       bounded sampled scan (4-block spacing within 128 blocks, only on
 *       use).</li>
 *   <li>Feedback always says what was found (or that nothing was in
 *       range), with distance and compass direction.</li>
 * </ul>
 */
public class RiftCompassItem extends Item {
    private static final int GATEWAY_SCAN_RADIUS = 128;
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
            user.getItemCooldownManager().set(stack, 100);
            serverWorld.playSound(null, user.getBlockPos(), ModSounds.RIFT_COMPASS_PING,
                    SoundCategory.PLAYERS, 0.6f, 1.0f);
            serverWorld.spawnParticles(ModParticles.VOID_MOTE,
                    user.getX(), user.getY() + 1.2, user.getZ(), 8, 0.4, 0.3, 0.4, 0.02);

            BlockPos origin = user.getBlockPos();
            // Primary: nearest DragonBrain structure via the vanilla locator.
            BlockPos found = serverWorld.locateStructure(
                    ModWorldgen.RIFT_COMPASS_TARGETS, origin, 100, false);
            String kindKey = "text.dragonbrain.compass.structure";
            if (found == null) {
                found = scanForGateway(serverWorld, origin);
                kindKey = "text.dragonbrain.compass.found";
            }
            if (found == null) {
                user.sendMessage(Text.translatable("text.dragonbrain.compass.none"), true);
            } else {
                int dist = (int) Math.round(Math.sqrt(origin.getSquaredDistance(found)));
                String dir = directionName(found.getX() - origin.getX(), found.getZ() - origin.getZ());
                user.sendMessage(Text.translatable(kindKey, dist, dir), true);
                serverWorld.playSound(null, user.getBlockPos(), ModSounds.DISCOVERY_CHIME,
                        SoundCategory.PLAYERS, 0.5f, 1.1f);
            }
        }
        return ActionResult.SUCCESS;
    }

    private static BlockPos scanForGateway(ServerWorld world, BlockPos origin) {
        BlockPos best = null;
        double bestSq = Double.MAX_VALUE;
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        for (int dx = -GATEWAY_SCAN_RADIUS; dx <= GATEWAY_SCAN_RADIUS; dx += STEP) {
            for (int dz = -GATEWAY_SCAN_RADIUS; dz <= GATEWAY_SCAN_RADIUS; dz += STEP) {
                for (int dy = -Y_RANGE; dy <= Y_RANGE; dy += STEP) {
                    cursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    var state = world.getBlockState(cursor);
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
        return best;
    }

    private static String directionName(int dx, int dz) {
        // Minecraft: -Z = north, +X = east
        double deg = Math.toDegrees(Math.atan2(dz, dx));
        String[] names = {"east", "south-east", "south", "south-west",
                          "west", "north-west", "north", "north-east"};
        int idx = (int) Math.floor(((deg + 180.0 + 22.5) % 360.0) / 45.0);
        return names[idx];
    }
}
