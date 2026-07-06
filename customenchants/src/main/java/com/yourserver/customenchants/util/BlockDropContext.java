package com.yourserver.customenchants.util;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * Carries everything captured about a just-broken block, at the moment the
 * on-break-block trigger fires (while the block/tool is still known), so
 * that a "blockdrop:" action line has:
 *   - a real drop to add bonus copies of (getDrops())
 *   - enough context (block, its pre-break state, and the player) to fire
 *     a genuine BlockDropItemEvent for the bonus items, so other plugins
 *     see them as real block drops rather than plain item-spawns.
 */
public final class BlockDropContext {

    private final Location location;
    private final List<ItemStack> drops;
    private final Block block;
    private final BlockState blockState;
    private final Player player;

    public BlockDropContext(Location location, List<ItemStack> drops, Block block, BlockState blockState, Player player) {
        this.location = location;
        this.drops = drops;
        this.block = block;
        this.blockState = blockState;
        this.player = player;
    }

    public Location getLocation() {
        return location;
    }

    public List<ItemStack> getDrops() {
        return drops;
    }

    /** The block that was broken. By the time actions run its physical state has usually already changed. */
    public Block getBlock() {
        return block;
    }

    /** A snapshot of the block as it was *before* it broke - the state a real BlockDropItemEvent should carry. */
    public BlockState getBlockState() {
        return blockState;
    }

    public Player getPlayer() {
        return player;
    }
}
