/*
 * Licensed under the EUPL, Version 1.2.
 * You may obtain a copy of the Licence at:
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 */

package net.dries007.tfc.common.tileentity;

import javax.annotation.Nullable;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraftforge.items.ItemStackHandler;

import net.dries007.tfc.common.TFCTags;
import net.dries007.tfc.common.container.LogPileContainer;
import net.dries007.tfc.util.Helpers;

import static net.dries007.tfc.TerraFirmaCraft.MOD_ID;

public class LogPileTileEntity extends InventoryTileEntity<ItemStackHandler> implements MenuProvider
{
    private static final Component NAME = new TranslatableComponent(MOD_ID + ".tile_entity.log_pile");

    private int playersUsing;

    public LogPileTileEntity()
    {
        super(TFCTileEntities.LOG_PILE.get(), defaultInventory(4), NAME);
        this.playersUsing = 0;
    }

    @Override
    public void setAndUpdateSlots(int slot)
    {
        super.setAndUpdateSlots(slot);
        if (level != null && !level.isClientSide())
        {
            if (playersUsing == 0 && isEmpty())
            {
                level.setBlockAndUpdate(worldPosition, Blocks.AIR.defaultBlockState());
            }
        }
    }

    public void onOpen(Player player)
    {
        if (!player.isSpectator())
        {
            playersUsing++;
        }
    }

    public void onClose(Player player)
    {
        if (!player.isSpectator())
        {
            playersUsing--;
            if (playersUsing < 0)
            {
                playersUsing = 0;
            }
            setAndUpdateSlots(-1);
        }
    }

    public boolean isEmpty()
    {
        for (ItemStack stack : Helpers.iterate(inventory))
        {
            if (!stack.isEmpty())
            {
                return false;
            }
        }
        return true;
    }

    public int logCount()
    {
        int count = 0;
        for (ItemStack stack : Helpers.iterate(inventory))
        {
            count += stack.getCount();
        }
        return count;
    }

    @Override
    public int getSlotStackLimit(int slot)
    {
        return 4;
    }

    @Override
    public boolean isItemValid(int slot, ItemStack stack)
    {
        return stack.getItem().is(TFCTags.Items.LOG_PILE_LOGS);
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int windowID, Inventory inv, Player player)
    {
        return new LogPileContainer(this, inv, windowID);
    }
}
