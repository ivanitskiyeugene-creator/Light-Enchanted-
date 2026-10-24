package dev.zerosevennine.facility;

import dev.zerosevennine.config.Scp079Config;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LeverBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.lang.reflect.Method;

@Mod.EventBusSubscriber(modid = "zero_seven_nine", bus = Mod.EventBusSubscriber.Bus.FORGE)
public class UniversalDoorHandler {

    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event) {
        if (!event.getEntity().level().isClientSide && event.getEntity() instanceof ArmorStand stand) {
            if (stand.getTags().contains("scp079_tesla_dummy") && stand.tickCount > 4) {
                stand.discard();
            }
        }
    }

    private static final ResourceLocation[] PREFERRED_REMOTE_ACCESS_CARDS = {
            new ResourceLocation("scpfr", "o_5_keycard"),
            new ResourceLocation("scpfr", "o5_keycard"),
            new ResourceLocation("scpfr", "keycard_o5"),
            new ResourceLocation("scpfr", "05_keycard"),
            new ResourceLocation("scpfr", "keycard_05"),
            new ResourceLocation("scpfr", "level_5_keycard"),
            new ResourceLocation("scpfr", "keycard_level_5"),
            new ResourceLocation("scpfr", "omni_keycard")
    };

    /**
     * Resolves a high-clearance card at runtime instead of assuming one historical SCPFR item ID.
     * SCPFR has changed keycard IDs between releases; a server owner can also set the exact ID in
     * {@code remote_access.remoteAccessKeycardId}. If that item is absent, a conservative scan for
     * SCPFR O5/level-5/omni cards keeps existing facility maps working after an SCPFR update.
     */
    public static ItemStack getO5KeycardStack() {
        if (Scp079Config.COMMON != null) {
            String configuredId = Scp079Config.COMMON.remoteAccessKeycardId.get();
            if (configuredId != null && !configuredId.isBlank()) {
                ResourceLocation configuredKey = ResourceLocation.tryParse(configuredId.trim());
                ItemStack configuredCard = getRegisteredCard(configuredKey);
                if (!configuredCard.isEmpty()) {
                    return configuredCard;
                }
            }
        }

        for (ResourceLocation candidate : PREFERRED_REMOTE_ACCESS_CARDS) {
            ItemStack card = getRegisteredCard(candidate);
            if (!card.isEmpty()) {
                return card;
            }
        }

        ResourceLocation bestMatch = null;
        int bestScore = 0;
        for (ResourceLocation key : ForgeRegistries.ITEMS.getKeys()) {
            if (!"scpfr".equalsIgnoreCase(key.getNamespace())) {
                continue;
            }
            int score = highClearanceCardScore(key.getPath().toLowerCase());
            if (score > bestScore) {
                bestScore = score;
                bestMatch = key;
            }
        }
        return getRegisteredCard(bestMatch);
    }

    private static ItemStack getRegisteredCard(ResourceLocation key) {
        if (key == null || !ForgeRegistries.ITEMS.containsKey(key)) {
            return ItemStack.EMPTY;
        }
        Item item = ForgeRegistries.ITEMS.getValue(key);
        return item == null ? ItemStack.EMPTY : new ItemStack(item);
    }

    private static int highClearanceCardScore(String path) {
        boolean cardLike = path.contains("card") || path.contains("key");
        if (!cardLike) {
            return 0;
        }
        if (path.contains("o_5") || path.contains("o5") || path.contains("05")) {
            return 100;
        }
        if (path.contains("level_5") || path.contains("level5") || path.contains("lvl_5") || path.contains("lvl5")) {
            return 90;
        }
        if (path.contains("omni") || path.contains("administrator")) {
            return 80;
        }
        if (path.contains("keycard_5") || path.contains("key_card_5") || path.contains("card_5")) {
            return 70;
        }
        return 0;
    }

    public static boolean isReaderOrScannerBlock(BlockState state) {
        if (state == null || state.isAir()) return false;
        var key = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if (key != null) {
            String path = key.getPath().toLowerCase();
            String ns = key.getNamespace().toLowerCase();
            if (path.contains("reader") || path.contains("scanner") || path.contains("keycard") ||
                path.contains("keypad") || path.contains("swipe") || path.contains("card_reader")) {
                return true;
            }
            if ((ns.contains("scp") || ns.contains("facility")) && (path.contains("keycard_reader") || path.contains("reader"))) {
                return true;
            }
        }
        return false;
    }

    public static boolean isDoorLikeBlock(BlockState state) {
        if (state == null || state.isAir()) return false;
        var block = state.getBlock();
        if (block instanceof DoorBlock || block instanceof TrapDoorBlock || block instanceof FenceGateBlock ||
            block instanceof ButtonBlock || block instanceof LeverBlock) {
            return true;
        }

        var key = ForgeRegistries.BLOCKS.getKey(block);
        if (key != null) {
            String path = key.getPath().toLowerCase();
            String ns = key.getNamespace().toLowerCase();
            if (path.contains("door") || path.contains("gate") || path.contains("blast") ||
                path.contains("airlock") || path.contains("checkpoint") || path.contains("cell") ||
                path.contains("shutter") || path.contains("hatch") || path.contains("panel") ||
                path.contains("reader") || path.contains("button") || path.contains("keycard") ||
                path.contains("keypad") || path.contains("scanner") || path.contains("sliding") ||
                path.contains("bulkhead") || path.contains("containment") || path.contains("decontamination") ||
                path.contains("iris") || path.contains("vault")) {
                return true;
            }
            if (ns.contains("scp") || ns.contains("facility") || ns.contains("foundation") ||
                ns.contains("scpthefacility") || ns.contains("thefacility")) {
                if (path.contains("elevator") || path.contains("transit") || path.contains("intercom") ||
                    path.contains("speaker") || path.contains("lock") || path.contains("terminal")) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean isTeslaGateBlock(BlockState state) {
        if (state == null || state.isAir()) return false;
        var block = state.getBlock();
        var key = ForgeRegistries.BLOCKS.getKey(block);
        if (key != null) {
            String path = key.getPath().toLowerCase();
            String ns = key.getNamespace().toLowerCase();
            if (path.contains("tesla") || path.contains("shock_gate") || path.contains("electric_gate") ||
                path.contains("shock_barrier") || path.contains("tesla_gate") || path.contains("tesla_coil")) {
                return true;
            }
            if ((ns.contains("scp") || ns.contains("facility")) && path.contains("tesla")) {
                return true;
            }
        }
        String className = block.getClass().getSimpleName().toLowerCase();
        return className.contains("tesla");
    }

    public static void triggerTeslaGate(Level level, BlockPos pos, Player player) {
        if (level == null || pos == null) return;

        BlockPos centerPos = pos;
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos p = pos.offset(dx, dy, dz);
                    BlockState st = level.getBlockState(p);
                    if (isTeslaGateBlock(st)) {
                        centerPos = p;
                        break;
                    }
                }
            }
        }

        if (level instanceof ServerLevel serverLevel) {
            ArmorStand dummy = new ArmorStand(serverLevel, centerPos.getX() + 0.5, centerPos.getY() + 0.1, centerPos.getZ() + 0.5);
            dummy.setInvisible(true);
            dummy.setNoGravity(true);
            dummy.setInvulnerable(true);
            dummy.setSilent(true);
            dummy.addTag("scp079_tesla_dummy");
            serverLevel.addFreshEntity(dummy);
        }
    }

    public static boolean toggleDoor(Level level, BlockPos pos, Player player) {
        return toggleDoorState(level, pos, player);
    }

    public static boolean toggleDoorState(Level level, BlockPos pos, Player player) {
        if (level == null || pos == null) return false;
        BlockState state = level.getBlockState(pos);

        ServerLevel serverLevel = level instanceof ServerLevel server ? server : null;
        ServerPlayer remotePlayer = player instanceof ServerPlayer serverPlayer ? serverPlayer : null;
        FakePlayer fakePlayer = createRemoteAccessPlayer(serverLevel, pos);
        ItemStack accessCard = getO5KeycardStack();

        ItemStack previousHand = player != null ? player.getItemInHand(InteractionHand.MAIN_HAND) : ItemStack.EMPTY;
        if (player != null && !accessCard.isEmpty()) {
            player.setItemInHand(InteractionHand.MAIN_HAND, accessCard.copy());
        }

        try {
            // Readers and secured doors must report success only after a card interaction actually
            // consumes an action. The previous implementation treated merely finding a nearby
            // reader as success, which spent AP and logged "Door toggled" without granting access.
            if (!accessCard.isEmpty() && serverLevel != null) {
                if (tryRemoteCardAccess(serverLevel, pos, fakePlayer, remotePlayer, accessCard)) {
                    notifyNeighbors(level, pos);
                    return true;
                }

                // A scanner is commonly mounted next to (rather than inside) the actual door.
                for (int dy = -1; dy <= 2; dy++) {
                    for (int dx = -2; dx <= 2; dx++) {
                        for (int dz = -2; dz <= 2; dz++) {
                            if (dx == 0 && dy == 0 && dz == 0) continue;
                            BlockPos readerPos = pos.offset(dx, dy, dz);
                            if (!isReaderOrScannerBlock(level.getBlockState(readerPos))) continue;
                            if (tryRemoteCardAccess(serverLevel, readerPos, fakePlayer, remotePlayer, accessCard)) {
                                notifyNeighbors(level, readerPos);
                                notifyNeighbors(level, pos);
                                return true;
                            }
                        }
                    }
                }
            }

            // Standard door toggles (Vanilla doors, trapdoors, fencegates, buttons, levers)
            if (state.getBlock() instanceof DoorBlock) {
                BlockPos lowerPos = state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
                BlockPos upperPos = state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos;
                BlockState lowerState = level.getBlockState(lowerPos);
                BlockState upperState = level.getBlockState(upperPos);

                boolean wasOpen = lowerState.hasProperty(DoorBlock.OPEN) && lowerState.getValue(DoorBlock.OPEN);
                boolean newState = !wasOpen;

                if (lowerState.getBlock() instanceof DoorBlock) {
                    level.setBlock(lowerPos, lowerState.setValue(DoorBlock.OPEN, newState), 3);
                }
                if (upperState.getBlock() instanceof DoorBlock) {
                    level.setBlock(upperPos, upperState.setValue(DoorBlock.OPEN, newState), 3);
                }
                level.playSound(null, pos, newState ? SoundEvents.IRON_DOOR_OPEN : SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 1.0f, 1.0f);
                notifyNeighbors(level, lowerPos);
                notifyNeighbors(level, upperPos);
                return true;
            }

            if (state.getBlock() instanceof TrapDoorBlock) {
                boolean wasOpen = state.getValue(TrapDoorBlock.OPEN);
                boolean newState = !wasOpen;
                level.setBlock(pos, state.setValue(TrapDoorBlock.OPEN, newState), 3);
                level.playSound(null, pos, newState ? SoundEvents.IRON_TRAPDOOR_OPEN : SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 1.0f, 1.0f);
                notifyNeighbors(level, pos);
                return true;
            }

            if (state.getBlock() instanceof FenceGateBlock) {
                boolean wasOpen = state.getValue(FenceGateBlock.OPEN);
                boolean newState = !wasOpen;
                level.setBlock(pos, state.setValue(FenceGateBlock.OPEN, newState), 3);
                level.playSound(null, pos, newState ? SoundEvents.FENCE_GATE_OPEN : SoundEvents.FENCE_GATE_CLOSE, SoundSource.BLOCKS, 1.0f, 1.0f);
                notifyNeighbors(level, pos);
                return true;
            }

            if (state.getBlock() instanceof ButtonBlock button) {
                button.press(state, level, pos);
                level.playSound(null, pos, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.BLOCKS, 1.0f, 1.0f);
                notifyNeighbors(level, pos);
                return true;
            }

            if (state.getBlock() instanceof LeverBlock) {
                boolean wasPowered = state.getValue(LeverBlock.POWERED);
                level.setBlock(pos, state.setValue(LeverBlock.POWERED, !wasPowered), 3);
                level.playSound(null, pos, SoundEvents.LEVER_CLICK, SoundSource.BLOCKS, 1.0f, wasPowered ? 0.5f : 0.6f);
                notifyNeighbors(level, pos);
                return true;
            }

            boolean handled = triggerModdedDoorToggle(level, pos, fakePlayer != null ? fakePlayer : player);
            if (handled) {
                notifyNeighbors(level, pos);
                return true;
            }

            BlockState toggled = toggleBooleanProperty(state, "open");
            if (toggled != state) {
                level.setBlock(pos, toggled, 3);
                notifyNeighbors(level, pos);
                return true;
            }

            // Unsupported blocks are failures, not successful remote card swipes. This lets the
            // packet handler refund AP and avoids misleading success entries in the SCP-079 log.
            return false;
        } finally {
            if (player != null) {
                player.setItemInHand(InteractionHand.MAIN_HAND, previousHand);
            }
        }
    }

    private static FakePlayer createRemoteAccessPlayer(ServerLevel level, BlockPos pos) {
        if (level == null) {
            return null;
        }
        FakePlayer fakePlayer = FakePlayerFactory.getMinecraft(level);
        fakePlayer.setGameMode(GameType.SURVIVAL);
        fakePlayer.setPos(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        return fakePlayer;
    }

    /**
     * Performs a real server-side right-click with a high-clearance card. Every face is attempted
     * because SCP facility readers may expose their usable face on either side of a door.
     */
    private static boolean tryRemoteCardAccess(ServerLevel level, BlockPos targetPos, FakePlayer fakePlayer,
                                               ServerPlayer remotePlayer, ItemStack accessCard) {
        for (Direction face : Direction.values()) {
            BlockHitResult hit = new BlockHitResult(
                    new Vec3(targetPos.getX() + 0.5, targetPos.getY() + 0.5, targetPos.getZ() + 0.5),
                    face, targetPos, false);

            if (fakePlayer != null) {
                prepareFakePlayerForCardSwipe(fakePlayer, targetPos, face, accessCard);
                InteractionResult result = fakePlayer.gameMode.useItemOn(
                        fakePlayer, level, fakePlayer.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
                if (result.consumesAction()) {
                    return true;
                }
            }

            // A few modded readers reject generic FakePlayers. Their block/item callbacks are
            // still safe to invoke with the actual SCP-079 player; the card is only equipped for
            // this call and is restored by toggleDoorState afterwards.
            if (remotePlayer != null) {
                ItemStack remoteCard = remotePlayer.getMainHandItem();
                if (!remoteCard.isEmpty()) {
                    InteractionResult itemResult = remoteCard.useOn(
                            new UseOnContext(remotePlayer, InteractionHand.MAIN_HAND, hit));
                    if (itemResult.consumesAction()) {
                        return true;
                    }
                }
                InteractionResult blockResult = level.getBlockState(targetPos).use(
                        level, remotePlayer, InteractionHand.MAIN_HAND, hit);
                if (blockResult.consumesAction()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void prepareFakePlayerForCardSwipe(FakePlayer fakePlayer, BlockPos targetPos,
                                                       Direction face, ItemStack accessCard) {
        BlockPos standingPos = targetPos.relative(face.getOpposite());
        fakePlayer.setPos(standingPos.getX() + 0.5, standingPos.getY(), standingPos.getZ() + 0.5);
        fakePlayer.getInventory().selected = 0;
        fakePlayer.getInventory().setItem(0, accessCard.copy());
        fakePlayer.setItemInHand(InteractionHand.MAIN_HAND, fakePlayer.getInventory().getItem(0));
        fakePlayer.setItemInHand(InteractionHand.OFF_HAND, accessCard.copy());
    }

    public static void setAreaLockState(Level level, BlockPos centerPos, boolean lock, Player player) {
        if (level == null || centerPos == null) return;

        for (int dy = -1; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    BlockPos p = centerPos.offset(dx, dy, dz);
                    swapButtonLockState(level, p, lock);
                }
            }
        }

        if (lock) {
            closeDoor(level, centerPos, player);
        }
        notifyNeighbors(level, centerPos);
    }

    private static void swapButtonLockState(Level level, BlockPos pos, boolean lock) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir()) return;

        BlockState withLockedProp = setBooleanProperty(state, "locked", lock);
        if (withLockedProp != state) {
            level.setBlock(pos, withLockedProp, 3);
            notifyNeighbors(level, pos);
            return;
        }

        ResourceLocation key = ForgeRegistries.BLOCKS.getKey(state.getBlock());
        if (key == null) return;

        String ns = key.getNamespace();
        String path = key.getPath();

        ResourceLocation targetKey = null;
        if (lock) {
            if (!path.contains("locked")) {
                ResourceLocation try1 = new ResourceLocation(ns, path + "_locked");
                if (ForgeRegistries.BLOCKS.containsKey(try1)) {
                    targetKey = try1;
                } else {
                    ResourceLocation try2 = new ResourceLocation(ns, "locked_" + path);
                    if (ForgeRegistries.BLOCKS.containsKey(try2)) {
                        targetKey = try2;
                    }
                }
            }
        } else {
            if (path.endsWith("_locked")) {
                String base = path.substring(0, path.length() - "_locked".length());
                ResourceLocation try1 = new ResourceLocation(ns, base);
                if (ForgeRegistries.BLOCKS.containsKey(try1)) {
                    targetKey = try1;
                }
            } else if (path.startsWith("locked_")) {
                String base = path.substring("locked_".length());
                ResourceLocation try1 = new ResourceLocation(ns, base);
                if (ForgeRegistries.BLOCKS.containsKey(try1)) {
                    targetKey = try1;
                }
            }
        }

        if (targetKey != null) {
            Block newBlock = ForgeRegistries.BLOCKS.getValue(targetKey);
            if (newBlock != null) {
                BlockState newState = newBlock.defaultBlockState();
                for (Property<?> prop : state.getProperties()) {
                    newState = copyProperty(state, newState, prop);
                }
                level.setBlock(pos, newState, 3);
                notifyNeighbors(level, pos);
            }
        }

        BlockEntity be = level.getBlockEntity(pos);
        if (be != null) {
            if (lock) {
                invokeMethod(be, "lock");
                invokeMethod(be, "setLocked", boolean.class, true);
                invokeMethod(be, "setIsLocked", boolean.class, true);
            } else {
                invokeMethod(be, "unlock");
                invokeMethod(be, "setLocked", boolean.class, false);
                invokeMethod(be, "setIsLocked", boolean.class, false);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> BlockState copyProperty(BlockState from, BlockState to, Property<T> prop) {
        if (to.hasProperty(prop)) {
            return to.setValue(prop, from.getValue(prop));
        }
        return to;
    }

    public static boolean closeDoor(Level level, BlockPos pos, Player player) {
        if (level == null || pos == null) return false;
        BlockState state = level.getBlockState(pos);

        if (state.getBlock() instanceof DoorBlock) {
            BlockPos lowerPos = state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
            BlockPos upperPos = state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos;
            BlockState lowerState = level.getBlockState(lowerPos);
            BlockState upperState = level.getBlockState(upperPos);

            if (lowerState.getBlock() instanceof DoorBlock) {
                level.setBlock(lowerPos, lowerState.setValue(DoorBlock.OPEN, false), 3);
            }
            if (upperState.getBlock() instanceof DoorBlock) {
                level.setBlock(upperPos, upperState.setValue(DoorBlock.OPEN, false), 3);
            }
            level.playSound(null, pos, SoundEvents.IRON_DOOR_CLOSE, SoundSource.BLOCKS, 1.0f, 1.0f);
            notifyNeighbors(level, lowerPos);
            notifyNeighbors(level, upperPos);
            return true;
        }

        if (state.getBlock() instanceof TrapDoorBlock) {
            level.setBlock(pos, state.setValue(TrapDoorBlock.OPEN, false), 3);
            level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 1.0f, 1.0f);
            notifyNeighbors(level, pos);
            return true;
        }

        if (state.getBlock() instanceof FenceGateBlock) {
            level.setBlock(pos, state.setValue(FenceGateBlock.OPEN, false), 3);
            level.playSound(null, pos, SoundEvents.FENCE_GATE_CLOSE, SoundSource.BLOCKS, 1.0f, 1.0f);
            notifyNeighbors(level, pos);
            return true;
        }

        triggerModdedDoorState(level, pos, player, false);
        BlockState modified = setBooleanProperty(state, "open", false);
        if (modified != state) {
            level.setBlock(pos, modified, 3);
        }
        notifyNeighbors(level, pos);
        return true;
    }

    public static boolean openDoor(Level level, BlockPos pos, Player player) {
        if (level == null || pos == null) return false;
        BlockState state = level.getBlockState(pos);

        if (state.getBlock() instanceof DoorBlock) {
            BlockPos lowerPos = state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? pos : pos.below();
            BlockPos upperPos = state.getValue(DoorBlock.HALF) == DoubleBlockHalf.LOWER ? pos.above() : pos;
            BlockState lowerState = level.getBlockState(lowerPos);
            BlockState upperState = level.getBlockState(upperPos);

            if (lowerState.getBlock() instanceof DoorBlock) {
                level.setBlock(lowerPos, lowerState.setValue(DoorBlock.OPEN, true), 3);
            }
            if (upperState.getBlock() instanceof DoorBlock) {
                level.setBlock(upperPos, upperState.setValue(DoorBlock.OPEN, true), 3);
            }
            level.playSound(null, pos, SoundEvents.IRON_DOOR_OPEN, SoundSource.BLOCKS, 1.0f, 1.0f);
            notifyNeighbors(level, lowerPos);
            notifyNeighbors(level, upperPos);
            return true;
        }

        if (state.getBlock() instanceof TrapDoorBlock) {
            level.setBlock(pos, state.setValue(TrapDoorBlock.OPEN, true), 3);
            level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.BLOCKS, 1.0f, 1.0f);
            notifyNeighbors(level, pos);
            return true;
        }

        if (state.getBlock() instanceof FenceGateBlock) {
            level.setBlock(pos, state.setValue(FenceGateBlock.OPEN, true), 3);
            level.playSound(null, pos, SoundEvents.FENCE_GATE_OPEN, SoundSource.BLOCKS, 1.0f, 1.0f);
            notifyNeighbors(level, pos);
            return true;
        }

        triggerModdedDoorState(level, pos, player, true);
        BlockState modified = setBooleanProperty(state, "open", true);
        if (modified != state) {
            level.setBlock(pos, modified, 3);
        }
        notifyNeighbors(level, pos);
        return true;
    }

    private static boolean triggerModdedDoorToggle(Level level, BlockPos pos, Player player) {
        for (int dy = -1; dy <= 2; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos p = pos.offset(dx, dy, dz);
                    BlockEntity be = level.getBlockEntity(p);
                    if (be != null) {
                        if (invokeMethod(be, "toggle")) return true;
                        if (invokeMethod(be, "toggleDoor")) return true;
                        if (invokeMethod(be, "openDoor")) return true;
                        if (invokeMethod(be, "interact", Player.class, player)) return true;
                        if (invokeMethod(be, "onInteract", Player.class, player)) return true;
                        if (invokeMethod(be, "trigger")) return true;
                        if (invokeMethod(be, "activate")) return true;
                        if (invokeMethod(be, "use", Player.class, player)) return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean triggerModdedDoorState(Level level, BlockPos pos, Player player, boolean open) {
        for (int dy = -1; dy <= 2; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos p = pos.offset(dx, dy, dz);
                    BlockEntity be = level.getBlockEntity(p);
                    if (be != null) {
                        if (invokeMethod(be, open ? "open" : "close")) return true;
                        if (invokeMethod(be, open ? "openDoor" : "closeDoor")) return true;
                        if (invokeMethod(be, "setOpen", boolean.class, open)) return true;
                        if (invokeMethod(be, "setOpened", boolean.class, open)) return true;
                        if (invokeMethod(be, "setPowered", boolean.class, open)) return true;
                    }
                }
            }
        }
        return false;
    }

    private static boolean invokeMethod(Object target, String name) {
        try {
            for (Method m : target.getClass().getMethods()) {
                if (m.getName().equalsIgnoreCase(name) && m.getParameterCount() == 0) {
                    m.invoke(target);
                    return true;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    private static boolean invokeMethod(Object target, String name, Class<?> paramType, Object arg) {
        try {
            for (Method m : target.getClass().getMethods()) {
                if (m.getName().equalsIgnoreCase(name) && m.getParameterCount() == 1) {
                    if (arg == null || m.getParameterTypes()[0].isAssignableFrom(arg.getClass()) ||
                       (paramType == boolean.class && m.getParameterTypes()[0] == Boolean.TYPE)) {
                        m.invoke(target, arg);
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    private static BlockState setBooleanProperty(BlockState state, String propertyNamePrefix, boolean value) {
        for (Property<?> prop : state.getProperties()) {
            if (prop instanceof BooleanProperty bp) {
                if (bp.getName().toLowerCase().startsWith(propertyNamePrefix.toLowerCase()) ||
                    bp.getName().toLowerCase().contains(propertyNamePrefix.toLowerCase())) {
                    try {
                        return state.setValue(bp, value);
                    } catch (Exception ignored) {}
                }
            }
        }
        return state;
    }

    private static BlockState toggleBooleanProperty(BlockState state, String propertyNamePrefix) {
        for (Property<?> prop : state.getProperties()) {
            if (prop instanceof BooleanProperty bp) {
                if (bp.getName().toLowerCase().startsWith(propertyNamePrefix.toLowerCase()) ||
                    bp.getName().toLowerCase().contains(propertyNamePrefix.toLowerCase())) {
                    try {
                        return state.cycle(bp);
                    } catch (Exception ignored) {}
                }
            }
        }
        return state;
    }

    public static void notifyNeighbors(Level level, BlockPos pos) {
        if (level == null || pos == null) return;
        BlockState state = level.getBlockState(pos);
        level.updateNeighborsAt(pos, state.getBlock());
        for (Direction dir : Direction.values()) {
            level.updateNeighborsAt(pos.relative(dir), state.getBlock());
        }
    }
}
