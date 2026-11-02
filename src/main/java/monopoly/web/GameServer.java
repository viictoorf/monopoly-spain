package monopoly.web;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import monopoly.game.Avatar;
import monopoly.game.Bot;
import monopoly.game.Game;
import monopoly.game.RuleException;
import monopoly.game.Session;

/**
 * Tiny local web server (JDK only). Security choices: listens on localhost unless --lan is given, checks the
 * Host header (DNS rebinding), requires a custom header on POST (cross-site forms cannot send it), serves only a
 * fixed whitelist of files (no path traversal), caps request size and sends a strict Content-Security-Policy.
 * With --lan, other devices can play but only the machine running the server can start, load or undo a game.
 */
public final class GameServer {
    private static final int MAX_BODY = 4096;
    private static final String HTML = "text/html; charset=utf-8";
    private static final Map<String, String[]> ASSETS = Map.of(
            "/", new String[] {"/web/index.html", HTML},
            "/index.html", new String[] {"/web/index.html", HTML},
            "/style.css", new String[] {"/web/style.css", "text/css; charset=utf-8"},
            "/app.js", new String[] {"/web/app.js", "text/javascript; charset=utf-8"},
            "/titulo.woff2", new String[] {"/web/titulo.woff2", "font/woff2"});

    private final HttpServer server;
    private final boolean lan;
    private final Random rng = new Random();
    private final Map<String, byte[]> cache = new HashMap<>();
    private final SaveStore store = SaveStore.inHome();
    private Session session;        // guarded by "this"
    private Session saved;          // the game found on disk, offered on the start screen
    private int rev;                // bumped on every change so other devices know when to refresh

    public GameServer(boolean lan, int port) throws IOException {
        this.lan = lan;
        this.saved = store.load();
        InetAddress address = lan ? InetAddress.getByName("0.0.0.0") : InetAddress.getLoopbackAddress();
        server = HttpServer.create(new InetSocketAddress(address, port), 0);
        server.createContext("/", this::handle);
    }

    public void start() {
        server.start();
    }

    private void handle(HttpExchange ex) throws IOException {
        try {
            if (!lan && !hostAllowed(ex)) {
                send(ex, 403, "text/plain", "forbidden".getBytes(StandardCharsets.UTF_8));
                return;
            }
            String path = ex.getRequestURI().getPath();
            if (path.startsWith("/api/")) {
                api(ex, path);
            } else {
                asset(ex, path);
            }
        } catch (Exception e) {
            json(ex, 500, "{\"error\":\"Error interno\"}");
        } finally {
            ex.close();
        }
    }

    private static boolean hostAllowed(HttpExchange ex) {
        String host = ex.getRequestHeaders().getFirst("Host");
        return host != null && (host.startsWith("localhost:") || host.startsWith("127.0.0.1:"));
    }

    private void asset(HttpExchange ex, String path) throws IOException {
        String[] entry = ASSETS.get(path);
        if (entry == null || !ex.getRequestMethod().equals("GET")) {
            send(ex, 404, "text/plain", "not found".getBytes(StandardCharsets.UTF_8));
            return;
        }
        byte[] body = cache.get(entry[0]);
        if (body == null) {
            try (InputStream in = GameServer.class.getResourceAsStream(entry[0])) {
                body = in.readAllBytes();
            }
            cache.put(entry[0], body);
        }
        send(ex, 200, entry[1], body);
    }

    private synchronized void api(HttpExchange ex, String path) throws IOException {
        boolean local = ex.getRemoteAddress().getAddress().isLoopbackAddress();
        String method = ex.getRequestMethod();
        if (method.equals("GET") && path.equals("/api/state")) {
            String asked = query(ex, "rev");
            if (asked.equals(String.valueOf(rev))) {
                json(ex, 200, "{\"same\":true}");
            } else {
                json(ex, 200, snapshot(local));
            }
            return;
        }
        if (!method.equals("POST") || !"monopoly".equals(ex.getRequestHeaders().getFirst("X-Requested-With"))) {
            json(ex, 403, "{\"error\":\"Petición no permitida\"}");
            return;
        }
        Map<String, String> form = form(ex);
        try {
            switch (path) {
                case "/api/new" -> {
                    requireLocal(local);
                    session = Session.start(setupFrom(form));
                    changed();
                }
                case "/api/load" -> {
                    requireLocal(local);
                    if (saved == null) {
                        throw new RuleException("No hay ninguna partida guardada.");
                    }
                    session = saved;
                    changed();
                }
                case "/api/undo" -> {
                    requireLocal(local);
                    requireGame();
                    session.undo();
                    changed();
                }
                case "/api/action" -> {
                    requireGame();
                    act(form.getOrDefault("a", ""));
                    changed();
                }
                default -> {
                    json(ex, 404, "{\"error\":\"No existe\"}");
                    return;
                }
            }
            json(ex, 200, snapshot(local));
        } catch (RuleException e) {
            json(ex, 400, "{\"error\":" + StateJson.q(e.getMessage()) + "}");
        }
    }

    private String snapshot(boolean local) {
        return session == null ? StateJson.idle(saved, rev) : StateJson.of(session, rev, local);
    }

    private void changed() {
        rev++;
        store.save(session);
        saved = session;
    }

    /** A human action, or "bot": one step of the computer player whose turn it is. */
    private void act(String action) throws RuleException {
        boolean botTurn = session.game().getActor().isBot();
        if (action.equals("bot")) {
            if (!botTurn || session.game().getPhase() == Game.Phase.OVER) {
                throw new RuleException("No le toca a un bot.");
            }
            String move = Bot.decide(session.game());
            try {
                session.apply(move);
            } catch (RuleException e) {
                session.apply(Bot.fallback(session.game()));
            }
            return;
        }
        if (botTurn) {
            throw new RuleException("Es el turno de un bot: espera a que juegue.");
        }
        session.apply(action);
    }

    private Session.Setup setupFrom(Map<String, String> form) throws RuleException {
        List<Game.PlayerSpec> specs = new ArrayList<>();
        for (String part : form.getOrDefault("players", "").split(";")) {
            String[] f = part.split("\\|", -1);
            if (f.length != 3) {
                throw new RuleException("Datos de jugador no válidos.");
            }
            try {
                specs.add(new Game.PlayerSpec(f[0], f[1].isEmpty() ? null : Avatar.valueOf(f[1]), f[2].equals("1")));
            } catch (IllegalArgumentException e) {
                throw new RuleException("Ficha no válida.");
            }
        }
        String rounds = form.getOrDefault("rounds", "0");
        if (!rounds.matches("\\d{1,3}")) {
            throw new RuleException("Número de rondas no válido.");
        }
        return new Session.Setup(rng.nextLong(), specs, new Game.Options(Integer.parseInt(rounds), form.getOrDefault("house", "0").equals("1")));
    }

    private void requireGame() throws RuleException {
        if (session == null) {
            throw new RuleException("No hay ninguna partida en curso.");
        }
    }

    private static void requireLocal(boolean local) throws RuleException {
        if (!local) {
            throw new RuleException("Eso solo se puede hacer desde el ordenador que aloja la partida.");
        }
    }

    private static String query(HttpExchange ex, String key) {
        String q = ex.getRequestURI().getRawQuery();
        if (q != null) {
            for (String pair : q.split("&")) {
                if (pair.startsWith(key + "=")) {
                    return pair.substring(key.length() + 1);
                }
            }
        }
        return "";
    }

    private static Map<String, String> form(HttpExchange ex) throws IOException {
        byte[] raw = ex.getRequestBody().readNBytes(MAX_BODY + 1);
        if (raw.length > MAX_BODY) {
            throw new IOException("body too large");
        }
        Map<String, String> map = new HashMap<>();
        for (String pair : new String(raw, StandardCharsets.UTF_8).split("&")) {
            int eq = pair.indexOf('=');
            if (!pair.isEmpty()) {
                map.put(URLDecoder.decode(eq < 0 ? pair : pair.substring(0, eq), StandardCharsets.UTF_8),
                        eq < 0 ? "" : URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
            }
        }
        return map;
    }

    private static void json(HttpExchange ex, int status, String body) throws IOException {
        send(ex, status, "application/json; charset=utf-8", body.getBytes(StandardCharsets.UTF_8));
    }

    private static void send(HttpExchange ex, int status, String type, byte[] body) throws IOException {
        var h = ex.getResponseHeaders();
        h.set("Content-Type", type);
        h.set("Cache-Control", "no-store");
        h.set("X-Content-Type-Options", "nosniff");
        h.set("Content-Security-Policy", "default-src 'self'; img-src 'self' data:; font-src 'self'; frame-ancestors 'none'");
        ex.sendResponseHeaders(status, body.length);
        ex.getResponseBody().write(body);
    }
}
