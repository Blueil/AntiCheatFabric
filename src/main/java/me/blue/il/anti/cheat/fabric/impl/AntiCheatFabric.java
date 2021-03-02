package me.blue.il.anti.cheat.fabric.impl;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v1.CommandRegistrationCallback;
import net.fabricmc.loader.api.*;
import net.fabricmc.loader.api.metadata.ModDependency;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.PlayerManager;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.LiteralText;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Collection;
import java.util.concurrent.CompletableFuture;

public class AntiCheatFabric implements ModInitializer {
    private static final String MOD_ID = "acf";

    public static final Logger LOG = LogManager.getLogger("AntiCheatFabric");

    public static Identifier id(String path1, String... path) {
        return new Identifier(MOD_ID, path1 + (path.length > 0 ? "/" + String.join("/", path) : ""));
    }

    @Override
    public void onInitialize() {
        Config.read();

        SuggestionProvider<ServerCommandSource> conditionType = (context, builder) -> {
            builder.suggest("whitelist");

            return CompletableFuture.completedFuture(builder.build());
        };

        SuggestionProvider<ServerCommandSource> actions = (context, builder) -> {
            builder.suggest("add");
            builder.suggest("get");
            builder.suggest("list");
            builder.suggest("remove");

            return CompletableFuture.completedFuture(builder.build());
        };

        SuggestionProvider<ServerCommandSource> mods = (context, builder) -> {
            String action = context.getArgument("action", String.class);

            if (action.equalsIgnoreCase("get") || action.equalsIgnoreCase("remove")) {
                for (ModDependency dependency : Config.getWhitelistedMods()) {
                    builder.suggest(dependency.getModId());
                }
            }

            return CompletableFuture.completedFuture(builder.build());
        };

        CommandRegistrationCallback.EVENT.register(((dispatcher, dedicated) ->
                        dispatcher.register(CommandManager.literal("acf")
                                .requires(source -> source.hasPermissionLevel(4))
                                .then(RequiredArgumentBuilder.<ServerCommandSource, String>argument("condition", StringArgumentType.string())
                                        .suggests(conditionType)
                                        .then(RequiredArgumentBuilder.<ServerCommandSource, String>argument("action", StringArgumentType.string())
                                                .suggests(actions)
                                                .then(RequiredArgumentBuilder.<ServerCommandSource, String>argument("modId", StringArgumentType.string())
                                                        .suggests(mods)
                                                        .executes(AntiCheatFabric::get)
                                                        .then(RequiredArgumentBuilder.<ServerCommandSource, String>argument("versionPredicate", StringArgumentType.string())
                                                                .executes(AntiCheatFabric::addWithVersion))
                                                        .executes(AntiCheatFabric::withoutVersion))
                                                .executes(AntiCheatFabric::get)
                                        )
                                )
                        )
                )
        );
    }

    private static int get(CommandContext<ServerCommandSource> context) {
        String action = context.getArgument("action", String.class);

        if (action.equalsIgnoreCase("list")) {


            Collection<ModDependency> dependencies = Config.getWhitelistedMods();

            context.getSource().sendFeedback(new TranslatableText("command.acf.list.whitelist",
                    dependencies.size()
            ), false);

            for (ModDependency dependency : dependencies) {
                context.getSource().sendFeedback(new LiteralText("  • " + DependencyUtil.toString(dependency)), false);
            }

            return 1;
        }

        return 100;
    }

    private static int addWithVersion(CommandContext<ServerCommandSource> context) {
        return add(context, '"' + context.getArgument("versionPredicate", String.class) + '"');
    }

    private static int withoutVersion(CommandContext<ServerCommandSource> context) {
        String action = context.getArgument("action", String.class);

        if (action.equalsIgnoreCase("add")) {
            return add(context, "\"*\"");
        } else if (action.equalsIgnoreCase("remove")) {
            String modId = context.getArgument("modId", String.class);

            ModDependency removed = Config.unWhitelist(modId);

            if (removed != null) {
                context.getSource().sendFeedback(new TranslatableText("command.acf.remove.whitelist", modId), true);
            }

            MinecraftServer server = context.getSource().getMinecraftServer();
            PlayerManager playerManager = server.getPlayerManager();

            for (ServerPlayerEntity player : playerManager.getPlayerList()) {
                player.networkHandler.disconnect(new TranslatableText("message.acf.whitelist.reload"));
            }

            return 1;
        } else if (action.equalsIgnoreCase("get")) {
            String modId = context.getArgument("modId", String.class);

            ModDependency dependency = Config.getWhitelistedVersion(modId);

            context.getSource().sendFeedback(dependency == null
                    ? new TranslatableText("command.acf.not-found", modId)
                    : new LiteralText(DependencyUtil.toString(dependency)), false);

            return 1;
        }

        return 100;
    }

    private static int add(CommandContext<ServerCommandSource> context, String versionPredicate) {
        String modId = context.getArgument("modId", String.class);
        String condition = context.getArgument("condition", String.class);
        ModDependency dependency = DependencyUtil.dependency(modId, versionPredicate);

        if (dependency == null) return -1;

        if (condition.equalsIgnoreCase("whitelist")) {
            return whitelist(modId, dependency);
        }

        return 100;
    }

    private static int whitelist(String modId, ModDependency dependency) {
        Config.whitelist(modId, dependency);

        return 1;
    }

    public static boolean isWhitelisted(String modId, String modVersion) {
        try {
            ModDependency dependency = Config.getWhitelistedVersion(modId);
            return dependency != null && dependency.matches(SemanticVersion.parse(modVersion));
        } catch (VersionParsingException e) {
            return Config.getWhitelistedVersion(modId) != null;
        }
    }

    public static boolean isDefaultWhitelisted(ModMetadata metadata) {
        return metadata.getId().equals("acf") ||
                metadata.getId().equals("minecraft") ||
                metadata.getId().equals("fabric") ||
                metadata.getId().equals("fabricloader") ||
                metadata.getId().equals("java") || metadata.getCustomValue("fabric-api:module-lifecycle") != null;
    }
}
