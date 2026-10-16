package dev.slc.item;

import dev.slc.admin.AdminPermission;
import dev.slc.entity.TestDummyEntity;
import dev.slc.init.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/** Configured OP-only spawner for the three separate dummy entity types. */
public final class DummySpawnerItem extends Item {
    public DummySpawnerItem(Properties properties) { super(properties); }

    public static ItemStack configured(String type, String aiMode) {
        ItemStack stack = new ItemStack(dev.slc.init.ModItems.DUMMY_SPAWNER.get());
        stack.getOrCreateTag().putString("DummyType", type);
        stack.getOrCreateTag().putString("AiMode", aiMode);
        return stack;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(context.getPlayer() instanceof ServerPlayer player) || !AdminPermission.isAdmin(player)) return InteractionResult.FAIL;
        CompoundTag tag = context.getItemInHand().getOrCreateTag();
        EntityType<TestDummyEntity> type = switch (tag.getString("DummyType")) {
            case "RESPAWN" -> ModEntities.RESPAWN_DUMMY.get();
            case "AI" -> ModEntities.AI_DUMMY.get();
            default -> ModEntities.STATIC_DUMMY.get();
        };
        TestDummyEntity dummy = type.create(level);
        if (dummy == null) return InteractionResult.FAIL;
        BlockPos pos = context.getClickedPos().above();
        dummy.moveTo(pos.getX() + .5D, pos.getY(), pos.getZ() + .5D, player.getYRot(), 0F);
        if (type == ModEntities.AI_DUMMY.get()) {
            try { dummy.setAiMode(TestDummyEntity.AiMode.valueOf(tag.getString("AiMode"))); } catch (IllegalArgumentException ignored) { }
        }
        level.addFreshEntity(dummy);
        return InteractionResult.CONSUME;
    }
}
