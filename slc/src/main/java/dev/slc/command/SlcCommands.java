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
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class SlcCommands {
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("slc")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("start")
                    .executes(ctx -> {
                        RoundEngine.forceStartRound();
                        ctx.getSource().sendSuccess(() -> Component.literal("§a[SLC] Раунд принудительно запущен!"), true);
                        return 1;
                    }))
                .then(Commands.literal("countdown")
                    .executes(ctx -> {
                        RoundEngine.startRoundCountdown(15);
                        ctx.getSource().sendSuccess(() -> Component.literal("§e[SLC] Запущен обратный отсчет до начала раунда!"), true);
                        return 1;
                    }))
                .then(Commands.literal("cassie")
                    .then(Commands.argument("message", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            String msg = StringArgumentType.getString(ctx, "message");
                            CassieVoiceEngine.broadcast(msg, 0x3498DB);
                            return 1;
                        })))
                .then(Commands.literal("warhead")
                    .then(Commands.literal("start")
                        .executes(ctx -> {
                            AlphaWarheadManager.startDetonation();
                            ctx.getSource().sendSuccess(() -> Component.literal("§c[SLC] Запущена детонация Альфа-Боеголовки!"), true);
                            return 1;
                        }))
                    .then(Commands.literal("cancel")
                        .executes(ctx -> {
                            AlphaWarheadManager.cancelDetonation();
                            ctx.getSource().sendSuccess(() -> Component.literal("§a[SLC] Детонация Альфа-Боеголовки отменена."), true);
                            return 1;
                        })))
                .then(Commands.literal("role")
                    .then(Commands.argument("player", EntityArgument.player())
                        .then(Commands.argument("roleName", StringArgumentType.word())
                            .executes(ctx -> {
                                ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                String roleStr = StringArgumentType.getString(ctx, "roleName").toUpperCase();
                                try {
                                    Role r = Role.valueOf(roleStr);
                                    RoundEngine.setPlayerRole(target, r);
                                    ctx.getSource().sendSuccess(() -> Component.literal("§a[SLC] Игроку " + target.getScoreboardName() + " установлена роль " + r.getDisplayName()), true);
                                    return 1;
                                } catch (Exception e) {
                                    ctx.getSource().sendFailure(Component.literal("§cНеизвестная роль: " + roleStr));
                                    return 0;
                                }
                            }))))
        );
    }
}
