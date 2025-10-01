import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.Base64;

public class Musicadle {
    private static final String CLIENT_ID = "";
    private static final String CLIENT_SECRET = "";
    private static String accessToken;
    private static final ObjectMapper mapper = new ObjectMapper();

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.print("Elige el artista: ");
        String artistName = scanner.nextLine();

        System.out.println("🎵 Iniciando Musicadle... 🎵");

        try {
            authenticate();

            JsonNode artistData = searchArtist(artistName);
            String artistUri = artistData.get("uri").asText();
            String artistId = artistData.get("id").asText();
            String finalArtistName = artistData.get("name").asText();

            List<Album> albums = getArtistAlbums(artistId);

            if (albums.isEmpty()) {
                System.out.println("No se encontraron álbumes para este artista.");
                return;
            }

            Random random = new Random();
            Album randomAlbum = albums.get(random.nextInt(albums.size()));
            Track randomTrack = randomAlbum.tracks.get(random.nextInt(randomAlbum.tracks.size()));

            System.out.println("🎵 ¡Bienvenido a Musicadle! 🎵");
            System.out.println("Adivina la canción de " + finalArtistName);

            Board board = new Board();
            board.print();

            Set<String> possibleTracks = new HashSet<>();
            for (Album album : albums) {
                for (Track track : album.tracks) {
                    possibleTracks.add(track.name.toLowerCase());
                }
            }

            Set<String> guesses = new HashSet<>();
            boolean win = false;
            int lives = 8;
            int attempts = 0;

            while (!win && lives > 0) {
                System.out.print("Guess the song: ");
                String guess = scanner.nextLine().toLowerCase();

                while (!possibleTracks.contains(guess) || guesses.contains(guess)) {
                    if (!possibleTracks.contains(guess)) {
                        System.out.print("NOT A SONG. Guess the song: ");
                    } else {
                        System.out.print("Already guessed. Guess another the song: ");
                    }
                    guess = scanner.nextLine().toLowerCase();
                }

                guesses.add(guess);

                Track guessedTrack = null;
                Album guessedAlbum = null;

                for (Album album : albums) {
                    for (Track track : album.tracks) {
                        if (track.name.toLowerCase().equals(guess)) {
                            guessedTrack = track;
                            guessedAlbum = album;
                            break;
                        }
                    }
                    if (guessedTrack != null) break;
                }

                board.name[attempts] = guessedTrack.name;

                int albumComparison = guessedAlbum.releaseDate.compareTo(randomAlbum.releaseDate);
                if (albumComparison < 0) {
                    board.album[attempts] = guessedAlbum.name + "↑";
                } else if (albumComparison > 0) {
                    board.album[attempts] = guessedAlbum.name + "↓";
                } else {
                    board.album[attempts] = guessedAlbum.name + " ✅";
                }

                if (guessedTrack.trackNumber < randomTrack.trackNumber) {
                    board.trackNo[attempts] = guessedTrack.trackNumber + "↑";
                } else if (guessedTrack.trackNumber > randomTrack.trackNumber) {
                    board.trackNo[attempts] = guessedTrack.trackNumber + "↓";
                } else {
                    board.trackNo[attempts] = guessedTrack.trackNumber + " ✅";
                }

                if (guessedTrack.duration < randomTrack.duration) {
                    board.length[attempts] = formatDuration(guessedTrack.duration) + "↑";
                } else if (guessedTrack.duration > randomTrack.duration) {
                    board.length[attempts] = formatDuration(guessedTrack.duration) + "↓";
                } else {
                    board.length[attempts] = formatDuration(guessedTrack.duration) + " ✅";
                }

                if (guessedTrack.features && randomTrack.features) {
                    board.ft[attempts] = "Features ✅";
                } else if (!guessedTrack.features && !randomTrack.features) {
                    board.ft[attempts] = "No features ✅";
                } else if (guessedTrack.features) {
                    board.ft[attempts] = "Features";
                } else {
                    board.ft[attempts] = "No features";
                }

                if (guessedTrack.explicit && randomTrack.explicit) {
                    board.explicit[attempts] = "Explicit ✅";
                } else if (!guessedTrack.explicit && !randomTrack.explicit) {
                    board.explicit[attempts] = "Not explicit ✅";
                } else if (guessedTrack.explicit) {
                    board.explicit[attempts] = "Explicit";
                } else {
                    board.explicit[attempts] = "Not explicit";
                }

                if (guessedTrack.name.equals(randomTrack.name)) {
                    win = true;
                }

                board.print();

                lives--;
                attempts++;
            }

            if (win) {
                System.out.println("✅ Ganaste en " + attempts + " intentos.");
            } else {
                System.out.println("❌ Perdiste. La cancion era: " + randomTrack.name + ".");
            }

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }

        scanner.close();
    }

    private static void authenticate() throws Exception {
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

            albums.add(album);
        }

        return albums;
    }

    private static String formatDuration(double seconds) {
        int minutes = (int) (seconds / 60);
        int secs = (int) (seconds % 60);
        return String.format("%d:%02d", minutes, secs);
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
}