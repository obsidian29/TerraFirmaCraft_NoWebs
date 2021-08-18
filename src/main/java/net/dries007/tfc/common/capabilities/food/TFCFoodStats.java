/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */

package net.dries007.tfc.common.capabilities.food;

import java.util.Random;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.fmllegacy.network.PacketDistributor;

import net.dries007.tfc.common.capabilities.player.PlayerDataCapability;
import net.dries007.tfc.config.TFCConfig;
import net.dries007.tfc.network.FoodStatsReplacePacket;
import net.dries007.tfc.network.FoodStatsUpdatePacket;
import net.dries007.tfc.network.PacketHandler;
import net.dries007.tfc.util.TFCDamageSources;
import net.dries007.tfc.util.calendar.ICalendar;

/**
 * A note on the reason why TFCFoodStats serializes to an external capability (player data):
 *
 * We don't use the vanilla read/add save data methods.
 * Why? Because we replace the FoodStats instance way after it has been already deserialized in vanilla.
 * - EntityConstructingEvent is too early as PlayerEntity's constructor would overwrite our food data
 * - AttachCapabilitiesEvent fires just after, and is where we attach the player data capability
 * - PlayerEvent.LoadFromFile fires later, and has access to the save data, but is too early as the player's connection is not yet set, so we can't properly sync that change to client.
 * - PlayerLoggedInEvent is where we can reliably update the FoodStats instance on server, and then sync that change to client.
 *
 * Now, at this point, we can actually read directly from our capability, as it has been deserialized much earlier in Entity#load.
 * Reading is a bit different: we read from the capability data early, store the NBT, and then copy it to the food stats on the instantiation of the custom food stats.
 */
public class TFCFoodStats extends net.minecraft.world.food.FoodData
{
    public static final float PASSIVE_HEAL_AMOUNT = 20 * 0.0002f; // On the display: 1 HP / 5 seconds
    public static final float EXHAUSTION_MULTIPLIER = 0.4f; // Multiplier for vanilla sources of exhaustion (we use passive exhaustion to keep hunger decaying even when not sprinting everywhere. That said, vanilla exhaustion should be reduced to compensate
    public static final float PASSIVE_EXHAUSTION = 20f * 4f / (2.5f * ICalendar.TICKS_IN_DAY); // Passive exhaustion will deplete your food bar once every 2.5 days. Food bar holds ~5 "meals", this requires two per day
    public static final float MAX_THIRST = 100f;

    public static void replaceFoodStats(Player player)
    {
        // Only replace the server player's stats if they aren't already
        final net.minecraft.world.food.FoodData foodStats = player.getFoodData();
        if (!(foodStats instanceof TFCFoodStats))
        {
            // Replace, and then read from the cached data on the player capability
            final TFCFoodStats newStats = new TFCFoodStats(player, foodStats);
            // todo: mixin
            player.foodData = newStats;
            player.getCapability(PlayerDataCapability.CAPABILITY).ifPresent(cap -> cap.writeTo(newStats));
        }
        // Send the update regardless so the client can perform the same logic
        if (player instanceof ServerPlayer serverPlayer)
        {
            PacketHandler.send(PacketDistributor.PLAYER.with(() -> serverPlayer), new FoodStatsReplacePacket());
        }
    }

    private final Player sourcePlayer;
    private final net.minecraft.world.food.FoodData delegate; // We keep this here to do normal vanilla tracking (rather than using super). This is also friendlier to other mods if they replace this
    private final NutritionStats nutritionStats; // Separate handler for nutrition, because it's a bit complex
    private long lastDrinkTick;
    private float thirst;
    private int healTimer;

    public TFCFoodStats(Player sourcePlayer, net.minecraft.world.food.FoodData delegate)
    {
        this.sourcePlayer = sourcePlayer;
        this.delegate = delegate;
        this.nutritionStats = new NutritionStats(0.5f, 0.0f);
        this.thirst = MAX_THIRST;
    }

    @Override
    public void eat(int foodLevelIn, float foodSaturationModifier)
    {
        // This should never be called directly - when it is we assume it's direct stat modifications (saturation potion, eating cake)
        // We make modifications to vanilla logic, as saturation needs to be unaffected by hunger
        // todo: mixin and replace cake with a proper eat function
    }

    @Override
    public void eat(Item maybeFood, ItemStack stack)
    {
        stack.getCapability(FoodCapability.CAPABILITY).ifPresent(this::eat);
    }

    /**
     * Called from {@link Player#tick()} on server side only
     *
     * @param player the player who's food stats this is
     */
    @Override
    public void tick(Player player)
    {
        final Difficulty difficulty = player.level.getDifficulty();
        if (difficulty == Difficulty.PEACEFUL && TFCConfig.SERVER.peacefulDifficultyPassiveRegeneration.get())
        {
            // Extra-Peaceful Difficulty
            // Health regeneration modified from PlayerEntity#aiStep()
            if (sourcePlayer.getHealth() < sourcePlayer.getMaxHealth() && sourcePlayer.tickCount % 20 == 0)
            {
                sourcePlayer.heal(1.0F);
            }
            if (sourcePlayer.tickCount % 10 == 0)
            {
                if (needsFood())
                {
                    setFoodLevel(getFoodLevel() + 1);
                }
                if (thirst < MAX_THIRST)
                {
                    addThirst(5f);
                }
            }
        }
        else
        {
            // Passive exhaustion - call the source player instead of the local method
            player.causeFoodExhaustion(PASSIVE_EXHAUSTION / EXHAUSTION_MULTIPLIER * TFCConfig.SERVER.passiveExhaustionModifier.get().floatValue());

            // Same check as the original food stats, so hunger and thirst loss are synced
            if (delegate.getExhaustionLevel() >= 4.0F)
            {
                addThirst(-TFCConfig.SERVER.thirstModifier.get().floatValue());

                // Vanilla will consume exhaustion and saturation in peaceful, but won't modify hunger.
                // We mimic the same checks that are about to happen in tick(), and if needed, consume hunger in advance
                if (difficulty == Difficulty.PEACEFUL && getSaturationLevel() <= 0)
                {
                    setFoodLevel(Math.max(getFoodLevel() - 1, 0));
                }
            }

            if (difficulty == Difficulty.PEACEFUL)
            {
                // Copied from vanilla's food stats, so we consume food in peaceful mode (would normally be part of the super.onUpdate call
                if (delegate.getExhaustionLevel() > 4.0F)
                {
                    setFoodLevel(Math.max(getFoodLevel() - 1, 0));
                }
            }
        }

        // Next, tick the original food stats
        delegate.tick(player);

        // Apply custom TFC regeneration
        if (player.isHurt() && getFoodLevel() >= 4.0f && getThirst() > 20f)
        {
            healTimer++;
            float multiplier = 1;
            if (getFoodLevel() > 16.0f && getThirst() > 80f) // Triple healing at >80% hunger and thirst
            {
                multiplier = 3;
            }

            if (healTimer > 10)
            {
                player.heal(multiplier * PASSIVE_HEAL_AMOUNT * TFCConfig.SERVER.naturalRegenerationModifier.get().floatValue());
                healTimer = 0;
            }
        }

        // Last, apply negative effects due to thirst
        if (player.tickCount % 100 == 0 && difficulty != Difficulty.PEACEFUL && !player.getAbilities().invulnerable)
        {
            if (thirst < 10f)
            {
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 160, 1, false, false));
                player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 160, 1, false, false));
                if (thirst <= 0f)
                {
                    // Hurt the player, same as starvation
                    player.hurt(TFCDamageSources.DEHYDRATION, 1);
                }
            }
            else if (thirst < 20f)
            {
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 160, 0, false, false));
                player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 160, 0, false, false));
            }
        }

        // Since this is only called server side, and vanilla has a custom packet for this stuff, we need our own
        if (player instanceof ServerPlayer serverPlayer)
        {
            PacketHandler.send(PacketDistributor.PLAYER.with(() -> serverPlayer), new FoodStatsUpdatePacket(nutritionStats.getNutrients(), thirst));
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag vanillaNbt)
    {
        delegate.readAdditionalSaveData(vanillaNbt);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag vanillaNbt)
    {
        delegate.addAdditionalSaveData(vanillaNbt);
    }

    @Override
    public int getFoodLevel()
    {
        return delegate.getFoodLevel();
    }

    @Override
    public boolean needsFood()
    {
        return delegate.needsFood();
    }

    @Override
    public void addExhaustion(float exhaustion)
    {
        delegate.addExhaustion(EXHAUSTION_MULTIPLIER * exhaustion);
    }

    @Override
    public float getSaturationLevel()
    {
        return delegate.getSaturationLevel();
    }

    @Override
    public void setFoodLevel(int food)
    {
        delegate.setFoodLevel(food);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public void setSaturation(float saturation)
    {
        delegate.setSaturation(saturation);
    }

    public void eat(IFood food)
    {
        // Eating items has nutritional benefits
        final FoodRecord data = food.getData();
        if (!food.isRotten())
        {
            eat(data);
        }
        else if (this.sourcePlayer instanceof ServerPlayer) // Check for server side first
        {
            // Minor effects from eating rotten food
            final Random random = sourcePlayer.getRandom();
            if (random.nextFloat() < 0.6)
            {
                sourcePlayer.addEffect(new MobEffectInstance(MobEffects.HUNGER, 1800, 1));
                if (random.nextFloat() < 0.15)
                {
                    sourcePlayer.addEffect(new MobEffectInstance(MobEffects.POISON, 1800, 0));
                }
            }
        }
    }

    public void eat(FoodRecord data)
    {
        addThirst(data.getWater());
        nutritionStats.addNutrients(data);

        // In order to get the exact saturation we want, apply this scaling factor here
        delegate.eat(data.getHunger(), data.getSaturation() / (2f * data.getHunger()));
    }

    public CompoundTag serializeToPlayerData()
    {
        CompoundTag nbt = new CompoundTag();

        nbt.putFloat("thirst", thirst);
        nbt.putFloat("lastDrinkTick", lastDrinkTick);
        nbt.put("nutrients", nutritionStats.serializeNBT());

        return nbt;
    }

    public void deserializeFromPlayerData(CompoundTag nbt)
    {
        thirst = nbt.getFloat("thirst");
        lastDrinkTick = nbt.getLong("lastDrinkTick");
        nutritionStats.deserializeNBT(nbt.getCompound("nutrients"));
    }

    /**
     * Sets data from a packet, received on client side. Does not contain the full data only the important information
     */
    public void onClientUpdate(float[] nutrients, float thirst)
    {
        this.nutritionStats.onClientUpdate(nutrients);
        this.thirst = thirst;
    }

    public float getHealthModifier()
    {
        return 0.25f + 1.5f * nutritionStats.getAverageNutrition();
    }

    public float getThirst()
    {
        return thirst;
    }

    public void setThirst(float thirst)
    {
        this.thirst = Mth.clamp(thirst, 0, MAX_THIRST);
    }

    public void addThirst(float toAdd)
    {
        setThirst(thirst + toAdd);
    }

    public NutritionStats getNutrition()
    {
        return nutritionStats;
    }

    public boolean attemptDrink(float value, boolean simulate)
    {
        int ticksPassed = (int) (sourcePlayer.level.getGameTime() - lastDrinkTick);
        if (ticksPassed >= 12 && thirst < MAX_THIRST)
        {
            if (!simulate)
            {
                // One drink every so often
                lastDrinkTick = sourcePlayer.level.getGameTime();
                addThirst(value);

                // todo: add a generic handler for drinkable fluids that is defined in json
                // needs to handle (at least) thirst, and application of random potion effects (the potion effect name, level, duration, and a chance number)
                // Salty drink effect
                //if (value < 0 && sourcePlayer.getRandom() < TFCConfig.SERVER.saltWaterDrinkThirstChance)
                //{
                //    sourcePlayer.addPotionEffect(new PotionEffect(PotionEffectsTFC.THIRST, 600, 0));
                //}
            }
            return true;
        }
        return false;
    }
}