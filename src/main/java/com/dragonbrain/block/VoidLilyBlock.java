package com.dragonbrain.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.PlantBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.WorldView;
import net.minecraft.world.World;

import com.dragonbrain.config.ConfigManager;
import com.dragonbrain.registry.ModBlocks;

/** Luminous Void Gardens plant. Grows on End Stone and Lumen Moss. */
public class VoidLilyBlock extends PlantBlock {
    private static final VoxelShape SHAPE = Block.createCuboidShape(4, 0, 4, 12, 13, 12);

    public VoidLilyBlock(Settings settings) {
        super(settings);
    }

    @Override
    protected boolean canPlantOnTop(BlockState floor, BlockView world, BlockPos pos) {
        return floor.isOf(Blocks.END_STONE)
                || floor.isOf(ModBlocks.LUMEN_MOSS_BLOCK)
                || floor.isOf(ModBlocks.FRACTURED_END_STONE)
                || floor.isOf(ModBlocks.ASTRAL_DUST_BLOCK);
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos,
                                      ShapeContext context) {
        return SHAPE.offset(state.getModelOffset(pos));
    }

    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        if (!ConfigManager.get().ambientParticles || random.nextInt(6) != 0) {
            return;
        }
        // Restrained glow: one slow mote occasionally, never a particle storm.
        world.addParticleClient(ParticleTypes.END_ROD,
                pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.3,
                pos.getY() + 0.7,
                pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.3,
                0.0, 0.02, 0.0);
    }

    @Override
    protected boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
        BlockPos below = pos.offset(Direction.DOWN);
        return canPlantOnTop(world.getBlockState(below), world, below);
    }
}
