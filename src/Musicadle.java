import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Base64;

public class Musicadle {
    private static final String CLIENT_ID = "CLIENT_ID";
    private static final String CLIENT_SECRET = "CLIENT_SECRET";
    private String accessToken;
    private ObjectMapper objectMapper;

    private Artist artist;
    private Map<String, List<String>> board;
    private Album randomAlbum;
    private Track randomTrack;
    private Scanner scanner;

    public Musicadle(String artistName) throws Exception {
        this.objectMapper = new ObjectMapper();
        this.scanner = new Scanner(System.in);

        System.out.println("🎵 Iniciando Musicadle... 🎵");

        // Obtener token de acceso
        this.accessToken = getAccessToken();

        // Obtener artista
        this.artist = getArtist(artistName);
        this.board = getBoard();

        // Seleccionar album y track aleatorio
        Random random = new Random();
        this.randomAlbum = artist.albums.get(random.nextInt(artist.albums.size()));
        this.randomTrack = randomAlbum.tracks.get(random.nextInt(randomAlbum.tracks.size()));

        System.out.println("🎵 ¡Bienvenido a Musicadle! 🎵");
        System.out.println("Adivina la canción de " + this.artist.name);
    }

    private String getAccessToken() throws Exception {
        String auth = Base64.getEncoder().encodeToString((CLIENT_ID + ":" + CLIENT_SECRET).getBytes());

        URL url = new URL("https://accounts.spotify.com/api/token");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        conn.setRequestMethod("POST");
        conn.setRequestProperty("Authorization", "Basic " + auth);
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
        conn.setDoOutput(true);

        String data = "grant_type=client_credentials";

        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = data.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            String responseLine;
            while ((responseLine = br.readLine()) != null) {
                response.append(responseLine.trim());
            }

            JsonNode jsonResponse = objectMapper.readTree(response.toString());
            return jsonResponse.get("access_token").asText();
        }
    }

    private JsonNode makeSpotifyRequest(String endpoint) throws Exception {
        URL url = new URL("https://api.spotify.com/v1/" + endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        conn.setRequestMethod("GET");
        conn.setRequestProperty("Authorization", "Bearer " + accessToken);

        try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            String responseLine;
            while ((responseLine = br.readLine()) != null) {
                response.append(responseLine.trim());
            }

            return objectMapper.readTree(response.toString());
        }
    }

    private Artist getArtist(String artistName) throws Exception {
        String encodedName = URLEncoder.encode(artistName, StandardCharsets.UTF_8.toString());
        JsonNode searchResult = makeSpotifyRequest("search?q=" + encodedName + "&type=artist&limit=1");

        JsonNode artistData = searchResult.get("artists").get("items").get(0);
        String artistId = artistData.get("id").asText();
        String name = artistData.get("name").asText();

        // Obtener albums del artista
        JsonNode albumsResult = makeSpotifyRequest("artists/" + artistId + "/albums?include_groups=album&limit=50");
        List<Album> albums = new ArrayList<>();

        for (JsonNode albumItem : albumsResult.get("items")) {
            String albumId = albumItem.get("id").asText();
            albums.add(getAlbum(albumId));
        }

        return new Artist(name, albums);
    }

    private Album getAlbum(String albumId) throws Exception {
        JsonNode albumData = makeSpotifyRequest("albums/" + albumId);

        String name = albumData.get("name").asText();
        String releaseDate = albumData.get("release_date").asText();

        List<Track> tracks = new ArrayList<>();
        for (JsonNode trackItem : albumData.get("tracks").get("items")) {
            String trackId = trackItem.get("id").asText();
            tracks.add(getTrack(trackId));
        }

        return new Album(name, releaseDate, tracks);
    }

    private Track getTrack(String trackId) throws Exception {
        JsonNode trackData = makeSpotifyRequest("tracks/" + trackId);

        String name = trackData.get("name").asText();
        int trackNumber = trackData.get("track_number").asInt();
        double duration = trackData.get("duration_ms").asDouble() / 1000.0;
        boolean features = trackData.get("artists").size() > 1;
        boolean explicit = trackData.get("explicit").asBoolean();

        return new Track(name, trackNumber, duration, features, explicit);
    }

    private Map<String, List<String>> getBoard() {
        Map<String, List<String>> board = new HashMap<>();
        String[] headers = {"name", "album", "track no.", "length", "Ft.", "explicit"};

        for (String header : headers) {
            List<String> row = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                row.add("_");
            }
            board.put(header, row);
        }

        return board;
    }

    public void playGame() {
        boolean win = false;
        int lives = 8;
        int attempt = 0;

        printBoard();

        // Obtener todas las canciones posibles
        Set<String> possibleTracks = new HashSet<>();
        for (Album album : artist.albums) {
            for (Track track : album.tracks) {
                possibleTracks.add(track.name.toLowerCase());
            }
        }

        Set<String> guesses = new HashSet<>();

        while (!win && lives > 0) {
            System.out.print("Guess the song: ");
            String guess = scanner.nextLine().toLowerCase();

            while (!possibleTracks.contains(guess) || guesses.contains(guess)) {
                if (!possibleTracks.contains(guess)) {
                    System.out.print("NOT A SONG. Guess the song: ");
                } else {
                    System.out.print("Already guessed. Guess another song: ");
                }
                guess = scanner.nextLine().toLowerCase();
            }

            // Encontrar el track correspondiente
            Track guessedTrack = null;
            Album guessedAlbum = null;

            for (Album album : artist.albums) {
                for (Track track : album.tracks) {
                    if (guess.equals(track.name.toLowerCase())) {
                        guessedTrack = track;
                        guessedAlbum = album;
                        break;
                    }
                }
                if (guessedTrack != null) break;
            }

            guesses.add(guess);

            // Actualizar board
            board.get("name").set(attempt, guessedTrack.name);

            // Comparar fechas de album
            int dateComparison = guessedAlbum.releaseDate.compareTo(randomAlbum.releaseDate);
            if (dateComparison < 0) {
                board.get("album").set(attempt, "↑");
            } else if (dateComparison > 0) {
                board.get("album").set(attempt, "↓");
            } else {
                board.get("album").set(attempt, "✅");
            }

            // Comparar número de track
            if (guessedTrack.trackNumber < randomTrack.trackNumber) {
                board.get("track no.").set(attempt, guessedTrack.trackNumber + "↑");
            } else if (guessedTrack.trackNumber > randomTrack.trackNumber) {
                board.get("track no.").set(attempt, guessedTrack.trackNumber + "↓");
            } else {
                board.get("track no.").set(attempt, "✅");
            }

            // Comparar duración
            if (guessedTrack.duration < randomTrack.duration) {
                board.get("length").set(attempt, guessedTrack.formatDuration() + "↑");
            } else if (guessedTrack.duration > randomTrack.duration) {
                board.get("length").set(attempt, guessedTrack.formatDuration() + "↓");
            } else {
                board.get("length").set(attempt, "✅");
            }

            // Comparar features
            if (guessedTrack.features == randomTrack.features) {
                board.get("Ft.").set(attempt, "✅");
            } else {
                board.get("Ft.").set(attempt, "❌");
            }

            // Comparar explicit
            if (guessedTrack.explicit == randomTrack.explicit) {
                board.get("explicit").set(attempt, "✅");
            } else {
                board.get("explicit").set(attempt, "❌");
            }

            if (guessedTrack.name.equals(randomTrack.name)) {
                win = true;
            }

            printBoard();

            lives--;
            attempt++;
        }

        if (win) {
            System.out.println("✅ Ganaste en " + attempt + " intentos.");
        } else {
            System.out.println("❌ Perdiste. La cancion era: " + randomTrack.name + ".");
        }
    }

    private void printBoard() {
        // Header
        System.out.printf("| %-20s | %-8s | %-10s | %-8s | %-4s | %-8s |\n",
                "name", "album", "track no.", "length", "Ft.", "explicit");
        System.out.println("|" + "-".repeat(22) + "|" + "-".repeat(10) + "|" +
                "-".repeat(12) + "|" + "-".repeat(10) + "|" + "-".repeat(6) + "|" +
                "-".repeat(10) + "|");

        // Rows
        for (int i = 0; i < 8; i++) {
            System.out.printf("| %-20s | %-8s | %-10s | %-8s | %-4s | %-8s |\n",
                    board.get("name").get(i),
                    board.get("album").get(i),
                    board.get("track no.").get(i),
                    board.get("length").get(i),
                    board.get("Ft.").get(i),
                    board.get("explicit").get(i));
        }
        System.out.println();
    }

    public static void main(String[] args) {
        try {
            Musicadle game = new Musicadle("ARTIST_NAME");
            game.playGame();
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

class Artist {
    String name;
    List<Album> albums;

    public Artist(String name, List<Album> albums) {
        this.name = name;
        this.albums = albums;
    }
}

class Album {
    String name;
    String releaseDate;
    List<Track> tracks;

    public Album(String name, String releaseDate, List<Track> tracks) {
        this.name = name;
        this.releaseDate = releaseDate;
        this.tracks = tracks;
    }
}

class Track {
    String name;
    int trackNumber;
    double duration;
    boolean features;
    boolean explicit;

    public Track(String name, int trackNumber, double duration, boolean features, boolean explicit) {
        this.name = name;
        this.trackNumber = trackNumber;
        this.duration = duration;
        this.features = features;
        this.explicit = explicit;
    }

    public String formatDuration() {
        int totalSeconds = (int) duration;
        int minutes = totalSeconds / 60;
        int seconds = totalSeconds % 60;

        return String.format("%d:%02d", minutes, seconds);
    }
}