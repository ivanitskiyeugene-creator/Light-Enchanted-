package dev.zerosevennine.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.zerosevennine.system.Scp079PlayerManager;
import dev.zerosevennine.system.Scp079Session;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class Scp079Commands {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("079")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("join")
                        .executes(ctx -> joinSelf(ctx.getSource()))
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(ctx -> joinTarget(ctx.getSource(), EntityArgument.getPlayer(ctx, "target")))))
                .then(Commands.literal("exit")
                        .executes(ctx -> exitSelf(ctx.getSource()))
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(ctx -> exitTarget(ctx.getSource(), EntityArgument.getPlayer(ctx, "target")))))
                .then(Commands.literal("ap")
                        .then(Commands.argument("amount", FloatArgumentType.floatArg(0.0f, 500.0f))
                                .executes(ctx -> setAp(ctx.getSource(), FloatArgumentType.getFloat(ctx, "amount")))))
                .then(Commands.literal("tier")
                        .then(Commands.argument("tier", IntegerArgumentType.integer(1, 5))
                                .executes(ctx -> setTier(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "tier")))))
                .then(Commands.literal("exp")
                        .then(Commands.argument("amount", IntegerArgumentType.integer(0, 10000))
                                .executes(ctx -> addExp(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "amount")))))
        );
    }

    private static int joinSelf(CommandSourceStack source) {
        if (source.getEntity() instanceof ServerPlayer player) {
            boolean success = Scp079PlayerManager.startSession(player, player.blockPosition());
            if (success) {
                source.sendSuccess(() -> Component.literal("§a[0-7-9] Successfully connected to facility surveillance network!"), true);
                return 1;
            }
        }
        return 0;
    }

    private static int joinTarget(CommandSourceStack source, ServerPlayer target) {
        boolean success = Scp079PlayerManager.startSession(target, target.blockPosition());
        if (success) {
            source.sendSuccess(() -> Component.literal("§a[0-7-9] Put " + target.getName().getString() + " into SCP-079 mode."), true);
            return 1;
        }
        return 0;
    }

    private static int exitSelf(CommandSourceStack source) {
        if (source.getEntity() instanceof ServerPlayer player) {
            Scp079PlayerManager.endSession(player);
            source.sendSuccess(() -> Component.literal("§e[0-7-9] Disconnected from surveillance network."), true);
            return 1;
        }
        return 0;
    }

    private static int exitTarget(CommandSourceStack source, ServerPlayer target) {
        Scp079PlayerManager.endSession(target);
        source.sendSuccess(() -> Component.literal("§e[0-7-9] Removed " + target.getName().getString() + " from SCP-079 mode."), true);
        return 1;
    }

    private static int setAp(CommandSourceStack source, float amount) {
        if (source.getEntity() instanceof ServerPlayer player) {
            Scp079Session session = Scp079PlayerManager.getSession(player.getUUID());
            if (session != null) {
                session.setAp(amount);
                source.sendSuccess(() -> Component.literal("§b[0-7-9] Auxiliary Power set to " + amount + " AP"), false);
                return 1;
            }
        }
        return 0;
    }

    private static int setTier(CommandSourceStack source, int tier) {
        if (source.getEntity() instanceof ServerPlayer player) {
            Scp079Session session = Scp079PlayerManager.getSession(player.getUUID());
            if (session != null) {
                session.setTier(tier);
                source.sendSuccess(() -> Component.literal("§b[0-7-9] Access Tier set to Level " + tier), false);
                return 1;
            }
        }
        return 0;
    }

    private static int addExp(CommandSourceStack source, int amount) {
        if (source.getEntity() instanceof ServerPlayer player) {
            Scp079Session session = Scp079PlayerManager.getSession(player.getUUID());
            if (session != null) {
                session.addExp(amount, player);
                source.sendSuccess(() -> Component.literal("§b[0-7-9] Added " + amount + " EXP"), false);
                return 1;
            }
        }
        return 0;
    }
}
