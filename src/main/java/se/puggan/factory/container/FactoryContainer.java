package se.puggan.factory.container;

import com.google.common.collect.Lists;
import net.minecraft.client.util.RecipeBookCategories;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.inventory.CraftingInventory;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.IRecipeHelperPopulator;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.inventory.container.RecipeBookContainer;
import net.minecraft.inventory.container.Slot;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.*;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.IntReferenceHolder;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IWorld;
import net.minecraft.world.World;
import net.minecraftforge.fml.network.PacketDistributor;
import se.puggan.factory.Factory;
import se.puggan.factory.blocks.FactoryBlock;
import se.puggan.factory.container.slot.*;
import se.puggan.factory.network.FactoryNetwork;
import se.puggan.factory.network.SetRecipeUsedMessage;
import se.puggan.factory.network.StateEnabledMessage;
import se.puggan.factory.util.RegistryHandler;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Optional;

public class FactoryContainer extends RecipeBookContainer<CraftingInventory> implements IRecipeHelperPopulator {
    private FactoryScreen screen;
    private boolean enabled;
    private CraftingInventory cInvetory;
    private SlotInvetory craftingResultInventory;
    private CraftingInventory inputInvetory;
    private SlotInvetory outputStackInventory;
    private PlayerInventory pInventory;
    private FactoryEntity fInventory;
    private boolean loading;
    private final int resultSlotIndex = 9;
    private final int outputSlotIndex = 19;
    private boolean clientSide;

    public FactoryContainer(int windowId, PlayerInventory playerInventory, IInventory inventory) {
        super(RegistryHandler.FACTORY_CONTAINER.get(), windowId);
        loading = true;
        pInventory = playerInventory;
        cInvetory = new SyncedCraftingInventory(this, 3, 3, inventory, 0);
        craftingResultInventory = new SyncedSlotInventory(inventory, 9);
        inputInvetory = new SyncedCraftingInventory(this, 3, 3, inventory, 10);
        outputStackInventory = new SyncedSlotInventory(inventory, 19);
        if (inventory instanceof FactoryEntity) {
            fInventory = (FactoryEntity) inventory;
            World world = fInventory.getWorld();
            if(world == null) {
                fInventory.setWorldAndPos(world = FactoryBlock.lastWorld, FactoryBlock.lastBlockPosition);
            }
            clientSide = world.isRemote;
            if(!clientSide) {
                fInventory.stateOpen(true);
            }
            enabled = fInventory.getState(FactoryBlock.enabledProperty);
            Factory.LOGGER.warn("FactoryContainer() " + (enabled ? "Enabled" : "Disabled"));
        } else {
            clientSide = !(pInventory.player instanceof ServerPlayerEntity);
        }
        slotInventory();
        loading = false;
        if(enabled) {
            Factory.LOGGER.warn("FactoryContainer() Activate");
            activate(false);
        } else {
            Factory.LOGGER.warn("FactoryContainer() De-Activate");
            deactivate(false);
        }
        onCraftMatrixChanged(cInvetory);
    }

    public FactoryContainer(int windowId, PlayerInventory playerInventory, PacketBuffer extraData) {
        this(windowId, playerInventory, new FactoryEntity());
    }

    private void slotInventory() {
        // Receipt Inventory, 0-8
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                addSlot(new ReceiptSlot(cInvetory, 3 * y + x, 79 + 18 * x, 16 + 18 * y));
            }
        }
        // Receipt output, 9
        addSlot(new HiddenSlot(craftingResultInventory, 0, 151, 16));

        // InBox, 10-18
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) {
                int index = 3 * y + x;
                ItemSlot slot = new ItemSlot(inputInvetory, index, 7 + 18 * x, 16 + 18 * y, enabled);
                addSlot(slot);
                if (enabled) {
                    slot.lockItem(inventorySlots.get(index));
                }
            }
        }
        // Outbox, 19
        ItemSlot outSlot = new ItemSlot(outputStackInventory, 0, 151, 52, false);
        addSlot(outSlot);
        if (enabled) {
            outSlot.lockItem(craftingResultInventory.getStackInSlot(0));
        }

        // Player Hotbar, 0-8
        for (int x = 0; x < 9; ++x) {
            addSlot(new PlayerSlot(pInventory, x, 8 + x * 18, 143));
        }

        // Player Inventory, 9-35
        for (int y = 0; y < 3; ++y) {
            for (int x = 0; x < 9; ++x) {
                addSlot(new PlayerSlot(pInventory, 9 + 9 * y + x, 8 + 18 * x, 85 + 18 * y));
            }
        }
    }

    @Override
    public boolean canInteractWith(@Nonnull PlayerEntity player) {
        return true;
    }

    public void setScreen(FactoryScreen factoryScreen) {
        screen = factoryScreen;
    }

    @Override
    public void fillStackedContents(RecipeItemHelper itemHelperIn) {
        cInvetory.fillStackedContents(itemHelperIn);
    }

    @Override
    public void clear() {
        cInvetory.clear();
        craftingResultInventory.clear();
        inputInvetory.clear();
        outputStackInventory.clear();
    }

    @Override
    public boolean matches(IRecipe<? super CraftingInventory> recipeIn) {
        fInventory.modeCrafting = true;
        boolean matches = recipeIn.matches(cInvetory, pInventory.player.world);
        fInventory.modeCrafting = false;
        return matches;
    }

    @Override
    public int getOutputSlot() {
        // 0-8 is in, 9 is out
        return 9;
    }

    @Override
    public int getWidth() {
        return 3;
    }

    @Override
    public int getHeight() {
        return 3;
    }

    @Override
    public int getSize() {
        return 10;
    }

    @Override
    @Nonnull
    public List<RecipeBookCategories> getRecipeBookCategories() {
        return Lists.newArrayList(
                RecipeBookCategories.SEARCH,
                RecipeBookCategories.EQUIPMENT,
                RecipeBookCategories.BUILDING_BLOCKS,
                RecipeBookCategories.MISC,
                RecipeBookCategories.REDSTONE
        );
    }

    public boolean activate() {return activate(true);}
    public boolean activate(boolean send) {
        if(pInventory.player.world != null && !pInventory.player.world.isRemote) {
            return true;
        }
        if (screen != null) {
            screen.enable();
        }
        if (enabled) return true;
        for (int i = 10; i < 20; i++) {
            Slot s = inventorySlots.get(i);
            if (s instanceof ItemSlot) {
                ((ItemSlot) s).lockItem(inventorySlots.get(i - 10));
            }
        }

        // TODO send to server, right now this function is only run on the client side
        Factory.LOGGER.info("activate() @ " + (pInventory.player instanceof ServerPlayerEntity ? "Server" : "Client") + "/" + (pInventory.player.world.isRemote ? "Remote" : "Local"));
        enabled = true;
        fInventory.stateEnabled(true);
        if(send) {
            FactoryNetwork.CHANNEL.sendToServer(new StateEnabledMessage(fInventory.getPos(), true));
        }
        return true;
    }

    public boolean deactivate() {return deactivate(true);}
    public boolean deactivate(boolean send) {
        if(pInventory.player.world != null && !pInventory.player.world.isRemote) {
            boolean stuffToDrop = false;
            for (int i = resultSlotIndex + 1; i <= outputSlotIndex; i++) {
                Slot slot = inventorySlots.get(i);
                if (slot instanceof ItemSlot) {
                    ItemStack stack = slot.getStack();
                    if (!stack.isEmpty()) {
                        pInventory.addItemStackToInventory(stack);
                    }
                    if (!stack.isEmpty()) {
                        if(i == outputSlotIndex) {
                            InventoryHelper.dropInventoryItems(pInventory.player.world, fInventory.getPos(), outputStackInventory);
                        } else {
                            stuffToDrop = true;
                        }
                    }
                    ((ItemSlot) slot).enabled = false;
                }
            }

            if(stuffToDrop) {
                InventoryHelper.dropInventoryItems(pInventory.player.world, fInventory.getPos(), inputInvetory);
            }

            detectAndSendChanges();
            return true;
        }

        if (screen != null) {
            screen.disable();
        }
        if (!enabled) return true;
        for (int i = 10; i < 20; i++) {
            Slot slot = inventorySlots.get(i);
            if (slot instanceof ItemSlot) {
                ((ItemSlot) slot).enabled = false;
            }
        }
        // TODO send to server, right now this function is only run on the client side
        Factory.LOGGER.info("deactivate() @ " + (pInventory.player instanceof ServerPlayerEntity ? "Server" : "Client") + "/" + (pInventory.player.world.isRemote ? "Remote" : "Local"));
        enabled = false;
        fInventory.stateEnabled(false);
        if(send) {
            FactoryNetwork.CHANNEL.sendToServer(new StateEnabledMessage(fInventory.getPos(), false));
        }
        return true;
    }

    @Override
    public void onCraftMatrixChanged(IInventory inventoryIn) {
        if (loading) {
            return;
        }
        super.onCraftMatrixChanged(inventoryIn);
        if(clientSide) {
            return;
        }

        ICraftingRecipe recipe = fInventory.getRecipeUsed();
        if (recipe != null && recipe.matches(cInvetory, pInventory.player.world)) {
            return;
        }

        Optional<ICraftingRecipe> optional = pInventory.player.world.getRecipeManager().getRecipe(IRecipeType.CRAFTING, cInvetory, pInventory.player.world);
        if (!optional.isPresent()) {
            if (recipe != null) {
                fInventory.setRecipeUsed(null);
                Factory.LOGGER.debug("onCraftMatrixChanged(): no Recipe");
            }
            deactivate(true);
            setReciptResult(ItemStack.EMPTY);
            return;
        }

        recipe = optional.get();
        Factory.LOGGER.debug("onCraftMatrixChanged(): Recipe " + recipe.getId());
        fInventory.setRecipeUsed(recipe);
        if(pInventory.player instanceof ServerPlayerEntity) {
            (new SetRecipeUsedMessage(fInventory.getPos(), recipe)).sendToPlayer((ServerPlayerEntity) pInventory.player);
        }
        setReciptResult(recipe.getCraftingResult(cInvetory));
    }

    private void setReciptResult(ItemStack stack) {
        Slot slot = inventorySlots.get(resultSlotIndex);
        if (slot instanceof LockedSlot) {
            ((LockedSlot) slot).enabled = !stack.isEmpty();
        }
        slot.putStack(stack);
        ((ItemSlot) inventorySlots.get(outputSlotIndex)).lockItem(stack);
        detectAndSendChanges();
    }

    @Override
    public void onContainerClosed(PlayerEntity playerIn) {
        super.onContainerClosed(playerIn);
        if(clientSide) {
            return;
        }
        fInventory.stateOpen(false);
    }

    public ServerPlayerEntity getServerPlayer() {
        return pInventory.player instanceof ServerPlayerEntity ? (ServerPlayerEntity)pInventory.player : null;
    }

    public boolean isEnabled() {
        return enabled;
    }
}