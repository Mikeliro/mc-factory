package se.puggan.factory.container.slot;

import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.container.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

import javax.annotation.Nullable;

public class ItemSlot extends Slot {
    public Item lockedItem = null;
    public boolean enabled;

    public ItemSlot(IInventory inventoryIn, int index, int xPosition, int yPosition, boolean enabled) {
        super(inventoryIn, index, xPosition, yPosition);
        this.enabled = enabled;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public boolean isItemValid(ItemStack stack) {
        return lockedItem != null && stack.getItem() == lockedItem;
    }

    public void lockItem(Slot slot) {
        lockItem(slot.getStack().getItem());
    }
    public void lockItem(ItemStack stack) {
        lockItem(stack.getItem());
    }
    public void lockItem(@Nullable Item item) {
        lockedItem = item;
        enabled = item != null;
        ItemStack stack = getStack();
        if(stack.isEmpty()) {
            return;
        }
        if(stack.getItem() == item) {
            return;
        }
        // TODO return item to player
    }
}