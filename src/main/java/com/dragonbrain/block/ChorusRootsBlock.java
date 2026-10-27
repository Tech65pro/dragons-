package com.dragonbrain.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.PlantBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.WorldView;

import com.dragonbrain.registry.ModBlocks;

/** Decorative chorus root growth for the Chorus Wilds. */
public class ChorusRootsBlock extends PlantBlock {
    public static final MapCodec<ChorusRootsBlock> CODEC = createCodec(ChorusRootsBlock::new);
    private static final VoxelShape SHAPE = Block.createCuboidShape(3, 0, 3, 13, 11, 13);

    public ChorusRootsBlock(Settings settings) {
        super(settings);
    }

    @Override
    protected boolean canPlantOnTop(BlockState floor, BlockView world, BlockPos pos) {
        return floor.isOf(Blocks.END_STONE)
                || floor.isOf(ModBlocks.CHORUS_BARK_LOG)
                || floor.isOf(ModBlocks.ENDSTONE_RUBBLE)
                || floor.isOf(ModBlocks.ASTRAL_DUST_BLOCK);
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos,
                                      ShapeContext context) {
        return SHAPE.offset(state.getModelOffset(pos));
    }

    @Override
    protected boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
        BlockPos below = pos.offset(Direction.DOWN);
        return canPlantOnTop(world.getBlockState(below), world, below);
    }
    @Override
    public MapCodec<ChorusRootsBlock> getCodec() {
        return CODEC;
    }
}
