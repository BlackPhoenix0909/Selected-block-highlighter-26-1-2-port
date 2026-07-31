package cc.spea.selectedblockhighlighter.client;

import cc.spea.selectedblockhighlighter.config.ModConfig;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;

import java.util.ArrayList;
import java.util.List;

/**
 * Unveraendert in seiner Logik gegenueber 1.20.1 - nur auf offizielle
 * Mojang-Mappings (statt Yarn intermediary) umgestellt, die Fabric ab
 * ca. 1.21.9 verwendet.
 */
@Environment(EnvType.CLIENT)
public class BlockScanner {
    private static List<BlockPos> matchingBlocks = new ArrayList<>();
    private static Block lastScannedBlock = null;
    private static Fluid lastScannedFluid = null;
    private static ItemStack lastScannedItem = ItemStack.EMPTY;
    private static BlockPos lastPlayerPos = null;

    public static List<BlockPos> getMatchingBlocks() {
        return matchingBlocks;
    }

    public static void scanForBlocks() {
        Minecraft client = Minecraft.getInstance();
        ModConfig config = ModConfig.getInstance();

        if (client.player == null || client.level == null || !config.isEnabled()) {
            reset();
            return;
        }

        ItemStack heldItem = client.player.getMainHandItem();
        BlockPos playerPos = client.player.blockPosition();

        Block targetBlock = null;
        Fluid targetFluid = null;

        Item item = heldItem.getItem();
        if (item instanceof BlockItem blockItem) {
            targetBlock = blockItem.getBlock();
        } else if (heldItem.is(Items.WATER_BUCKET)) {
            targetFluid = Fluids.WATER;
        } else if (heldItem.is(Items.LAVA_BUCKET)) {
            targetFluid = Fluids.LAVA;
        } else {
            reset();
            return;
        }

        boolean sameTarget = (targetBlock != null && targetBlock.equals(lastScannedBlock))
                || (targetFluid != null && targetFluid.equals(lastScannedFluid));

        if (sameTarget && ItemStack.matches(heldItem, lastScannedItem) && playerPos.equals(lastPlayerPos)) {
            return;
        }

        lastScannedBlock = targetBlock;
        lastScannedFluid = targetFluid;
        lastScannedItem = heldItem.copy();
        lastPlayerPos = playerPos;
        matchingBlocks.clear();

        ClientLevel level = client.level;
        int range = config.getScanRange();

        for (int x = -range; x <= range; x++) {
            for (int y = -range; y <= range; y++) {
                for (int z = -range; z <= range; z++) {
                    BlockPos pos = playerPos.offset(x, y, z);

                    if (targetFluid != null) {
                        FluidState fluidState = level.getFluidState(pos);
                        if (fluidState.getType() != targetFluid || !fluidState.isSource()) {
                            continue;
                        }
                        matchingBlocks.add(pos.immutable());
                        continue;
                    }

                    BlockState state = level.getBlockState(pos);
                    if (targetBlock == null || state.getBlock() != targetBlock) {
                        continue;
                    }
                    matchingBlocks.add(pos.immutable());
                }
            }
        }
    }

    public static void clear() {
        reset();
    }

    private static void reset() {
        matchingBlocks.clear();
        lastScannedBlock = null;
        lastScannedFluid = null;
        lastScannedItem = ItemStack.EMPTY;
        lastPlayerPos = null;
    }
}
