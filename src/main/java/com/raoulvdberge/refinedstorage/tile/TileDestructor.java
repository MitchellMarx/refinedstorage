package com.raoulvdberge.refinedstorage.tile;

import com.raoulvdberge.refinedstorage.RS;
import com.raoulvdberge.refinedstorage.RSUtils;
import com.raoulvdberge.refinedstorage.api.util.IComparer;
import com.raoulvdberge.refinedstorage.inventory.ItemHandlerBasic;
import com.raoulvdberge.refinedstorage.inventory.ItemHandlerFluid;
import com.raoulvdberge.refinedstorage.inventory.ItemHandlerUpgrade;
import com.raoulvdberge.refinedstorage.item.ItemUpgrade;
import com.raoulvdberge.refinedstorage.tile.config.IComparable;
import com.raoulvdberge.refinedstorage.tile.config.IFilterable;
import com.raoulvdberge.refinedstorage.tile.config.IType;
import com.raoulvdberge.refinedstorage.tile.data.ITileDataConsumer;
import com.raoulvdberge.refinedstorage.tile.data.ITileDataProducer;
import com.raoulvdberge.refinedstorage.tile.data.TileDataParameter;
import mcmultipart.microblock.IMicroblock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.inventory.InventoryHelper;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.IFluidBlock;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.wrappers.BlockLiquidWrapper;
import net.minecraftforge.fluids.capability.wrappers.FluidBlockWrapper;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.IItemHandler;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class TileDestructor extends TileMultipartNode implements IComparable, IFilterable, IType {
    public static final TileDataParameter<Integer> COMPARE = IComparable.createParameter();
    public static final TileDataParameter<Integer> MODE = IFilterable.createParameter();
    public static final TileDataParameter<Integer> TYPE = IType.createParameter();
    public static final TileDataParameter<Boolean> PICKUP = new TileDataParameter<>(DataSerializers.BOOLEAN, false, new ITileDataProducer<Boolean, TileDestructor>() {
        @Override
        public Boolean getValue(TileDestructor tile) {
            return tile.pickupItem;
        }
    }, new ITileDataConsumer<Boolean, TileDestructor>() {
        @Override
        public void setValue(TileDestructor tile, Boolean value) {
            tile.pickupItem = value;

            tile.markDirty();
        }
    });

    private static final String NBT_COMPARE = "Compare";
    private static final String NBT_MODE = "Mode";
    private static final String NBT_TYPE = "Type";
    private static final String NBT_PICKUP = "Pickup";

    private static final int BASE_SPEED = 20;

    private ItemHandlerBasic itemFilters = new ItemHandlerBasic(9, this);
    private ItemHandlerFluid fluidFilters = new ItemHandlerFluid(9, this);

    private ItemHandlerUpgrade upgrades = new ItemHandlerUpgrade(4, this, ItemUpgrade.TYPE_SPEED, ItemUpgrade.TYPE_SILK_TOUCH, ItemUpgrade.TYPE_FORTUNE);

    private int compare = IComparer.COMPARE_NBT | IComparer.COMPARE_DAMAGE;
    private int mode = IFilterable.WHITELIST;
    private int type = IType.ITEMS;
    private boolean pickupItem = false;

    public TileDestructor() {
        dataManager.addWatchedParameter(COMPARE);
        dataManager.addWatchedParameter(MODE);
        dataManager.addWatchedParameter(TYPE);
        dataManager.addWatchedParameter(PICKUP);
    }

    @Override
    public boolean canAddMicroblock(IMicroblock microblock) {
        return !isBlockingMicroblock(microblock, getDirection());
    }

    @Override
    public int getEnergyUsage() {
        return RS.INSTANCE.config.destructorUsage + upgrades.getEnergyUsage();
    }

    protected int ticks = 0;
    @Override
    public void updateNode() {
        ticks++;
        if (ticks % upgrades.getSpeed(BASE_SPEED, 4) == 0) {

            BlockPos front = pos.offset(getDirection());

            if (pickupItem && type == IType.ITEMS) {
                List<Entity> droppedItems = new ArrayList<>();

                Chunk chunk = getWorld().getChunkFromBlockCoords(front);
                chunk.getEntitiesWithinAABBForEntity(null, new AxisAlignedBB(front), droppedItems, null);

                for (Entity entity : droppedItems) {
                    if (entity instanceof EntityItem) {
                        ItemStack droppedItem = ((EntityItem) entity).getEntityItem();

                        if (IFilterable.canTake(itemFilters, mode, compare, droppedItem) && network.insertItem(droppedItem, droppedItem.stackSize, true) == null) {
                            network.insertItem(droppedItem.copy(), droppedItem.stackSize, false);

                            getWorld().removeEntity(entity);

                            break;
                        }
                    }
                }
            } else if (type == IType.ITEMS) {
                IBlockState frontBlockState = getWorld().getBlockState(front);
                Block frontBlock = frontBlockState.getBlock();

                ItemStack frontStack = frontBlock.getPickBlock(frontBlockState, null, getWorld(), front, null);

                if (frontStack != null) {
                    if (IFilterable.canTake(itemFilters, mode, compare, frontStack) && frontBlockState.getBlockHardness(getWorld(), front) != -1.0) {
                        List<ItemStack> drops;
                        if (upgrades.hasUpgrade(ItemUpgrade.TYPE_SILK_TOUCH) && frontBlock.canSilkHarvest(getWorld(), front, frontBlockState, null)) {
                            drops = Collections.singletonList(frontStack);
                        } else {
                            drops = frontBlock.getDrops(getWorld(), front, frontBlockState, upgrades.getFortuneLevel());
                        }

                        for (ItemStack drop : drops) {
                            if (network.insertItem(drop, drop.stackSize, true) != null) {
                                return;
                            }
                        }

                        BlockEvent.BreakEvent e = new BlockEvent.BreakEvent(getWorld(), front, frontBlockState, FakePlayerFactory.getMinecraft((WorldServer) getWorld()));

                        if (!MinecraftForge.EVENT_BUS.post(e)) {
                            getWorld().playEvent(null, 2001, front, Block.getStateId(frontBlockState));
                            getWorld().setBlockToAir(front);

                            for (ItemStack drop : drops) {
                                // We check if the controller isn't null here because when a destructor faces a node and removes it
                                // it will essentially remove this block itself from the network without knowing
                                if (network == null) {
                                    InventoryHelper.spawnItemStack(getWorld(), front.getX(), front.getY(), front.getZ(), drop);
                                } else {
                                    network.insertItem(drop, drop.stackSize, false);
                                }
                            }
                        }
                    }
                }
            } else if (type == IType.FLUIDS) {
                Block frontBlock = getWorld().getBlockState(front).getBlock();

                IFluidHandler handler = null;

                if (frontBlock instanceof BlockLiquid) {
                    handler = new BlockLiquidWrapper((BlockLiquid) frontBlock, getWorld(), front);
                } else if (frontBlock instanceof IFluidBlock) {
                    handler = new FluidBlockWrapper((IFluidBlock) frontBlock, getWorld(), front);
                }

                if (handler != null) {
                    FluidStack stack = handler.drain(Fluid.BUCKET_VOLUME, false);

                    if (stack != null && IFilterable.canTakeFluids(fluidFilters, mode, compare, stack) && network.insertFluid(stack, stack.amount, true) == null) {
                        FluidStack drained = handler.drain(Fluid.BUCKET_VOLUME, true);

                        network.insertFluid(drained, drained.amount, false);
                    }
                }
            }
        }
    }

    @Override
    public int getCompare() {
        return compare;
    }

    @Override
    public void setCompare(int compare) {
        this.compare = compare;

        markDirty();
    }

    @Override
    public int getMode() {
        return mode;
    }

    @Override
    public void setMode(int mode) {
        this.mode = mode;

        markDirty();
    }

    @Override
    public void read(NBTTagCompound tag) {
        super.read(tag);

        RSUtils.readItems(upgrades, 1, tag);
    }

    @Override
    public NBTTagCompound write(NBTTagCompound tag) {
        super.write(tag);

        RSUtils.writeItems(upgrades, 1, tag);

        return tag;
    }

    @Override
    public NBTTagCompound writeConfiguration(NBTTagCompound tag) {
        super.writeConfiguration(tag);

        tag.setInteger(NBT_COMPARE, compare);
        tag.setInteger(NBT_MODE, mode);
        tag.setInteger(NBT_TYPE, type);
        tag.setBoolean(NBT_PICKUP, pickupItem);

        RSUtils.writeItems(itemFilters, 0, tag);
        RSUtils.writeItems(fluidFilters, 2, tag);

        return tag;
    }

    @Override
    public void readConfiguration(NBTTagCompound tag) {
        super.readConfiguration(tag);

        if (tag.hasKey(NBT_COMPARE)) {
            compare = tag.getInteger(NBT_COMPARE);
        }

        if (tag.hasKey(NBT_MODE)) {
            mode = tag.getInteger(NBT_MODE);
        }

        if (tag.hasKey(NBT_TYPE)) {
            type = tag.getInteger(NBT_TYPE);
        }

        if (tag.hasKey(NBT_PICKUP)) {
            pickupItem = tag.getBoolean(NBT_PICKUP);
        }

        RSUtils.readItems(itemFilters, 0, tag);
        RSUtils.readItems(fluidFilters, 2, tag);
    }

    public IItemHandler getUpgrades() {
        return upgrades;
    }

    public IItemHandler getInventory() {
        return itemFilters;
    }

    @Override
    public boolean hasConnectivityState() {
        return true;
    }

    @Override
    public IItemHandler getDrops() {
        return upgrades;
    }

    @Override
    public int getType() {
        return getWorld().isRemote ? TYPE.getValue() : type;
    }

    @Override
    public void setType(int type) {
        this.type = type;

        markDirty();
    }

    @Override
    public IItemHandler getFilterInventory() {
        return getType() == IType.ITEMS ? itemFilters : fluidFilters;
    }

    @Override
    public <T> T getCapability(Capability<T> capability, EnumFacing facing) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return (T) upgrades;
        }

        return super.getCapability(capability, facing);
    }

    @Override
    public boolean hasCapability(Capability<?> capability, EnumFacing facing) {
        return capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY || super.hasCapability(capability, facing);
    }
}
