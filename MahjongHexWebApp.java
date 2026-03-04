
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;

public class MahjongHexWebApp {
    private static final int DEFAULT_PORT = 8080;
    private static final String HOST = "0.0.0.0";
    private static final Path STATE_FILE = Paths.get("room_state.bin");
    private static final Path WEB_FILE = Paths.get("index.html");
    private static final Object LOCK = new Object();

    private static final List<HexDef> HEXES = List.of(
            new HexDef(1, "红中大乐透", "摸到红中时，立刻再摸两张，然后扔出两张牌"),
            new HexDef(2, "不拘一格", "你可以选择不在牌堆，而是在废弃牌中摸一张牌"),
            new HexDef(3, "开摆", "你接下来三轮无法行动，第四轮时可以摸六张牌"),
            new HexDef(4, "后期专家", "当牌只剩最后一排时，你胡牌倍数翻倍"),
            new HexDef(5, "潘多拉备战席", "你有一张手牌可以当做同样点数的其他牌"),
            new HexDef(6, "福星之冕", "你可以把发财当做万能牌使用"),
            new HexDef(7, "变形重组器", "开局使用，扔一个骰子，根据点数摸几打几"),
            new HexDef(8, "恶魔契约", "开局使用，你只能通过清一色胡牌，每回合可以摸两张牌，清一色为翻四番"),
            new HexDef(9, "光明偷偷", "向一个人索要一张指定牌（非万能牌），如果没有，他下回合无法行动"),
            new HexDef(10, "行家里手", "开局使用，选择一个玩家抽取他的三张手牌并还给他三张"),
            new HexDef(11, "了解你的敌人", "选择一名玩家明牌行动"),
            new HexDef(12, "先苦后甜", "如果你凑齐东西南北风四张牌，可以一回合打掉这四张牌，并且摸三张牌，有一张可视为万能牌"),
            new HexDef(13, "拆卸器", "你可以让一个玩家的海克斯无法生效"),
            new HexDef(14, "南蛮入侵", "本回合所有人必须打出条子，否则胡牌番数-2"),
            new HexDef(15, "万箭齐发", "本回合所有人必须打出万子，否则胡牌番数-2"),
            new HexDef(16, "宝宝麻将", "你每碰一次牌，胡牌番数+2"),
            new HexDef(17, "最后储备", "当你即将死亡时，你免疫本回合的淘汰，下一次对局时，有两张牌可以当做万能牌使用并且番数乘2"),
            new HexDef(18, "最佳好友", "如果你已经听牌，有一家胡牌时他的牌中有你听的牌，则你无需付出"),
            new HexDef(19, "优柔寡断", "本回合无法行动，下个回合偷取一个人的海克斯"),
            new HexDef(20, "遥遥领先", "开局使用，你无法摸牌，转而从弃牌堆里面摸取一张"),
            new HexDef(21, "英雄登场", "当一人胡牌时，你可以用一张牌换取他所摸到的那张牌截胡"),
            new HexDef(22, "利滚利", "当你连续三把未胡牌时，下一把胡牌翻倍"),
            new HexDef(23, "清晰头脑", "如果你本局没有碰，杠胡了，本局番数+3"),
            new HexDef(24, "奋起直追", "开局使用，低于十五点筹码时，本局摸二打二"),
            new HexDef(25, "商店故障", "你可以选择一种牌（条饼万杂），直到摸到该种类牌为止"),
            new HexDef(26, "胜利宣告", "如果你在火热连胜状态，胡牌番数乘二，直到失败为止"),
            new HexDef(27, "棱彩命运", "你立刻抽取三个海克斯，从中抽取一个，其余两个作废"),
            new HexDef(28, "决斗", "你可以指定两个人两轮轮流出你指定的牌型（条饼万杂），若有人出不出来则下回合无法行动"),
            new HexDef(29, "重启任务", "开局使用，你可以选择放弃自己的牌转而重新摸取"),
            new HexDef(30, "光明水银", "使其他人的海克斯无法对你生效"),
            new HexDef(31, "休眠锻炉", "使用该海克斯之后下回合可以抽取两个海克斯"),
            new HexDef(32, "冒险举动", "开局使用，你选择明牌行动，若胡牌则番数乘三"),
            new HexDef(33, "五谷丰登", "立刻摸取四张牌，从自己开始轮流选择一张"),
            new HexDef(34, "潘多拉的盒子", "你胡牌时可以选择抽取两次决定你的番数"),
            new HexDef(35, "D个痛快", "本回合你可以摸三打三"),
            new HexDef(36, "别再错过", "你可以放弃摸牌阶段，转而从自己打出去的牌中摸一张"),
            new HexDef(37, "挑个好伙计", "若你已经听牌，其余人打出一张你所听的牌，你可以进行一次判定，若下张牌为你所预测的类型（条饼万杂），则你可以拿一张牌换取你所听的牌")
    );

    private static final Map<Integer, HexDef> HEX_BY_ID = buildHexMap();
    private static RoomState state = loadOrCreateState();

    public static void main(String[] args) throws IOException {
        int port = resolvePort();
        HttpServer server = HttpServer.create(new InetSocketAddress(HOST, port), 0);
        server.createContext("/", MahjongHexWebApp::handleRoot);
        server.createContext("/api/state", MahjongHexWebApp::handleState);
        server.createContext("/api/join", MahjongHexWebApp::handleJoin);
        server.createContext("/api/draw", MahjongHexWebApp::handleDraw);
        server.createContext("/api/use", MahjongHexWebApp::handleUse);
        server.createContext("/api/clear", MahjongHexWebApp::handleClear);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();

        System.out.println("Mahjong Hex room is running.");
        System.out.println("Local: http://localhost:" + port);
        String lan = resolveLanUrl(port);
        if (!lan.isBlank()) {
            System.out.println("LAN:   " + lan);
        }
    }

    private static void handleRoot(HttpExchange ex) throws IOException {
        if (!isMethod(ex, "GET")) {
            sendJson(ex, 405, errorJson("Method Not Allowed", false));
            return;
        }

        String path = ex.getRequestURI().getPath();
        if ("/".equals(path) || "/index.html".equals(path)) {
            sendText(ex, 200, readIndexHtml(), "text/html; charset=utf-8");
            return;
        }

        if ("/favicon.ico".equals(path)) {
            ex.sendResponseHeaders(204, -1);
            ex.close();
            return;
        }

        sendJson(ex, 404, errorJson("Not Found", false));
    }

    private static void handleState(HttpExchange ex) throws IOException {
        if (!isMethod(ex, "GET")) {
            sendJson(ex, 405, errorJson("Method Not Allowed", false));
            return;
        }

        String playerId = trimToEmpty(parseQuery(ex.getRequestURI()).get("playerId"));
        String stateJson;
        synchronized (LOCK) {
            stateJson = buildStateJsonLocked(playerId);
        }
        sendJson(ex, 200, "{\"ok\":true,\"state\":" + stateJson + "}");
    }

    private static void handleJoin(HttpExchange ex) throws IOException {
        if (!isMethod(ex, "POST")) {
            sendJson(ex, 405, errorJson("Method Not Allowed", false));
            return;
        }

        Map<String, String> form = parseFormBody(ex);
        String name = normalizeName(form.get("name"));
        if (name.isBlank()) {
            sendJson(ex, 400, errorJson("请输入姓名后加入房间", true));
            return;
        }

        String oldId = trimToEmpty(form.get("playerId"));
        String playerId;
        String stateJson;

        synchronized (LOCK) {
            PlayerState player = oldId.isBlank() ? null : state.players.get(oldId);
            String excludePlayerId = player == null ? "" : defaultString(player.id);
            if (isNameUsedByOtherLocked(name, excludePlayerId)) {
                sendJson(ex, 409, errorJson("姓名已存在，请使用其他姓名", true));
                return;
            }

            if (player == null) {
                player = new PlayerState();
                player.id = nextPlayerIdLocked();
                player.name = name;
                player.joinedAt = Instant.now().toString();
                player.hexes = new ArrayList<>();
                state.players.put(player.id, player);
            } else {
                player.name = name;
            }

            playerId = player.id;
            markDirtyAndSaveLocked();
            stateJson = buildStateJsonLocked(playerId);
        }

        sendJson(ex, 200, "{\"ok\":true,\"playerId\":\"" + escapeJson(playerId) + "\",\"state\":" + stateJson + "}");
    }

    private static void handleDraw(HttpExchange ex) throws IOException {
        if (!isMethod(ex, "POST")) {
            sendJson(ex, 405, errorJson("Method Not Allowed", false));
            return;
        }

        String playerId = trimToEmpty(parseFormBody(ex).get("playerId"));
        if (playerId.isBlank()) {
            sendJson(ex, 400, errorJson("请先输入姓名加入房间", true));
            return;
        }

        String stateJson;
        synchronized (LOCK) {
            PlayerState player = state.players.get(playerId);
            if (player == null) {
                sendJson(ex, 400, errorJson("玩家不存在，请重新加入房间", true));
                return;
            }

            if (state.remainingPool.isEmpty()) {
                refillPoolLocked();
                state.poolCycle += 1;
            }

            int index = ThreadLocalRandom.current().nextInt(state.remainingPool.size());
            int hexId = state.remainingPool.remove(index);

            OwnedHex card = new OwnedHex();
            card.cardId = "c" + state.cardSeq++;
            card.hexId = hexId;
            card.drawnAt = Instant.now().toString();
            player.hexes.add(card);

            markDirtyAndSaveLocked();
            stateJson = buildStateJsonLocked(playerId);
        }

        sendJson(ex, 200, "{\"ok\":true,\"state\":" + stateJson + "}");
    }

    private static void handleUse(HttpExchange ex) throws IOException {
        if (!isMethod(ex, "POST")) {
            sendJson(ex, 405, errorJson("Method Not Allowed", false));
            return;
        }

        Map<String, String> form = parseFormBody(ex);
        String playerId = trimToEmpty(form.get("playerId"));
        String cardId = trimToEmpty(form.get("cardId"));
        String rawTarget = trimToEmpty(form.get("target"));
        String target = "SELF";
        String targetPlayerId = "";
        if ("ALL".equalsIgnoreCase(rawTarget)) {
            target = "ALL";
        } else if (rawTarget.regionMatches(true, 0, "PLAYER:", 0, 7)) {
            target = "PLAYER";
            targetPlayerId = trimToEmpty(rawTarget.substring(7));
        }

        if (playerId.isBlank() || cardId.isBlank()) {
            sendJson(ex, 400, errorJson("参数缺失，无法使用海克斯", true));
            return;
        }

        String stateJson;
        synchronized (LOCK) {
            PlayerState player = state.players.get(playerId);
            if (player == null) {
                sendJson(ex, 400, errorJson("玩家不存在，请重新加入房间", true));
                return;
            }

            OwnedHex removed = null;
            for (int i = 0; i < player.hexes.size(); i++) {
                OwnedHex card = player.hexes.get(i);
                if (card != null && cardId.equals(card.cardId)) {
                    removed = player.hexes.remove(i);
                    break;
                }
            }

            if (removed == null) {
                sendJson(ex, 404, errorJson("找不到这张海克斯卡", false));
                return;
            }

            HexDef def = HEX_BY_ID.get(removed.hexId);
            if (def == null) {
                sendJson(ex, 400, errorJson("海克斯数据异常", false));
                return;
            }

            String resolvedTarget = target;
            String resolvedTargetPlayerId = "";
            String targetLabel;
            if ("ALL".equals(target)) {
                targetLabel = "对所有人使用";
            } else if ("PLAYER".equals(target)) {
                if (targetPlayerId.isBlank()) {
                    sendJson(ex, 400, errorJson("请选择目标玩家", false));
                    return;
                }
                PlayerState targetPlayer = state.players.get(targetPlayerId);
                if (targetPlayer == null) {
                    sendJson(ex, 400, errorJson("目标玩家不存在或已离开房间", false));
                    return;
                }

                if (defaultString(targetPlayer.id).equals(defaultString(player.id))) {
                    resolvedTarget = "SELF";
                    targetLabel = "对自己使用";
                } else {
                    resolvedTarget = "PLAYER";
                    resolvedTargetPlayerId = defaultString(targetPlayer.id);
                    targetLabel = "对" + defaultString(targetPlayer.name) + "使用";
                }
            } else {
                targetLabel = "对自己使用";
            }

            UseLog log = new UseLog();
            log.id = "l" + state.logSeq++;
            log.playerId = player.id;
            log.playerName = player.name;
            log.hexName = def.name;
            log.target = resolvedTarget;
            log.targetPlayerId = resolvedTargetPlayerId;
            log.targetLabel = targetLabel;
            log.usedAt = Instant.now().toString();
            state.useLogs.add(0, log);
            if (state.useLogs.size() > 80) {
                state.useLogs.remove(state.useLogs.size() - 1);
            }

            markDirtyAndSaveLocked();
            stateJson = buildStateJsonLocked(playerId);
        }

        sendJson(ex, 200, "{\"ok\":true,\"state\":" + stateJson + "}");
    }

    private static void handleClear(HttpExchange ex) throws IOException {
        if (!isMethod(ex, "POST")) {
            sendJson(ex, 405, errorJson("Method Not Allowed", false));
            return;
        }

        String stateJson;
        synchronized (LOCK) {
            state = createFreshState();
            saveStateLocked();
            stateJson = buildStateJsonLocked("");
        }

        sendJson(ex, 200, "{\"ok\":true,\"state\":" + stateJson + "}");
    }

    private static int resolvePort() {
        String portEnv = System.getenv("PORT");
        if (portEnv == null || portEnv.isBlank()) {
            return DEFAULT_PORT;
        }
        try {
            int parsed = Integer.parseInt(portEnv.trim());
            return parsed > 0 ? parsed : DEFAULT_PORT;
        } catch (NumberFormatException e) {
            return DEFAULT_PORT;
        }
    }

    private static String resolveLanUrl(int port) {
        try {
            for (NetworkInterface ni : java.util.Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) {
                    continue;
                }
                for (InetAddress addr : java.util.Collections.list(ni.getInetAddresses())) {
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        return "http://" + addr.getHostAddress() + ":" + port;
                    }
                }
            }
        } catch (Exception ignored) {
            // ignore
        }
        return "";
    }

    private static String readIndexHtml() {
        try {
            return Files.readString(WEB_FILE, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "<!doctype html><meta charset=\"UTF-8\"><h1>index.html not found</h1>";
        }
    }

    private static RoomState loadOrCreateState() {
        if (Files.exists(STATE_FILE)) {
            try (ObjectInputStream in = new ObjectInputStream(Files.newInputStream(STATE_FILE))) {
                Object obj = in.readObject();
                if (obj instanceof RoomState loaded) {
                    sanitizeLoadedState(loaded);
                    if (loaded.remainingPool.isEmpty()) {
                        refillPool(loaded);
                    }
                    return loaded;
                }
            } catch (Exception e) {
                System.err.println("Failed to load room state, use fresh state: " + e.getMessage());
            }
        }
        return createFreshState();
    }

    private static RoomState createFreshState() {
        RoomState rs = new RoomState();
        rs.players = new LinkedHashMap<>();
        rs.remainingPool = new ArrayList<>();
        rs.useLogs = new ArrayList<>();
        rs.version = 1;
        rs.cardSeq = 1;
        rs.logSeq = 1;
        rs.poolCycle = 1;
        refillPool(rs);
        return rs;
    }

    private static void sanitizeLoadedState(RoomState rs) {
        if (rs.players == null) {
            rs.players = new LinkedHashMap<>();
        }
        if (rs.remainingPool == null) {
            rs.remainingPool = new ArrayList<>();
        }
        if (rs.useLogs == null) {
            rs.useLogs = new ArrayList<>();
        }
        if (rs.version <= 0) {
            rs.version = 1;
        }
        if (rs.cardSeq <= 0) {
            rs.cardSeq = 1;
        }
        if (rs.logSeq <= 0) {
            rs.logSeq = 1;
        }
        if (rs.poolCycle <= 0) {
            rs.poolCycle = 1;
        }

        LinkedHashMap<String, PlayerState> cleanedPlayers = new LinkedHashMap<>();
        long maxCardSeq = 0;
        Set<Integer> validIds = HEX_BY_ID.keySet();

        for (Map.Entry<String, PlayerState> entry : rs.players.entrySet()) {
            String id = trimToEmpty(entry.getKey());
            PlayerState p = entry.getValue();
            if (id.isBlank() || p == null) {
                continue;
            }

            if (p.hexes == null) {
                p.hexes = new ArrayList<>();
            }

            ArrayList<OwnedHex> cleanedHexes = new ArrayList<>();
            for (OwnedHex hex : p.hexes) {
                if (hex == null || !validIds.contains(hex.hexId) || trimToEmpty(hex.cardId).isBlank()) {
                    continue;
                }
                cleanedHexes.add(hex);
                maxCardSeq = Math.max(maxCardSeq, parseSequence(hex.cardId));
            }

            p.hexes = cleanedHexes;
            if (trimToEmpty(p.id).isBlank()) {
                p.id = id;
            }
            if (trimToEmpty(p.name).isBlank()) {
                p.name = "玩家";
            }
            if (p.joinedAt == null) {
                p.joinedAt = "";
            }

            cleanedPlayers.put(id, p);
        }
        rs.players = cleanedPlayers;

        ArrayList<Integer> cleanedPool = new ArrayList<>();
        Set<Integer> seen = new HashSet<>();
        for (Integer id : rs.remainingPool) {
            if (id != null && validIds.contains(id) && seen.add(id)) {
                cleanedPool.add(id);
            }
        }
        rs.remainingPool = cleanedPool;

        ArrayList<UseLog> cleanedLogs = new ArrayList<>();
        long maxLogSeq = 0;
        for (UseLog log : rs.useLogs) {
            if (log == null || trimToEmpty(log.id).isBlank() || trimToEmpty(log.hexName).isBlank()) {
                continue;
            }
            if (trimToEmpty(log.playerName).isBlank()) {
                log.playerName = "玩家";
            }
            if (trimToEmpty(log.target).isBlank()) {
                log.target = "SELF";
            }
            if (log.targetPlayerId == null) {
                log.targetPlayerId = "";
            }
            if (trimToEmpty(log.targetLabel).isBlank()) {
                if ("ALL".equals(log.target)) {
                    log.targetLabel = "对所有人使用";
                } else if ("PLAYER".equals(log.target) && !trimToEmpty(log.targetPlayerId).isBlank()) {
                    PlayerState targetPlayer = rs.players.get(log.targetPlayerId);
                    if (targetPlayer != null && !trimToEmpty(targetPlayer.name).isBlank()) {
                        log.targetLabel = "对" + targetPlayer.name + "使用";
                    } else {
                        log.targetLabel = "对指定玩家使用";
                    }
                } else {
                    log.targetLabel = "对自己使用";
                }
            }
            if (log.usedAt == null) {
                log.usedAt = "";
            }
            cleanedLogs.add(log);
            maxLogSeq = Math.max(maxLogSeq, parseSequence(log.id));
            if (cleanedLogs.size() >= 80) {
                break;
            }
        }
        rs.useLogs = cleanedLogs;

        if (rs.cardSeq <= maxCardSeq) {
            rs.cardSeq = maxCardSeq + 1;
        }
        if (rs.logSeq <= maxLogSeq) {
            rs.logSeq = maxLogSeq + 1;
        }
    }

    private static long parseSequence(String value) {
        if (value == null || value.length() < 2) {
            return 0;
        }
        try {
            return Long.parseLong(value.substring(1));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static void refillPoolLocked() {
        refillPool(state);
    }

    private static void refillPool(RoomState rs) {
        rs.remainingPool.clear();
        for (HexDef def : HEXES) {
            rs.remainingPool.add(def.id);
        }
    }

    private static void markDirtyAndSaveLocked() {
        state.version += 1;
        saveStateLocked();
    }

    private static void saveStateLocked() {
        Path temp = STATE_FILE.resolveSibling(STATE_FILE.getFileName() + ".tmp");
        try (ObjectOutputStream out = new ObjectOutputStream(Files.newOutputStream(
                temp,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE
        ))) {
            out.writeObject(state);
        } catch (IOException e) {
            System.err.println("Failed to save room state: " + e.getMessage());
            return;
        }

        try {
            Files.move(temp, STATE_FILE, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            try {
                Files.move(temp, STATE_FILE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException inner) {
                System.err.println("Failed to replace room state file: " + inner.getMessage());
            }
        } catch (IOException e) {
            System.err.println("Failed to move room state file: " + e.getMessage());
        }
    }

    private static String nextPlayerIdLocked() {
        String id;
        do {
            id = "p" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        } while (state.players.containsKey(id));
        return id;
    }

    private static boolean isNameUsedByOtherLocked(String name, String excludePlayerId) {
        for (PlayerState existing : state.players.values()) {
            if (existing == null) {
                continue;
            }
            String existingId = defaultString(existing.id);
            if (!excludePlayerId.isBlank() && excludePlayerId.equals(existingId)) {
                continue;
            }
            if (name.equalsIgnoreCase(normalizeName(existing.name))) {
                return true;
            }
        }
        return false;
    }

    private static String buildStateJsonLocked(String meId) {
        boolean meExists = !trimToEmpty(meId).isBlank() && state.players.containsKey(meId);
        String safeMeId = meExists ? meId : "";

        StringBuilder sb = new StringBuilder(8192);
        sb.append('{')
                .append("\"version\":").append(state.version).append(',')
                .append("\"needJoin\":").append(!meExists).append(',')
                .append("\"meId\":\"").append(escapeJson(safeMeId)).append("\",")
                .append("\"poolTotal\":").append(HEXES.size()).append(',')
                .append("\"poolRemaining\":").append(state.remainingPool.size()).append(',')
                .append("\"poolCycle\":").append(state.poolCycle).append(',');

        sb.append("\"players\":[");
        int playerCount = 0;
        for (PlayerState player : state.players.values()) {
            if (player == null) {
                continue;
            }
            if (playerCount++ > 0) {
                sb.append(',');
            }

            boolean isMe = meExists && defaultString(player.id).equals(meId);
            List<OwnedHex> allCards = player.hexes == null ? List.of() : player.hexes;
            List<OwnedHex> visibleCards = isMe ? allCards : List.of();
            sb.append('{')
                    .append("\"id\":\"").append(escapeJson(defaultString(player.id))).append("\",")
                    .append("\"name\":\"").append(escapeJson(defaultString(player.name))).append("\",")
                    .append("\"isMe\":").append(isMe).append(',')
                    .append("\"joinedAt\":\"").append(escapeJson(defaultString(player.joinedAt))).append("\",")
                    .append("\"hexCount\":").append(isMe ? allCards.size() : 0).append(',')
                    .append("\"hexes\":[");

            int cardCount = 0;
            for (OwnedHex card : visibleCards) {
                if (card == null) {
                    continue;
                }
                HexDef def = HEX_BY_ID.get(card.hexId);
                if (def == null) {
                    continue;
                }
                if (cardCount++ > 0) {
                    sb.append(',');
                }
                sb.append('{')
                        .append("\"cardId\":\"").append(escapeJson(defaultString(card.cardId))).append("\",")
                        .append("\"hexId\":").append(card.hexId).append(',')
                        .append("\"name\":\"").append(escapeJson(def.name)).append("\",")
                        .append("\"desc\":\"").append(escapeJson(def.desc)).append("\",")
                        .append("\"drawnAt\":\"").append(escapeJson(defaultString(card.drawnAt))).append("\"}");
            }
            sb.append("]}");
        }
        sb.append("],");

        sb.append("\"logs\":[");
        int logCount = 0;
        for (UseLog log : state.useLogs) {
            if (log == null) {
                continue;
            }
            if (logCount++ > 0) {
                sb.append(',');
            }
            sb.append('{')
                    .append("\"id\":\"").append(escapeJson(defaultString(log.id))).append("\",")
                    .append("\"playerId\":\"").append(escapeJson(defaultString(log.playerId))).append("\",")
                    .append("\"playerName\":\"").append(escapeJson(defaultString(log.playerName))).append("\",")
                    .append("\"hexName\":\"").append(escapeJson(defaultString(log.hexName))).append("\",")
                    .append("\"target\":\"").append(escapeJson(defaultString(log.target))).append("\",")
                    .append("\"targetPlayerId\":\"").append(escapeJson(defaultString(log.targetPlayerId))).append("\",")
                    .append("\"targetLabel\":\"").append(escapeJson(defaultString(log.targetLabel))).append("\",")
                    .append("\"usedAt\":\"").append(escapeJson(defaultString(log.usedAt))).append("\"}");
        }
        sb.append("]}");
        return sb.toString();
    }

    private static String defaultString(String value) {
        return value == null ? "" : value;
    }

    private static Map<Integer, HexDef> buildHexMap() {
        Map<Integer, HexDef> map = new LinkedHashMap<>();
        for (HexDef def : HEXES) {
            map.put(def.id, def);
        }
        return map;
    }

    private static boolean isMethod(HttpExchange ex, String method) {
        return method.equalsIgnoreCase(ex.getRequestMethod());
    }

    private static String errorJson(String message, boolean needJoin) {
        return "{\"ok\":false,\"needJoin\":" + needJoin + ",\"error\":\"" + escapeJson(message) + "\"}";
    }

    private static Map<String, String> parseQuery(URI uri) {
        return parseQueryString(uri.getRawQuery());
    }

    private static Map<String, String> parseFormBody(HttpExchange ex) throws IOException {
        String raw = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        return parseQueryString(raw);
    }

    private static Map<String, String> parseQueryString(String raw) {
        Map<String, String> params = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) {
            return params;
        }

        String[] pairs = raw.split("&");
        for (String pair : pairs) {
            if (pair.isBlank()) {
                continue;
            }
            int idx = pair.indexOf('=');
            String key = idx >= 0 ? pair.substring(0, idx) : pair;
            String value = idx >= 0 ? pair.substring(idx + 1) : "";
            String decodedKey = URLDecoder.decode(key, StandardCharsets.UTF_8);
            String decodedValue = URLDecoder.decode(value, StandardCharsets.UTF_8);
            params.put(decodedKey, decodedValue);
        }
        return params;
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private static String normalizeName(String name) {
        if (name == null) {
            return "";
        }
        String cleaned = name.strip().replaceAll("\\s+", " ");
        if (cleaned.length() > 20) {
            cleaned = cleaned.substring(0, 20);
        }
        return cleaned;
    }

    private static String escapeJson(String text) {
        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static void sendJson(HttpExchange ex, int status, String body) throws IOException {
        sendText(ex, status, body, "application/json; charset=utf-8");
    }

    private static void sendText(HttpExchange ex, int status, String body, String contentType) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", contentType);
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static final class HexDef {
        private final int id;
        private final String name;
        private final String desc;

        private HexDef(int id, String name, String desc) {
            this.id = id;
            this.name = name;
            this.desc = desc;
        }
    }

    private static final class RoomState implements Serializable {
        private static final long serialVersionUID = 1L;

        private LinkedHashMap<String, PlayerState> players;
        private ArrayList<Integer> remainingPool;
        private ArrayList<UseLog> useLogs;
        private long version;
        private long cardSeq;
        private long logSeq;
        private long poolCycle;
    }

    private static final class PlayerState implements Serializable {
        private static final long serialVersionUID = 1L;

        private String id;
        private String name;
        private String joinedAt;
        private ArrayList<OwnedHex> hexes;
    }

    private static final class OwnedHex implements Serializable {
        private static final long serialVersionUID = 1L;

        private String cardId;
        private int hexId;
        private String drawnAt;
    }

    private static final class UseLog implements Serializable {
        private static final long serialVersionUID = 1L;

        private String id;
        private String playerId;
        private String playerName;
        private String hexName;
        private String target;
        private String targetPlayerId;
        private String targetLabel;
        private String usedAt;
    }
}
