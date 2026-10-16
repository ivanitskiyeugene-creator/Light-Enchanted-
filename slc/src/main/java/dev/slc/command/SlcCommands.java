package dev.slc.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.slc.cassie.CassieVoiceEngine;
import dev.slc.role.Role;
import dev.slc.round.RoundEngine;
import dev.slc.warhead.AlphaWarheadManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;

public class SlcCommands {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("slc")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("start").executes(ctx -> { RoundEngine.forceStartRound(); return 1; }))
                .then(Commands.literal("countdown").executes(ctx -> { RoundEngine.startRoundCountdown(15); return 1; }))
                .then(Commands.literal("cassie").then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(ctx -> { CassieVoiceEngine.broadcast(StringArgumentType.getString(ctx, "message"), 0x3498DB); return 1; })))
                .then(Commands.literal("warhead")
                        .then(Commands.literal("start").executes(ctx -> { AlphaWarheadManager.startDetonation(); return 1; }))
                        .then(Commands.literal("cancel").executes(ctx -> { AlphaWarheadManager.cancelDetonation(); return 1; })))
                .then(Commands.literal("role")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("roleName", StringArgumentType.word()).executes(ctx -> {
                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                    try {
                                        RoundEngine.forceRoleAndSpawn(target, Role.valueOf(StringArgumentType.getString(ctx, "roleName").toUpperCase()));
                                        return 1;
                                    } catch (IllegalArgumentException ignored) { return 0; }
                                }))))

        );
    }
}
