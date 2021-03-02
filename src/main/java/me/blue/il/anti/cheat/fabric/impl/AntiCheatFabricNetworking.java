package me.blue.il.anti.cheat.fabric.impl;

import me.blue.il.anti.cheat.fabric.mixin.GameProfileAccessor;
import io.netty.buffer.Unpooled;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientLoginNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PacketSender;
import net.fabricmc.fabric.api.networking.v1.ServerLoginConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerLoginNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.metadata.ModMetadata;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientLoginNetworkHandler;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerLoginNetworkHandler;
import net.minecraft.text.LiteralText;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableText;
import net.minecraft.util.Identifier;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class AntiCheatFabricNetworking implements ModInitializer, ClientModInitializer {
    private static final Identifier MOD_VALIDATION_CHANNEL = AntiCheatFabric.id("channel", "mod_validation");
    private static final Text REQUEST_NOT_UNDERSTOOD = new LiteralText("Please install the Anti Cheat Fabric mod to play on this server.");

    @Override
    public void onInitialize() {
        ServerLoginNetworking.registerGlobalReceiver(MOD_VALIDATION_CHANNEL, AntiCheatFabricNetworking::handleResponse);
        ServerLoginConnectionEvents.QUERY_START.register(AntiCheatFabricNetworking::request);
    }

    @Override
    @Environment(EnvType.CLIENT)
    public void onInitializeClient() {
        ClientLoginNetworking.registerGlobalReceiver(MOD_VALIDATION_CHANNEL, AntiCheatFabricNetworking::response);
    }

    private static void request(ServerLoginNetworkHandler handler, MinecraftServer server, PacketSender sender, ServerLoginNetworking.LoginSynchronizer loginSynchronizer) {
        sender.sendPacket(MOD_VALIDATION_CHANNEL, PacketByteBufs.empty());
    }

    @Environment(EnvType.CLIENT)
    private static CompletableFuture<PacketByteBuf> response(MinecraftClient client, ClientLoginNetworkHandler handler, PacketByteBuf empty, Consumer<GenericFutureListener<? extends Future<? super Void>>> genericFutureListenerConsumer) {
        PacketByteBuf buf = new PacketByteBuf(Unpooled.buffer());

        Collection<ModContainer> mods = FabricLoader.getInstance().getAllMods();

        int i = 0;
        for (ModContainer container : mods) {
            ModMetadata metadata = container.getMetadata();
            if (AntiCheatFabric.isDefaultWhitelisted(metadata)) i++;
        }
        buf.writeVarInt(mods.size() - i);
        for (ModContainer container : mods) {
            ModMetadata metadata = container.getMetadata();
            if (AntiCheatFabric.isDefaultWhitelisted(metadata)) continue;
            buf.writeString(metadata.getId());
            buf.writeString(metadata.getVersion().toString());
        }

        return CompletableFuture.completedFuture(buf);
    }

    private static void handleResponse(MinecraftServer server, ServerLoginNetworkHandler handler, boolean understood, PacketByteBuf buf, ServerLoginNetworking.LoginSynchronizer loginSynchronizer, PacketSender sender) {
        if (!understood) {
            MutableText text = REQUEST_NOT_UNDERSTOOD.copy();

            text.append(DependencyUtil.getTextWithLinks(Collections.emptyMap()));

            handler.disconnect(text);
        } else {
            String playerName = ((GameProfileAccessor) handler).getProfile().getName();

            Map<String, String> mods = new HashMap<>();
            Map<String, String> unListed = new LinkedHashMap<>();

            int modCount = buf.readVarInt();

            // Read all mods from the packet buffer
            for (int i = 0; i < modCount; ++i) {
                String modId = buf.readString(32767);
                String modVersion = buf.readString(32767);

                if (!AntiCheatFabric.isWhitelisted(modId, modVersion)) {
                    unListed.put(modId, modVersion);
                }

                mods.put(modId, modVersion);
            }

            Optional<MutableText> unlistedResult = checkWhitelist(playerName, unListed);

            // Disconnect if either criteria is not met
            if (unlistedResult.isPresent()) {
                MutableText disconnectReason = new LiteralText("");
                disconnectReason.append(unlistedResult.get());
                handler.disconnect(disconnectReason);
                return;
            }

            // And finally update the players version map if they're not disconnected.
            PlayerModVersionsContainerImpl versions = (PlayerModVersionsContainerImpl) ((PlayerVersionMap) server).getModVersions(playerName);

            for (Map.Entry<String, String> mod : mods.entrySet()) {
                versions.put(mod.getKey(), mod.getValue());
            }
        }
    }

    private static Optional<MutableText> checkWhitelist(String playerName, Map<String, String> unlisted) {
        if (unlisted.isEmpty()) return Optional.empty();

        AntiCheatFabric.LOG.info("{} tried to join with unlisted mods:", playerName);

        StringBuilder builder = new StringBuilder();

        for (Map.Entry<String, String> entry : unlisted.entrySet()) {
            String modId = entry.getKey();
            String modVersion = entry.getValue();
            AntiCheatFabric.LOG.info("\t{}: {}", modId, modVersion);
            builder.append("\n").append(modId + ": " + modVersion);
        }
        builder.append("\n").append("§cPlease contact one of the administrators if you believe these mods should be whitelisted.");

        return Optional.of(new TranslatableText("message.acf.unlisted", builder.toString()));
    }
}
