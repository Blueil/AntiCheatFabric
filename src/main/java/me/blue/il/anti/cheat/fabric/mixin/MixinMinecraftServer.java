package me.blue.il.anti.cheat.fabric.mixin;

import me.blue.il.anti.cheat.fabric.api.PlayerModVersionsContainer;
import me.blue.il.anti.cheat.fabric.impl.PlayerModVersionsContainerImpl;
import me.blue.il.anti.cheat.fabric.impl.PlayerVersionMap;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

import java.util.HashMap;
import java.util.Map;

@Mixin(MinecraftServer.class)
public class MixinMinecraftServer implements PlayerVersionMap {
    @Unique private final Map<String, PlayerModVersionsContainer> playerVersions = new HashMap<>();


    @Override
    public PlayerModVersionsContainer getModVersions(String playerName) {
        return playerVersions.computeIfAbsent(playerName, id -> new PlayerModVersionsContainerImpl());
    }
}