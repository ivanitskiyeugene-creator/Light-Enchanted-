package dev.slc.item;

import dev.slc.elevator.ElevatorButtonBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * Links ANY number of Elevator Button blocks into ONE elevator network.
 *
 * Usage (silent feedback, sounds only):
 *  - Right-click an elevator button      -> add/remove it as an EXTERIOR (floor) call button
 *  - Sneak + right-click a button        -> add/remove it as the INTERIOR (in-cabin) button
 *  - Right-click AIR                     -> finalize the network (needs >= 2 exterior buttons)
 *  - Sneak + right-click AIR             -> clear selection
 */
public class ElevatorLinkerItem extends Item {
    private static final String SEL_TAG = "SlcLiftSelection";
    private static final int MAX_MEMBERS = 16;

    public ElevatorLinkerItem(Properties properties) {
        super(properties);
    }

    private static ListTag getSelection(ItemStack stack) {
        CompoundTag tag = stack.getOrCreateTag();
        if (!tag.contains(SEL_TAG, Tag.TAG_LIST)) {
            tag.put(SEL_TAG, new ListTag());
        }
        return tag.getList(SEL_TAG, Tag.TAG_COMPOUND);
    }

    private static int indexOf(ListTag list, BlockPos pos) {
        for (int i = 0; i < list.size(); i++) {
            CompoundTag m = list.getCompound(i);
            if (m.getInt("X") == pos.getX() && m.getInt("Y") == pos.getY() && m.getInt("Z") == pos.getZ()) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos();
        ItemStack stack = context.getItemInHand();
        Player player = context.getPlayer();

        if (level.isClientSide) return InteractionResult.SUCCESS;

        BlockEntity be = level.getBlockEntity(clickedPos);
        if (!(be instanceof ElevatorButtonBlockEntity)) {
            return InteractionResult.PASS; // not an elevator button
        }

        ListTag sel = getSelection(stack);
        boolean asInterior = player != null && player.isShiftKeyDown();
        int existing = indexOf(sel, clickedPos);

        if (existing >= 0) {
            // Toggle selection OFF
            sel.remove(existing);
            level.playSound(null, clickedPos, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.BLOCKS, 0.7f, 0.9f);
        } else if (sel.size() < MAX_MEMBERS) {
            CompoundTag m = new CompoundTag();
            m.putInt("X", clickedPos.getX());
            m.putInt("Y", clickedPos.getY());
            m.putInt("Z", clickedPos.getZ());
            m.putBoolean("Interior", asInterior);
            sel.add(m);
            // Interior member -> lower pitch chime, floor member -> higher pitch
            level.playSound(null, clickedPos, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.6f, asInterior ? 0.7f : 1.4f);
        }

        return InteractionResult.CONSUME;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(stack);

        ListTag sel = getSelection(stack);

        if (player.isShiftKeyDown()) {
            // Sneak + air click = clear selection
            sel.clear();
            level.playSound(null, player.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.7f, 0.6f);
            return InteractionResultHolder.consume(stack);
        }

        // Air click = finalize the network
        List<BlockPos> members = new ArrayList<>();
        List<Boolean> interiors = new ArrayList<>();
        int exteriorCount = 0;
        for (int i = 0; i < sel.size(); i++) {
            CompoundTag m = sel.getCompound(i);
            BlockPos pos = new BlockPos(m.getInt("X"), m.getInt("Y"), m.getInt("Z"));
            boolean interiorFlag = m.getBoolean("Interior");
            members.add(pos);
            interiors.add(interiorFlag);
            if (!interiorFlag) exteriorCount++;
        }

        if (members.size() < 2 || exteriorCount < 2) {
            level.playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 0.8f, 0.5f);
            return InteractionResultHolder.consume(stack);
        }

        int linked = 0;
        for (BlockPos pos : members) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ElevatorButtonBlockEntity button) {
                button.applyNetwork(members, interiors);
                linked++;
            }
        }

        sel.clear();
        if (linked >= 2) {
            level.playSound(null, player.blockPosition(), SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.7f, 1.6f);
        } else {
            level.playSound(null, player.blockPosition(), SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 0.8f, 0.5f);
        }
        return InteractionResultHolder.consume(stack);
    }
}
