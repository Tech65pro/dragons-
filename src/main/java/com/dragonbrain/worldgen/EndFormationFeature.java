package com.dragonbrain.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.util.StringIdentifiable;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.feature.Feature;
import net.minecraft.world.gen.feature.FeatureConfig;
import net.minecraft.world.gen.feature.util.FeatureContext;

/**
 * One configurable End formation feature used by the custom biomes:
 * tapered pillars/spires, spikes, and branching chorus-style trees.
 * Placement always requires real island ground below the origin, so
 * formations never float over the void or create void traps.
 */
public class EndFormationFeature extends Feature<EndFormationFeature.Config> {

    public enum Style implements StringIdentifiable {
        PILLAR("pillar"),
        SPIKE("spike"),
        TREE("tree");

        private final String name;

        Style(String name) {
            this.name = name;
        }

        @Override
        public String asString() {
            return this.name;
        }
    }

    public record Config(Block primary, Block secondary, Style style,
                         int minHeight, int maxHeight) implements FeatureConfig {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(instance ->
                instance.group(
                        Registries.BLOCK.getCodec().fieldOf("primary").forGetter(Config::primary),
                        Registries.BLOCK.getCodec().fieldOf("secondary").forGetter(Config::secondary),
                        StringIdentifiable.createCodec(Style::values).fieldOf("style").forGetter(Config::style),
                        Codec.intRange(1, 48).fieldOf("min_height").forGetter(Config::minHeight),
                        Codec.intRange(1, 64).fieldOf("max_height").forGetter(Config::maxHeight)
                ).apply(instance, Config::new));
    }

    public EndFormationFeature() {
        super(Config.CODEC);
    }

    @Override
    public boolean generate(FeatureContext<Config> context) {
        StructureWorldAccess world = context.getWorld();
        BlockPos origin = context.getOrigin();
        Config config = context.getConfig();
        Random random = context.getRandom();

        int topY = world.getTopY(Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
                origin.getX(), origin.getZ());
        if (topY <= world.getBottomY() + 2) {
            return false; // open void: no island here
        }
        BlockPos base = new BlockPos(origin.getX(), topY, origin.getZ());
        BlockState ground = world.getBlockState(base.down());
        if (ground.isAir() || !ground.getFluidState().isEmpty()) {
            return false;
        }
        int height = config.minHeight()
                + random.nextInt(Math.max(1, config.maxHeight() - config.minHeight() + 1));
        if (base.getY() + height >= world.getTopYInclusive() - 1) {
            return false;
        }
        return switch (config.style()) {
            case PILLAR -> generatePillar(world, base, config, height, random, false);
            case SPIKE -> generatePillar(world, base, config, height, random, true);
            case TREE -> generateTree(world, base, config, height, random);
        };
    }

    private boolean generatePillar(StructureWorldAccess world, BlockPos base, Config config,
                                   int height, Random random, boolean spiky) {
        BlockPos.Mutable cursor = base.mutableCopy();
        int leanX = 0;
        int leanZ = 0;
        for (int i = 0; i < height; i++) {
            if (random.nextInt(6) == 0) {
                leanX += random.nextInt(3) - 1;
            }
            if (random.nextInt(6) == 0) {
                leanZ += random.nextInt(3) - 1;
            }
            leanX = Math.max(-2, Math.min(2, leanX));
            leanZ = Math.max(-2, Math.min(2, leanZ));
            cursor.set(base.getX() + leanX, base.getY() + i, base.getZ() + leanZ);
            place(world, cursor, config.primary().getDefaultState());
            if (i < height / 3) { // sturdy base
                place(world, cursor.east(), config.primary().getDefaultState());
                place(world, cursor.south(), config.primary().getDefaultState());
                cursor.set(base.getX() + leanX, base.getY() + i, base.getZ() + leanZ);
            }
            if (spiky && i > height * 2 / 3) {
                place(world, cursor.up(), config.secondary().getDefaultState());
                cursor.set(base.getX() + leanX, base.getY() + i, base.getZ() + leanZ);
            }
        }
        cursor.set(base.getX() + leanX, base.getY() + height, base.getZ() + leanZ);
        place(world, cursor, config.secondary().getDefaultState());
        return true;
    }

    private boolean generateTree(StructureWorldAccess world, BlockPos base, Config config,
                                 int height, Random random) {
        BlockState trunk = config.primary().getDefaultState();
        BlockState canopy = config.secondary().getDefaultState();
        BlockPos.Mutable cursor = base.mutableCopy();
        for (int i = 0; i < height; i++) {
            cursor.set(base).move(Direction.UP, i);
            place(world, cursor, trunk);
        }
        BlockPos top = base.up(height - 1);
        canopyBlob(world, top.up(), canopy, 2, random);
        int branches = 2 + random.nextInt(3);
        Direction[] horizontals = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
        for (int b = 0; b < branches; b++) {
            Direction dir = horizontals[random.nextInt(horizontals.length)];
            BlockPos start = base.up(height - 2 - random.nextInt(Math.max(1, height / 3)));
            BlockPos end = start;
            int length = 2 + random.nextInt(3);
            for (int s = 0; s < length; s++) {
                end = end.offset(dir).up(s >= length - 1 ? 1 : 0);
                place(world, end, trunk);
            }
            canopyBlob(world, end.up(), canopy, 1, random);
        }
        return true;
    }

    private void canopyBlob(StructureWorldAccess world, BlockPos center, BlockState canopy,
                            int radius, Random random) {
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.abs(dx) == radius && Math.abs(dz) == radius) {
                        continue; // keep the blob rounded
                    }
                    if (radius > 1 && Math.abs(dy) == 1 && random.nextInt(3) == 0) {
                        continue;
                    }
                    cursor.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    if (world.isAir(cursor)) {
                        place(world, cursor, canopy);
                    }
                }
            }
        }
    }

    private void place(StructureWorldAccess world, BlockPos pos, BlockState state) {
        world.setBlockState(pos, state, Block.NOTIFY_LISTENERS);
    }
}
