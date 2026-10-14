package dev.slc.item;

import dev.slc.elevator.ElevatorButtonBlockEntity;
import dev.slc.zone.SpatialZoneManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

public class SpatialMarkerWandItem extends Item {
    public SpatialMarkerWandItem(Properties properties) {
        super(properties);
    }

    @Override
    public boolean canAttackBlock(net.minecraft.world.level.block.state.BlockState state, Level level, BlockPos pos, Player player) {
        if (!level.isClientSide) {
            ItemStack stack = player.getMainHandItem();
            CompoundTag tag = stack.getOrCreateTag();
            tag.putInt("Pos1X", pos.getX());
            tag.putInt("Pos1Y", pos.getY());
            tag.putInt("Pos1Z", pos.getZ());
            player.displayClientMessage(Component.literal("§6[Wand] §eТочка 1 (Pos1) установлена: §f" + pos.toShortString()), true);
        }
        return false; // Don't break the block
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();

        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (player == null) return InteractionResult.PASS;

        CompoundTag tag = stack.getOrCreateTag();

        if (player.isShiftKeyDown()) {
            // Apply Selected AABB
            if (!tag.contains("Pos1X") || !tag.contains("Pos2X")) {
                player.displayClientMessage(Component.literal("§c[Wand] Сначала выделите Точку 1 (ЛКМ) и Точку 2 (ПКМ)!"), true);
                return InteractionResult.CONSUME;
            }

            BlockPos p1 = new BlockPos(tag.getInt("Pos1X"), tag.getInt("Pos1Y"), tag.getInt("Pos1Z"));
            BlockPos p2 = new BlockPos(tag.getInt("Pos2X"), tag.getInt("Pos2Y"), tag.getInt("Pos2Z"));
            AABB zoneAABB = new AABB(
                    Math.min(p1.getX(), p2.getX()), Math.min(p1.getY(), p2.getY()), Math.min(p1.getZ(), p2.getZ()),
                    Math.max(p1.getX(), p2.getX()) + 1.0, Math.max(p1.getY(), p2.getY()) + 1.0, Math.max(p1.getZ(), p2.getZ()) + 1.0
            );

            if (level instanceof ServerLevel serverLevel) {
                SpatialZoneManager zoneMgr = SpatialZoneManager.get(serverLevel);

                if (level.getBlockEntity(pos) instanceof ElevatorButtonBlockEntity) {
                    // Bind to elevator cabin
                    zoneMgr.setElevatorCabin(pos, zoneAABB);
                    player.displayClientMessage(Component.literal("§a[Wand] §lКабина лифта сохранена для кнопки: §f" + pos.toShortString() + " §7(Размер: " + (int)zoneAABB.getXsize() + "x" + (int)zoneAABB.getYsize() + "x" + (int)zoneAABB.getZsize() + ")"), false);
                } else {
                    // Bind as surface escape zone
                    zoneMgr.setEscapeZone(zoneAABB);
                    player.displayClientMessage(Component.literal("§b[Wand] §lЗона побега на Поверхности (Escape Zone) сохранена! §7(" + p1.toShortString() + " -> " + p2.toShortString() + ")"), false);
                }
            }
            return InteractionResult.CONSUME;
        } else {
            // Set Pos 2
            tag.putInt("Pos2X", pos.getX());
            tag.putInt("Pos2Y", pos.getY());
            tag.putInt("Pos2Z", pos.getZ());
            player.displayClientMessage(Component.literal("§6[Wand] §aТочка 2 (Pos2) установлена: §f" + pos.toShortString()), true);
            return InteractionResult.CONSUME;
        }
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide && player.isShiftKeyDown()) {
            player.displayClientMessage(Component.literal("§e[Wand] Кликните Shift+ПКМ по Кнопке Лифта чтобы привязать кабину, или по блоку побега для Escape Zone!"), false);
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }
}
