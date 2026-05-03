package com.example;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RelationTracker {

    public static final int NO_NATION  = 0; // white  — not in a nation
    public static final int NATION     = 1; // green  — same town or same nation
    public static final int ALLY       = 2; // blue   — ally
    public static final int NEUTRAL    = 3; // orange — neutral
    public static final int ENEMY      = 4; // red    — enemy / at war

    private static final ConcurrentHashMap<UUID, Integer> relations = new ConcurrentHashMap<>();

    /** Call this every time before using a relation — keeps it fresh from scoreboard */
    public static int getRelation(Player player) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return NO_NATION;
        if (player.getUUID().equals(mc.player.getUUID())) return NO_NATION;

        Scoreboard scoreboard = mc.player.getScoreboard();
        PlayerTeam team = scoreboard.getPlayersTeam(player.getGameProfile().getName());
        if (team == null) return NO_NATION;

        String prefix = team.getPlayerPrefix().getString();
        int relation = parseRelation(prefix);
        relations.put(player.getUUID(), relation);
        return relation;
    }

    private static int parseRelation(String prefix) {
        if (prefix == null) return NO_NATION;
        for (int i = 0; i < prefix.length() - 1; i++) {
            if (prefix.charAt(i) == '\u00a7') {
                char code = prefix.charAt(i + 1);
                switch (code) {
                    case 'a': // GREEN — same town
                    case '2': // DARK_GREEN — same nation
                        return NATION;
                    case '3': // DARK_AQUA — ally
                        return ALLY;
                    case '6': // GOLD — neutral
                        return NEUTRAL;
                    case 'c': // RED — enemy
                        return ENEMY;
                    default:
                        break;
                }
            }
        }
        return NO_NATION;
    }

    /** ARGB color for each relation */
    public static int getColor(int relation) {
        return switch (relation) {
            case NATION  -> 0x9955FF55; // green,  ~60% opacity
            case ALLY    -> 0x995555FF; // blue,   ~60% opacity
            case NEUTRAL -> 0x99FFAA00; // orange, ~60% opacity
            case ENEMY   -> 0x99FF5555; // red,    ~60% opacity
            default      -> 0x00FFFFFF; // fully transparent — no nation
        };
    }

    public static void clear() {
        relations.clear();
    }
}
