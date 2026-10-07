package dev.sirblambo.impactful.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.sirblambo.impactful.config.ModConfig;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PresenceClient {

    private static final String PRESENCE_URL = "https://impactful-presence.sirblambo.workers.dev";

    private static final long HEARTBEAT_MS        = 10 * 60_000L;
    private static final long FAST_WINDOW_MS      = 30_000L;
    private static final long FAST_GAP_MS         = 1_000L;
    private static final long SLOW_GAP_MS         = 60_000L;
    private static final long NEWCOMER_DELAY_MS   = 5_000L;
    private static final long FRESH_WINDOW_MS     = 30_000L;
    private static final long NEGATIVE_RECHECK_MS = 30 * 60_000L;
    private static final long FAILURE_BACKOFF_MS  = 10_000L;
    private static final int  MAX_PLAYERS = 500;
    private static final int    CHAT_NAME_WINDOW = 64;
    private static final String CHAT_SEPARATORS  = ":>\u00BB";
    private static final String CHAT_NAME_TRAIL  = "]) ";

    private static final Text ICON = Text.literal("\uDBFA\uDDA0\uDBFA\uDDA1").setStyle(Style.EMPTY
            .withColor(0xFFFFFF)
            .withoutShadow());

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private static final Set<UUID> active  = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Long> checkedAt = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> firstSeen = new HashMap<>();
    private static volatile boolean inFlight = false;
    private static volatile long lastFailureMs = 0L;
    private static long lastRequestMs = 0L;
    private static Object lastWorld = null;
    private static long fastUntilMs = 0L;

    private static boolean configured() {
        return !PRESENCE_URL.isEmpty();
    }

    public static void tick(MinecraftClient client) {
        if (!configured() || !ModConfig.get().showImpactfulUsers) return;
        ClientPlayNetworkHandler handler = client.getNetworkHandler();
        if (client.player == null || handler == null) return;

        long now = System.currentTimeMillis();
        if (client.world != lastWorld) {
            lastWorld = client.world;
            fastUntilMs = now + FAST_WINDOW_MS;
        }
        if (inFlight || now - lastFailureMs < FAILURE_BACKOFF_MS) return;
        long since = now - lastRequestMs;
        if (since < FAST_GAP_MS) return;
        boolean fast = now < fastUntilMs;

        List<UUID> urgent = new ArrayList<>();
        List<UUID> stale = new ArrayList<>();
        Set<UUID> present = new HashSet<>();
        for (PlayerListEntry entry : handler.getPlayerList()) {
            UUID id = entry.getProfile().id();
            present.add(id);
            long age = now - firstSeen.computeIfAbsent(id, k -> now);
            if (active.contains(id)) continue;
            Long checked = checkedAt.get(id);
            if (checked == null) {
                if (fast || age >= NEWCOMER_DELAY_MS) urgent.add(id);
            } else if (fast && age < FRESH_WINDOW_MS && now - checked >= Math.max(FAST_GAP_MS, age / 2)) {
                urgent.add(id);
            } else if (now - checked >= NEGATIVE_RECHECK_MS) {
                stale.add(id);
            }
        }
        firstSeen.keySet().retainAll(present);
        checkedAt.keySet().retainAll(present);
        active.retainAll(present);

        boolean due = (!urgent.isEmpty() && (fast || since >= SLOW_GAP_MS)) || since >= HEARTBEAT_MS;
        if (!due) return;

        List<UUID> players = new ArrayList<>(urgent);
        for (UUID id : stale) {
            if (players.size() >= MAX_PLAYERS) break;
            players.add(id);
        }
        if (players.size() > MAX_PLAYERS) players = new ArrayList<>(players.subList(0, MAX_PLAYERS));

        lastRequestMs = now;
        send(client.player.getUuid(), players);
    }

    private static void send(UUID self, List<UUID> players) {
        JsonObject body = new JsonObject();
        body.addProperty("self_id", self.toString());
        JsonArray ids = new JsonArray();
        for (UUID id : players) ids.add(id.toString());
        body.add("player_ids", ids);

        HttpRequest request = HttpRequest.newBuilder(URI.create(PRESENCE_URL + "/presence"))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("User-Agent", "Impactful/" + modVersion())
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        inFlight = true;
        HTTP.sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete((response, error) -> {
            try {
                if (error != null || response.statusCode() / 100 != 2) {
                    lastFailureMs = System.currentTimeMillis();
                    return;
                }
                for (JsonElement e : JsonParser.parseString(response.body()).getAsJsonArray()) {
                    active.add(UUID.fromString(e.getAsString()));
                }
            } catch (Exception ignored) {
                lastFailureMs = System.currentTimeMillis();
            } finally {
                long done = System.currentTimeMillis();
                for (UUID id : players) checkedAt.put(id, done);
                inFlight = false;
            }
        });
    }

    public static boolean isImpactfulUser(UUID id) {
        if (id == null || !ModConfig.get().showImpactfulUsers) return false;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null && id.equals(client.player.getUuid())) return true;
        return active.contains(id);
    }

    public static Text decorate(Text name, UUID id) {
        if (name == null || !isImpactfulUser(id)) return name;
        MutableText out = name.copy();
        out.append(ICON);
        return out;
    }

    public static Text decorateChat(Text message) {
        if (message == null || !ModConfig.get().showImpactfulUsers) return message;
        List<String> names = impactfulNames();
        if (names.isEmpty()) return message;
        int insertAt = findChatInsert(message.getString(), names);
        if (insertAt < 0) return message;

        MutableText out = Text.empty();
        int[] pos = {0};
        boolean[] inserted = {false};
        message.visit((style, str) -> {
            int start = pos[0];
            int end = start + str.length();
            if (!inserted[0] && insertAt >= start && insertAt <= end) {
                out.append(Text.literal(str.substring(0, insertAt - start)).setStyle(style));
                out.append(ICON);
                out.append(Text.literal(" ").setStyle(style));
                out.append(Text.literal(str.substring(insertAt - start)).setStyle(style));
                inserted[0] = true;
            } else {
                out.append(Text.literal(str).setStyle(style));
            }
            pos[0] = end;
            return Optional.empty();
        }, Style.EMPTY);
        return inserted[0] ? out : message;
    }

    private static List<String> impactfulNames() {
        List<String> names = new ArrayList<>();
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player != null) names.add(client.player.getGameProfile().name());
        ClientPlayNetworkHandler handler = client.getNetworkHandler();
        if (handler == null) return names;
        for (PlayerListEntry entry : handler.getPlayerList()) {
            if (active.contains(entry.getProfile().id())) names.add(entry.getProfile().name());
        }
        return names;
    }

    private static int findChatInsert(String plain, List<String> names) {
        int limit = plain.length();
        for (int k = 0; k < plain.length() - 1; k++) {
            if (CHAT_SEPARATORS.indexOf(plain.charAt(k)) >= 0 && plain.charAt(k + 1) == ' ') {
                limit = k;
                break;
            }
        }
        int bestStart = Integer.MAX_VALUE;
        int bestInsert = -1;
        for (String name : names) {
            if (name == null || name.isEmpty()) continue;
            int i = plain.indexOf(name);
            while (i >= 0 && i < CHAT_NAME_WINDOW && i < bestStart && i < limit) {
                int end = i + name.length();
                boolean bounded = (i == 0 || !isNameChar(plain.charAt(i - 1)))
                        && (end == plain.length() || !isNameChar(plain.charAt(end)));
                if (bounded) {
                    int j = end;
                    while (j < plain.length() && j - end < 3 && CHAT_NAME_TRAIL.indexOf(plain.charAt(j)) >= 0
                            && CHAT_SEPARATORS.indexOf(plain.charAt(j)) < 0) j++;
                    if (j < plain.length() && CHAT_SEPARATORS.indexOf(plain.charAt(j)) >= 0) {
                        int insert = j + 1;
                        if (insert < plain.length() && plain.charAt(insert) == ' ') insert++;
                        bestStart = i;
                        bestInsert = insert;
                        break;
                    }
                }
                i = plain.indexOf(name, i + 1);
            }
        }
        return bestInsert;
    }

    private static boolean isNameChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    public static void reset() {
        active.clear();
        checkedAt.clear();
        firstSeen.clear();
        lastRequestMs = 0L;
        lastFailureMs = 0L;
        lastWorld = null;
        fastUntilMs = 0L;
    }

    private static String modVersion() {
        return FabricLoader.getInstance().getModContainer("impactful")
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }
}
