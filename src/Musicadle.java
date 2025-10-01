import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class Musicadle {
    private static final String CLIENT_ID = Optional.ofNullable(System.getenv("SPOTIFY_CLIENT_ID")).orElse("");
    private static final String CLIENT_SECRET = Optional.ofNullable(System.getenv("SPOTIFY_CLIENT_SECRET")).orElse("");
    private static String accessToken;
    private static final ObjectMapper mapper = new ObjectMapper();

    private static final Map<String, GameState> games = new HashMap<>();

    public static void main(String[] args) {
        if (args.length > 0 && "--cli".equals(args[0])) {
            runCli();
            return;
        }

        try {
            startServer();
        } catch (Exception e) {
            System.err.println("Error starting server: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void startServer() throws Exception {
        if (CLIENT_ID.isEmpty() || CLIENT_SECRET.isEmpty()) {
            System.out.println("⚠️  SPOTIFY_CLIENT_ID o SPOTIFY_CLIENT_SECRET no están definidos. El backend no podrá autenticar.");
        }

        try {
            authenticate();
        } catch (Exception e) {
            System.out.println("⚠️  No se pudo autenticar con Spotify al iniciar: " + e.getMessage());
        }

        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/", new StaticFileHandler("web/index.html"));
        server.createContext("/api/game", new GameHandler());
        server.createContext("/api/game/", new GuessHandler());

        server.setExecutor(null);
        System.out.println("🚀 Servidor iniciado en http://localhost:8080");
        server.start();
    }

    private static void runCli() {
        Scanner scanner = new Scanner(System.in);

        System.out.print("Elige el artista: ");
        String artistName = scanner.nextLine();

        System.out.println("🎵 Iniciando Musicadle... 🎵");

        try {
            ensureAuthenticated();

            GameState state = createGame(artistName);

            if (state == null || state.albums.isEmpty()) {
                System.out.println("No se encontraron álbumes para este artista.");
                return;
            }

            System.out.println("🎵 ¡Bienvenido a Musicadle! 🎵");
            System.out.println("Adivina la canción de " + state.artistName);

            state.board.print();

            Scanner guessScanner = new Scanner(System.in);
            while (!state.over()) {
                System.out.print("Guess the song: ");
                String guess = guessScanner.nextLine();
                GuessResult result = processGuess(state, guess);

                while (!result.accepted) {
                    System.out.print(result.message + " ");
                    guess = guessScanner.nextLine();
                    result = processGuess(state, guess);
                }

                System.out.println(result.feedback);
                state.board.print();
            }

            if (state.win) {
                System.out.println("✅ Ganaste en " + state.attempts + " intentos.");
            } else {
                System.out.println("❌ Perdiste. La canción era: " + state.randomTrack.name + ".");
            }

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }

        scanner.close();
    }

    private static void authenticate() throws Exception {
        if (CLIENT_ID.isEmpty() || CLIENT_SECRET.isEmpty()) {
            throw new IllegalStateException("Las credenciales de Spotify no están configuradas. Define SPOTIFY_CLIENT_ID y SPOTIFY_CLIENT_SECRET.");
        }

        String auth = CLIENT_ID + ":" + CLIENT_SECRET;
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes(StandardCharsets.UTF_8));

        URL url = new URL("https://accounts.spotify.com/api/token");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Basic " + encodedAuth);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setDoOutput(true);

        String body = "grant_type=client_credentials";
        conn.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));

        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        reader.close();

        JsonNode json = mapper.readTree(response.toString());
        accessToken = json.get("access_token").asText();
    }

    private static void ensureAuthenticated() throws Exception {
        if (accessToken == null || accessToken.isEmpty()) {
            authenticate();
        }
    }

    private static JsonNode makeSpotifyRequest(String endpoint) throws Exception {
        URL url = new URL("https://api.spotify.com/v1/" + endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + accessToken);

        BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        reader.close();

        return mapper.readTree(response.toString());
    }

    private static JsonNode searchArtist(String artistName) throws Exception {
        String encoded = URLEncoder.encode(artistName, StandardCharsets.UTF_8.toString());
        JsonNode result = makeSpotifyRequest("search?q=" + encoded + "&type=artist&limit=1");
        return result.get("artists").get("items").get(0);
    }

    private static List<Album> getArtistAlbums(String artistId) throws Exception {
        JsonNode albumsData = makeSpotifyRequest("artists/" + artistId + "/albums?include_groups=album");
        List<Album> albums = new ArrayList<>();

        for (JsonNode albumItem : albumsData.get("items")) {
            String albumId = albumItem.get("id").asText();
            JsonNode albumData = makeSpotifyRequest("albums/" + albumId);

            Album album = new Album();
            album.name = albumData.get("name").asText();
            album.releaseDate = albumData.get("release_date").asText();
            album.tracks = new ArrayList<>();

            for (JsonNode trackItem : albumData.get("tracks").get("items")) {
                Track track = new Track();
                track.name = trackItem.get("name").asText();
                track.trackNumber = trackItem.get("track_number").asInt();
                track.duration = trackItem.get("duration_ms").asInt() / 1000.0;
                track.features = trackItem.get("artists").size() > 1;
                track.explicit = trackItem.get("explicit").asBoolean();

                album.tracks.add(track);
            }

            if (!album.tracks.isEmpty()) {
                albums.add(album);
            }
        }

        return albums;
    }

    private static GameState createGame(String artistName) throws Exception {
        JsonNode artistData = searchArtist(artistName);
        String artistId = artistData.get("id").asText();
        String finalArtistName = artistData.get("name").asText();

        List<Album> albums = getArtistAlbums(artistId);
        if (albums.isEmpty()) {
            return null;
        }

        Random random = new Random();
        Album randomAlbum = albums.get(random.nextInt(albums.size()));
        Track randomTrack = randomAlbum.tracks.get(random.nextInt(randomAlbum.tracks.size()));

        GameState state = new GameState();
        state.artistId = artistId;
        state.artistName = finalArtistName;
        state.albums = albums;
        state.randomAlbum = randomAlbum;
        state.randomTrack = randomTrack;
        state.board = new Board();

        Map<String, TrackSelection> trackLookup = new HashMap<>();
        for (Album album : albums) {
            for (Track track : album.tracks) {
                String key = track.name.toLowerCase();
                trackLookup.putIfAbsent(key, new TrackSelection(track, album));
            }
        }

        state.trackLookup = trackLookup;
        state.possibleTracks = new ArrayList<>(trackLookup.keySet());
        Collections.sort(state.possibleTracks);

        return state;
    }

    private static GuessResult processGuess(GameState state, String guessInput) {
        if (state.over()) {
            return new GuessResult(false, "El juego ya ha terminado.", "");
        }

        if (guessInput == null || guessInput.trim().isEmpty()) {
            return new GuessResult(false, "Ingresa un nombre de canción válido.", "");
        }

        String guess = guessInput.trim().toLowerCase();

        if (!state.trackLookup.containsKey(guess)) {
            return new GuessResult(false, "No es una canción válida de este artista. Intenta otra vez.", "");
        }

        if (state.guesses.contains(guess)) {
            return new GuessResult(false, "Ya intentaste esa canción. Prueba con otra.", "");
        }

        state.guesses.add(guess);
        state.lives--;
        state.attempts++;

        TrackSelection selection = state.trackLookup.get(guess);
        Track guessedTrack = selection.track;
        Album guessedAlbum = selection.album;

        int index = state.attempts - 1;
        state.board.name[index] = guessedTrack.name;

        int albumComparison = guessedAlbum.releaseDate.compareTo(state.randomAlbum.releaseDate);
        if (albumComparison < 0) {
            state.board.album[index] = guessedAlbum.name + " ↑";
        } else if (albumComparison > 0) {
            state.board.album[index] = guessedAlbum.name + " ↓";
        } else {
            state.board.album[index] = guessedAlbum.name + " ✅";
        }

        if (guessedTrack.trackNumber < state.randomTrack.trackNumber) {
            state.board.trackNo[index] = guessedTrack.trackNumber + " ↑";
        } else if (guessedTrack.trackNumber > state.randomTrack.trackNumber) {
            state.board.trackNo[index] = guessedTrack.trackNumber + " ↓";
        } else {
            state.board.trackNo[index] = guessedTrack.trackNumber + " ✅";
        }

        if (guessedTrack.duration < state.randomTrack.duration) {
            state.board.length[index] = formatDuration(guessedTrack.duration) + " ↑";
        } else if (guessedTrack.duration > state.randomTrack.duration) {
            state.board.length[index] = formatDuration(guessedTrack.duration) + " ↓";
        } else {
            state.board.length[index] = formatDuration(guessedTrack.duration) + " ✅";
        }

        if (guessedTrack.features && state.randomTrack.features) {
            state.board.ft[index] = "Features ✅";
        } else if (!guessedTrack.features && !state.randomTrack.features) {
            state.board.ft[index] = "No features ✅";
        } else if (guessedTrack.features) {
            state.board.ft[index] = "Features";
        } else {
            state.board.ft[index] = "No features";
        }

        if (guessedTrack.explicit && state.randomTrack.explicit) {
            state.board.explicit[index] = "Explicit ✅";
        } else if (!guessedTrack.explicit && !state.randomTrack.explicit) {
            state.board.explicit[index] = "Not explicit ✅";
        } else if (guessedTrack.explicit) {
            state.board.explicit[index] = "Explicit";
        } else {
            state.board.explicit[index] = "Not explicit";
        }

        if (guessedTrack.name.equals(state.randomTrack.name)) {
            state.win = true;
        }

        if (state.win) {
            state.lives = Math.max(state.lives, 0);
            return new GuessResult(true, "¡Correcto! Adivinaste la canción.", summary(state));
        }

        if (state.lives == 0) {
            return new GuessResult(true, "Sin intentos restantes.", summary(state));
        }

        return new GuessResult(true, "Sigue intentando. Te quedan " + state.lives + " intentos.", summary(state));
    }

    private static String summary(GameState state) {
        if (state.win) {
            return "Ganaste en " + state.attempts + " intentos.";
        }
        if (state.lives == 0) {
            return "Perdiste. La canción era: " + state.randomTrack.name + ".";
        }
        return "Intentos usados: " + state.attempts + ".";
    }

    private static String formatDuration(double seconds) {
        int minutes = (int) (seconds / 60);
        int secs = (int) (seconds % 60);
        return String.format("%d:%02d", minutes, secs);
    }

    private static void sendJson(HttpExchange exchange, int status, Object body) throws IOException {
        byte[] response = mapper.writeValueAsBytes(body);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "application/json; charset=utf-8");
        headers.set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(status, response.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response);
        }
    }

    private static void sendText(HttpExchange exchange, int status, String body) throws IOException {
        byte[] response = body.getBytes(StandardCharsets.UTF_8);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(status, response.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(response);
        }
    }

    private static class StaticFileHandler implements HttpHandler {
        private final String filePath;

        StaticFileHandler(String filePath) {
            this.filePath = filePath;
        }

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            try {
                String content = new String(java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(filePath)), StandardCharsets.UTF_8);
                Headers headers = exchange.getResponseHeaders();
                headers.set("Content-Type", "text/html; charset=utf-8");
                exchange.sendResponseHeaders(200, content.getBytes(StandardCharsets.UTF_8).length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(content.getBytes(StandardCharsets.UTF_8));
                }
            } catch (IOException e) {
                exchange.sendResponseHeaders(404, -1);
            }
        }
    }

    private static class GameHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                Headers headers = exchange.getResponseHeaders();
                headers.set("Access-Control-Allow-Origin", "*");
                headers.set("Access-Control-Allow-Headers", "Content-Type");
                headers.set("Access-Control-Allow-Methods", "POST, OPTIONS");
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            try {
                JsonNode body = mapper.readTree(exchange.getRequestBody());
                String artist = body.hasNonNull("artist") ? body.get("artist").asText() : null;

                if (artist == null || artist.trim().isEmpty()) {
                    sendJson(exchange, 400, Map.of("error", "El campo 'artist' es requerido."));
                    return;
                }

                ensureAuthenticated();

                GameState state = createGame(artist);
                if (state == null || state.albums.isEmpty()) {
                    sendJson(exchange, 404, Map.of("error", "No se encontraron álbumes para este artista."));
                    return;
                }

                String gameId = UUID.randomUUID().toString();
                games.put(gameId, state);

                ObjectNode response = mapper.createObjectNode();
                response.put("gameId", gameId);
                response.put("artistName", state.artistName);
                response.put("message", "Juego iniciado. ¡Comienza a adivinar!");
                response.set("board", mapper.valueToTree(state.board));
                response.put("lives", state.lives);
                response.put("attempts", state.attempts);
                response.put("win", state.win);
                response.put("over", state.over());
                response.set("possibleTracks", mapper.valueToTree(state.possibleTracks));

                sendJson(exchange, 200, response);
            } catch (Exception e) {
                e.printStackTrace();
                sendJson(exchange, 500, Map.of("error", "Error al iniciar el juego: " + e.getMessage()));
            }
        }
    }

    private static class GuessHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                Headers headers = exchange.getResponseHeaders();
                headers.set("Access-Control-Allow-Origin", "*");
                headers.set("Access-Control-Allow-Headers", "Content-Type");
                headers.set("Access-Control-Allow-Methods", "POST, OPTIONS");
                exchange.sendResponseHeaders(204, -1);
                return;
            }

            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.sendResponseHeaders(405, -1);
                return;
            }

            URI requestURI = exchange.getRequestURI();
            String path = requestURI.getPath();
            String[] parts = path.split("/");

            if (parts.length < 4) {
                sendJson(exchange, 400, Map.of("error", "Formato de URL inválido."));
                return;
            }

            String gameId = parts[3];
            GameState state = games.get(gameId);

            if (state == null) {
                sendJson(exchange, 404, Map.of("error", "Juego no encontrado."));
                return;
            }

            try {
                JsonNode body = mapper.readTree(exchange.getRequestBody());
                String guess = body.hasNonNull("guess") ? body.get("guess").asText() : null;

                GuessResult result = processGuess(state, guess);

                if (!result.accepted) {
                    sendJson(exchange, 400, Map.of("error", result.message));
                    return;
                }

                ObjectNode response = mapper.createObjectNode();
                response.put("message", result.message);
                response.put("feedback", result.feedback);
                response.put("lives", state.lives);
                response.put("attempts", state.attempts);
                response.put("win", state.win);
                response.put("over", state.over());
                response.put("answer", state.over() ? state.randomTrack.name : "");
                response.set("board", mapper.valueToTree(state.board));

                sendJson(exchange, 200, response);
            } catch (Exception e) {
                e.printStackTrace();
                sendJson(exchange, 500, Map.of("error", "Error al procesar el intento: " + e.getMessage()));
            }
        }
    }

    static class TrackSelection {
        final Track track;
        final Album album;

        TrackSelection(Track track, Album album) {
            this.track = track;
            this.album = album;
        }
    }

    static class GuessResult {
        final boolean accepted;
        final String message;
        final String feedback;

        GuessResult(boolean accepted, String message, String feedback) {
            this.accepted = accepted;
            this.message = message;
            this.feedback = feedback;
        }
    }

    static class Track {
        String name;
        int trackNumber;
        double duration;
        boolean features;
        boolean explicit;
    }

    static class Album {
        String name;
        String releaseDate;
        List<Track> tracks;
    }

    @JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
    static class Board {
        String[] name = new String[8];
        String[] album = new String[8];
        String[] trackNo = new String[8];
        String[] length = new String[8];
        String[] ft = new String[8];
        String[] explicit = new String[8];

        Board() {
            Arrays.fill(name, "_");
            Arrays.fill(album, "_");
            Arrays.fill(trackNo, "_");
            Arrays.fill(length, "_");
            Arrays.fill(ft, "_");
            Arrays.fill(explicit, "_");
        }

        void print() {
            System.out.println("\n" + "=".repeat(120));
            System.out.printf("| %-30s | %-25s | %-10s | %-10s | %-15s | %-15s |%n",
                    "name", "album", "track no.", "length", "Ft.", "explicit");
            System.out.println("=".repeat(120));

            for (int i = 0; i < 8; i++) {
                System.out.printf("| %-30s | %-25s | %-10s | %-10s | %-15s | %-15s |%n",
                        truncate(name[i], 30),
                        truncate(album[i], 25),
                        trackNo[i],
                        length[i],
                        ft[i],
                        explicit[i]);
            }
            System.out.println("=".repeat(120) + "\n");
        }

        private String truncate(String str, int maxLen) {
            if (str.length() <= maxLen) return str;
            return str.substring(0, maxLen - 3) + "...";
        }
    }

    static class GameState {
        String artistId;
        String artistName;
        List<Album> albums;
        Album randomAlbum;
        Track randomTrack;
        Board board;
        Map<String, TrackSelection> trackLookup;
        List<String> possibleTracks;
        Set<String> guesses = new HashSet<>();
        int lives = 8;
        int attempts = 0;
        boolean win = false;

        boolean over() {
            return win || lives <= 0;
        }
    }
}
