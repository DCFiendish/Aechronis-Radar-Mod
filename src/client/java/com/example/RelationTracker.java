package com.example;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class RelationTracker {

    public static final int NO_NATION = 0;
    public static final int NATION    = 1;
    public static final int ALLY      = 2;
    public static final int NEUTRAL   = 3;
    public static final int ENEMY     = 4;

    private static final String TOWNS_URL = "https://map.aechronis.net/nodes/towns.json";

    private static volatile JsonObject cachedResidents = null;
    private static volatile JsonObject cachedTowns = null;
    private static volatile String myTownName = null;
    private static volatile Set<String> alliedTowns = new HashSet<>();
    private static volatile Set<String> enemyTowns = new HashSet<>();
    private static volatile Set<String> nationTowns = new HashSet<>();
    private static volatile boolean myDataComputed = false;

    private static final ConcurrentHashMap<UUID, Integer> cache = new ConcurrentHashMap<>();

    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "AechronisRadar-Fetcher");
        t.setDaemon(true);
        return t;
    });

    public static void start() {
        System.out.println("[AechronisRadar] Starting fetcher...");
        scheduler.scheduleAtFixedRate(RelationTracker::fetchTowns, 0, 60, TimeUnit.SECONDS);
    }

    private static void fetchTowns() {
        try {
            System.out.println("[AechronisRadar] Fetching towns.json...");
            String json = fetch(TOWNS_URL);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            cachedResidents = root.getAsJsonObject("residents");
            cachedTowns = root.getAsJsonObject("towns");
            myDataComputed = false;
            cache.clear();
            System.out.println("[AechronisRadar] Fetched! residents=" + cachedResidents.size() + " towns=" + cachedTowns.size());
        } catch (Exception ex) {
            System.out.println("[AechronisRadar] Fetch error: " + ex.getMessage());
        }
    }

    private static void computeMyData(String myUuid) {
        if (cachedResidents == null || cachedTowns == null) return;
        String foundTown = null;
        String foundNation = null;
        if (cachedResidents.has(myUuid)) {
            JsonObject me = cachedResidents.getAsJsonObject(myUuid);
            if (me.has("town") && !me.get("town").isJsonNull()) foundTown = me.get("town").getAsString();
            if (me.has("nation") && !me.get("nation").isJsonNull()) foundNation = me.get("nation").getAsString();
        }
        myTownName = foundTown;
        Set<String> newAllies = new HashSet<>();
        Set<String> newEnemies = new HashSet<>();
        Set<String> newNationTowns = new HashSet<>();
        if (foundTown != null && cachedTowns.has(foundTown)) {
            JsonObject myTown = cachedTowns.getAsJsonObject(foundTown);
            if (myTown.has("allies")) for (JsonElement e : myTown.getAsJsonArray("allies")) newAllies.add(e.getAsString());
            if (myTown.has("enemies")) for (JsonElement e : myTown.getAsJsonArray("enemies")) newEnemies.add(e.getAsString());
        }
        if (foundNation != null) {
            for (var entry : cachedTowns.entrySet()) {
                JsonObject town = entry.getValue().getAsJsonObject();
                if (!town.has("residents")) continue;
                for (JsonElement resUuid : town.getAsJsonArray("residents")) {
                    String uid = resUuid.getAsString();
                    if (cachedResidents.has(uid)) {
                        JsonObject res = cachedResidents.getAsJsonObject(uid);
                        if (res.has("nation") && !res.get("nation").isJsonNull()
                                && res.get("nation").getAsString().equals(foundNation)) {
                            newNationTowns.add(entry.getKey());
                            break;
                        }
                    }
                }
            }
        }
        alliedTowns = newAllies;
        enemyTowns = newEnemies;
        nationTowns = newNationTowns;
        myDataComputed = true;
        cache.clear();
        System.out.println("[AechronisRadar] My data: town=" + foundTown + " nation=" + foundNation
                + " allies=" + newAllies.size() + " enemies=" + newEnemies.size() + " nationTowns=" + newNationTowns.size());
    }

    public static int getRelation(Player player) {
        if (!myDataComputed) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) computeMyData(mc.player.getUUID().toString());
        }
        if (myTownName == null) return NO_NATION;
        return cache.computeIfAbsent(player.getUUID(), u -> computeRelation(player));
    }

    private static int computeRelation(Player player) {
        String playerTown = null;
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                var team = mc.player.getScoreboard().getPlayersTeam(player.getGameProfile().getName());
                if (team != null) {
                    String prefix = team.getPlayerPrefix().getString().trim();
                    if (prefix.startsWith("[") && prefix.contains("]")) {
                        playerTown = prefix.substring(1, prefix.indexOf("]")).trim();
                    }
                }
            }
        } catch (Exception ignored) {}

        if (playerTown == null) return NO_NATION;
        System.out.println("[AechronisRadar] " + player.getGameProfile().getName() + " town=" + playerTown);
        if (playerTown.equals(myTownName)) return NATION;
        if (nationTowns.contains(playerTown)) return NATION;
        if (alliedTowns.contains(playerTown)) return ALLY;
        if (enemyTowns.contains(playerTown)) return ENEMY;
        return NEUTRAL;
    }

    public static int getColor(int relation) {
        return switch (relation) {
            case NATION  -> 0xFF55FF55;
            case ALLY    -> 0xFF5555FF;
            case NEUTRAL -> 0xFFFFAA00;
            case ENEMY   -> 0xFFFF5555;
            default      -> 0x00FFFFFF;
        };
    }

    private static String fetch(String urlStr) throws Exception {
        URL url = new URL(urlStr);
        var conn = url.openConnection();
        conn.setConnectTimeout(10000);
        conn.setReadTimeout(10000);
        conn.setRequestProperty("User-Agent", "AechronisRadar/1.0");
        try (InputStream is = conn.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) sb.append(line);
            return sb.toString();
        }
    }

    public static void clear() {
        cache.clear();
        myDataComputed = false;
    }
}
