import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public class SpotifyService {
    private final String clientId;
    private final String clientSecret;
    private String accessToken;
    private final ObjectMapper mapper;

    public SpotifyService(String clientId, String clientSecret) {
        this.clientId = "";
        this.clientSecret = "";
        this.mapper = new ObjectMapper();
    }

    public void authenticate() throws Exception {
        String auth = clientId + ":" + clientSecret;
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

    private JsonNode makeSpotifyRequest(String endpoint) throws Exception {
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

    public JsonNode searchArtist(String artistName) throws Exception {
        String encoded = URLEncoder.encode(artistName, StandardCharsets.UTF_8.toString());
        JsonNode result = makeSpotifyRequest("search?q=" + encoded + "&type=artist&limit=1");
        return result.get("artists").get("items").get(0);
    }

    public List<Album> getArtistAlbums(String artistId) throws Exception {
        JsonNode albumsData = makeSpotifyRequest("artists/" + artistId + "/albums?include_groups=album");
        List<Album> albums = new ArrayList<>();

        for (JsonNode albumItem : albumsData.get("items")) {
            String albumId = albumItem.get("id").asText();
            JsonNode albumData = makeSpotifyRequest("albums/" + albumId);

            Album album = new Album();
            album.setName(albumData.get("name").asText());
            album.setReleaseDate(albumData.get("release_date").asText());

            for (JsonNode trackItem : albumData.get("tracks").get("items")) {
                Track track = new Track();
                track.setName(trackItem.get("name").asText());
                track.setTrackNumber(trackItem.get("track_number").asInt());
                track.setDuration(trackItem.get("duration_ms").asInt() / 1000.0);
                track.setFeatures(trackItem.get("artists").size() > 1);
                track.setExplicit(trackItem.get("explicit").asBoolean());

                album.addTrack(track);
            }

            albums.add(album);
        }

        return albums;
    }

}
