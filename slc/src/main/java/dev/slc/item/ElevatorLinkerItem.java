package dev.slc.item;

import dev.slc.elevator.ElevatorButtonBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public class ElevatorLinkerItem extends Item {
    public ElevatorLinkerItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos clickedPos = context.getClickedPos();
        ItemStack stack = context.getItemInHand();

        if (level.isClientSide) return InteractionResult.SUCCESS;

        BlockEntity be = level.getBlockEntity(clickedPos);
        if (!(be instanceof ElevatorButtonBlockEntity currentElevator)) {
            if (context.getPlayer() != null) {
                context.getPlayer().displayClientMessage(Component.literal("§c[Linker] Кликните по кнопке лифта (Elevator Button)!"), true);
            }
            return InteractionResult.CONSUME;
        }

        CompoundTag tag = stack.getOrCreateTag();
        if (!tag.contains("SourceX")) {
            // Store Source Elevator
            tag.putInt("SourceX", clickedPos.getX());
            tag.putInt("SourceY", clickedPos.getY());
            tag.putInt("SourceZ", clickedPos.getZ());
            if (context.getPlayer() != null) {
                context.getPlayer().displayClientMessage(Component.literal("§e[Linker] Первая кнопка лифта выбрана: §f" + clickedPos.toShortString() + "§e. Теперь кликните по второй кнопке!"), true);
            }
        } else {
            // Link Source and Target
            BlockPos srcPos = new BlockPos(tag.getInt("SourceX"), tag.getInt("SourceY"), tag.getInt("SourceZ"));
            tag.remove("SourceX");
            tag.remove("SourceY");
            tag.remove("SourceZ");

            if (srcPos.equals(clickedPos)) {
                if (context.getPlayer() != null) {
                    context.getPlayer().displayClientMessage(Component.literal("§c[Linker] Нельзя привязать кнопку лифта к самой себе! Сброс."), true);
                }
                return InteractionResult.CONSUME;
            }

            BlockEntity srcBe = level.getBlockEntity(srcPos);
            if (srcBe instanceof ElevatorButtonBlockEntity srcElevator) {
                srcElevator.setLinkedPos(clickedPos);
                currentElevator.setLinkedPos(srcPos);

                if (context.getPlayer() != null) {
                    context.getPlayer().displayClientMessage(Component.literal("§a[Linker] §lЛифты успешно связаны! §7(" + srcPos.toShortString() + " <--> " + clickedPos.toShortString() + ")"), false);
                }
            } else {
                if (context.getPlayer() != null) {
                    context.getPlayer().displayClientMessage(Component.literal("§c[Linker] Исходная кнопка лифта не найдена! Сброс."), true);
                }
            }
        }

        return InteractionResult.CONSUME;
    }
}
