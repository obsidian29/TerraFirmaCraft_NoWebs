/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */

package net.dries007.tfc.world.noise;

import java.util.function.ToDoubleFunction;
import it.unimi.dsi.fastutil.HashCommon;

/**
 * Modified from {@link FastNoiseLite#SingleCellular(int, float, float)}
 */
public class Cellular2D implements Noise2D
{
    public static final float JITTER = 0.43701595f;

    private final int seed;
    private float frequency;

    public Cellular2D(long seed)
    {
        this.seed = HashCommon.long2int(seed);
        this.frequency = 1;
    }

    @Override
    public float noise(float x, float y)
    {
        return cell(x, y).noise();
    }

    @Override
    public Cellular2D spread(float scaleFactor)
    {
        frequency *= scaleFactor;
        return this;
    }

    public Noise2D then(ToDoubleFunction<Cell> f)
    {
        return (x, y) -> (float) f.applyAsDouble(cell(x, y));
    }

    public Cell cell(float x, float y)
    {
        x *= frequency;
        y *= frequency;

        final int primeX = 501125321;
        final int primeY = 1136930381;

        int xr = FastNoiseLite.FastFloor(x);
        int yr = FastNoiseLite.FastFloor(y);

        float distance0 = Float.MAX_VALUE;
        float distance1 = Float.MAX_VALUE;
        float closestCenterX = 0;
        float closestCenterY = 0;
        int closestHash = 0;
        int closestCellX = 0;
        int closestCellY = 0;

        int xPrimed = (xr - 1) * primeX;
        int yPrimedBase = (yr - 1) * primeY;

        for (int xi = xr - 1; xi <= xr + 1; xi++)
        {
            int yPrimed = yPrimedBase;

            for (int yi = yr - 1; yi <= yr + 1; yi++)
            {
                int hash = FastNoiseLite.Hash(seed, xPrimed, yPrimed);
                int idx = hash & (255 << 1);

                float vecX = xi + FastNoiseLite.RandVecs2D[idx] * JITTER;
                float vecY = yi + FastNoiseLite.RandVecs2D[idx | 1] * JITTER;

                float newDistance = (vecX - x) * (vecX - x) + (vecY - y) * (vecY - y);

                distance1 = FastNoiseLite.FastMax(FastNoiseLite.FastMin(distance1, newDistance), distance0);
                if (newDistance < distance0)
                {
                    distance0 = newDistance;
                    closestHash = hash;

                    // Store the last computed centers
                    closestCenterX = vecX;
                    closestCenterY = vecY;
                    closestCellX = xi;
                    closestCellY = yi;
                }
                yPrimed += primeY;
            }
            xPrimed += primeX;
        }

        return new Cell(closestCenterX / frequency, closestCenterY / frequency, closestCellX, closestCellY, distance0, distance1, closestHash * (1 / 2147483648.0f));
    }

    public record Cell(float x, float y, int cx, int cy, float f1, float f2, float noise) {}
}
