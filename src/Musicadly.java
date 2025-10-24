import com.fasterxml.jackson.databind.JsonNode;
import java.util.*;

public class Musicadly {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.println("creadores: francico, javier y dante");
        System.out.println("🎵 ¡Bienvenido a Musicadly! 🎵");
        System.out.println("¡disfruten este juego!");
        System.out.print("Elegí un artista: ");
        String artistName = scanner.nextLine();

        System.out.println("🎵 Iniciando Musicadly... 🎵");

        try {
            SpotifyService spotifyService = new SpotifyService();
            spotifyService.authenticate();

            JsonNode artistData = spotifyService.searchArtist(artistName);
            String artistId = artistData.get("id").asText();
            String finalArtistName = artistData.get("name").asText();

            List<Album> albums = spotifyService.getArtistAlbums(artistId);

            if (albums.isEmpty()) {
                System.out.println("No se encontraron álbumes para este artista.");
                return;
            }

            Random random = new Random();
            Album randomAlbum = albums.get(random.nextInt(albums.size()));
            Track randomTrack = randomAlbum.getTracks().get(random.nextInt(randomAlbum.getTracks().size()));

            System.out.println("Adiviná la canción de " + finalArtistName);

            GameController gameController = new GameController(scanner);
            gameController.initializePossibleTracks(albums);
            gameController.showBoard();

            boolean win = gameController.play(albums, randomTrack, randomAlbum);

            if (win) {
                if (gameController.getAttempts() == 1) {
                    System.out.println("✅ Ganaste a la primera locoo");

                } else {
                    System.out.println("✅ Ganaste en " + gameController.getAttempts() + " intentos.");
                }
            } else {
                System.out.println("❌ Perdiste. La cancion era: " + randomTrack.getName() + ".");
            }

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }

        scanner.close();
    }
}
