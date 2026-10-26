package com.dragonbrain.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

import com.dragonbrain.config.ConfigManager;
import com.dragonbrain.registry.ModParticles;
import com.dragonbrain.registry.ModSounds;

/**
 * Eclipse Lantern: a bright End light source with a restrained void-mote
 * shimmer. Right-click plays its resonance hum and emits a small spark
 * burst (also used as the "discovery" feedback at ruins/vaults).
 */
public class EclipseLanternBlock extends Block {
    private static final VoxelShape SHAPE = VoxelShapes.union(
            Block.createCuboidShape(5, 0, 5, 11, 11, 11),
            Block.createCuboidShape(7, 11, 7, 9, 13, 9));

    public EclipseLanternBlock(Settings settings) {
        super(settings);
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos,
                                      ShapeContext context) {
        return SHAPE;
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockView world, BlockPos pos,
                                        ShapeContext context) {
        return SHAPE;
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos,
                                 PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient()) {
            ServerWorld serverWorld = (ServerWorld) world;
            if (ConfigManager.get().customSounds) {
                serverWorld.playSound(null, pos, ModSounds.ECLIPSE_LANTERN_HUM,
                        SoundCategory.BLOCKS, 0.5f, 0.95f + world.getRandom().nextFloat() * 0.1f);
            }
            serverWorld.spawnParticles(ModParticles.RESONANCE_SPARK,
                    pos.getX() + 0.5, pos.getY() + 0.55, pos.getZ() + 0.5,
                    6, 0.25, 0.25, 0.25, 0.02);
        }
        return ActionResult.SUCCESS;
    }

    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        if (!ConfigManager.get().ambientParticles || random.nextInt(9) != 0) {
            return;
        }
        world.addParticleClient(ModParticles.VOID_MOTE,
                pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.6,
                pos.getY() + 0.4 + random.nextDouble() * 0.4,
                pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.6,
                0.0, 0.015, 0.0);
    }
}
