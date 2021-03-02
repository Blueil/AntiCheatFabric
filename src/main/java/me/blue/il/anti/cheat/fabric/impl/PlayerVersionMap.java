package me.blue.il.anti.cheat.fabric.impl;

import me.blue.il.anti.cheat.fabric.api.PlayerModVersionsContainer;

public interface PlayerVersionMap {
    PlayerModVersionsContainer getModVersions(String playerName);
}
