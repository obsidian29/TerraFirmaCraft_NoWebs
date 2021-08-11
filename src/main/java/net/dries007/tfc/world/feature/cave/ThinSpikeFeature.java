/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */

package net.dries007.tfc.world.feature.cave;

import java.util.Random;

import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.feature.Feature;

import com.mojang.serialization.Codec;
import net.dries007.tfc.common.blocks.ThinSpikeBlock;

public class ThinSpikeFeature extends Feature<ThinSpikeConfig>
{
    public ThinSpikeFeature(Codec<ThinSpikeConfig> codec)
    {
        super(codec);
    }

    @Override
    public boolean place(WorldGenLevel world, ChunkGenerator generator, Random rand, BlockPos pos, ThinSpikeConfig config)
    {
        final BlockState spike = config.getState();
        final BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        boolean placedAny = false;

        for (int attempt = 0; attempt < config.getTries(); attempt++)
        {
            mutablePos.setWithOffset(pos, rand.nextInt(config.getRadius()) - rand.nextInt(config.getRadius()), rand.nextInt(config.getRadius() - rand.nextInt(config.getRadius())), rand.nextInt(config.getRadius()) - rand.nextInt(config.getRadius()));
            // Move upwards to find a suitable spot
            for (int i = 0; i < 7; i++)
            {
                mutablePos.move(0, 1, 0);
                if (!world.isEmptyBlock(mutablePos))
                {
                    mutablePos.move(0, -1, 0);
                    break;
                }
            }
            if (spike.canSurvive(world, mutablePos) && world.isEmptyBlock(mutablePos))
            {
                placeSpike(world, mutablePos, spike, rand, config);
                placedAny = true;
            }
        }
        return placedAny;
    }

    private void placeSpike(WorldGenLevel world, BlockPos.MutableBlockPos mutablePos, BlockState spike, Random rand, ThinSpikeConfig config)
    {
        final int height = config.getHeight(rand);
        for (int i = 0; i < height; i++)
        {
            setBlock(world, mutablePos, spike);
            mutablePos.move(0, -1, 0);
            if (!world.isEmptyBlock(mutablePos))
            {
                // Make the previous state the tip, and exit
                setBlock(world, mutablePos.move(0, 1, 0), spike.setValue(ThinSpikeBlock.TIP, true));
                return;
            }
        }
        // Add the tip
        setBlock(world, mutablePos, spike.setValue(ThinSpikeBlock.TIP, true));
    }
}
