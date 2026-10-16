package dev.slc.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.slc.cassie.CassieVoiceEngine;
import dev.slc.init.ModItems;
import dev.slc.role.Role;
import dev.slc.round.RoundEngine;
import dev.slc.warhead.AlphaWarheadManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

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
                .then(Commands.literal("admin")
                        .requires(source -> source.hasPermission(4))
                        .then(Commands.literal("give").executes(ctx -> {
                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                            ItemStack panel = new ItemStack(ModItems.ADMIN_PANEL.get());
                            if (!player.getInventory().add(panel)) player.drop(panel, false);
                            player.inventoryMenu.broadcastChanges();
                            return 1;
                        }))
                        .then(Commands.literal("give").then(Commands.argument("player", EntityArgument.player()).executes(ctx -> {
                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                            // A panel held by a non-OP stays inert, but never hand one to a non-OP by accident.
                            if (!target.hasPermissions(4)) return 0;
                            ItemStack panel = new ItemStack(ModItems.ADMIN_PANEL.get());
                            if (!target.getInventory().add(panel)) target.drop(panel, false);
                            target.inventoryMenu.broadcastChanges();
                            return 1;
                        })))
                )
        );
    }
}
