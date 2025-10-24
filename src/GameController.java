import java.util.*;

public class GameController {
    private final Board board;
    private final Scanner scanner;
    private Set<String> possibleTracks;
    private Set<String> guesses;
    private int lives;
    private int attempts;

    public GameController(Scanner scanner) {
        this.board = new Board();
        this.scanner = scanner;
        this.guesses = new HashSet<>();
        this.lives = 8;
        this.attempts = 0;
    }

    public void initializePossibleTracks(List<Album> albums) {
        possibleTracks = new HashSet<>();
        for (Album album : albums) {
            for (Track track : album.getTracks()) {
                possibleTracks.add(track.getName().toLowerCase());
            }
        }
    }

    public void showBoard() {
        board.print();
    }

    public boolean play(List<Album> albums, Track randomTrack, Album randomAlbum) {
        boolean win = false;

        while (!win && lives > 0) {
            System.out.print("Escribí una cancion: ");
            String guess = scanner.nextLine().toLowerCase();

            while (!possibleTracks.contains(guess) || guesses.contains(guess)) {
                if (!possibleTracks.contains(guess)) {
                    System.out.println("No se encuentra la canción en la lista de canciones.");
                    System.out.print("Escribí una cancion: ");
                } else {
                    System.out.println("Esta ya la pusiste antes.");
                    System.out.print("Escribí una cancion: ");
                }
                guess = scanner.nextLine().toLowerCase();
            }

            guesses.add(guess);

            Track guessedTrack = null;
            Album guessedAlbum = null;

            for (Album album : albums) {
                for (Track track : album.getTracks()) {
                    if (track.getName().toLowerCase().equals(guess)) {
                        guessedTrack = track;
                        guessedAlbum = album;
                        break;
                    }
                }
                if (guessedTrack != null) break;
            }

            updateBoard(guessedTrack, guessedAlbum, randomTrack, randomAlbum);

            if (guessedTrack.getName().equals(randomTrack.getName())) {
                win = true;
            }

            board.print();

            lives--;
            attempts++;
        }

        return win;
    }

    private void updateBoard(Track guessedTrack, Album guessedAlbum, Track randomTrack, Album randomAlbum) {
        board.setName(attempts, guessedTrack.getName());

        int albumComparison = guessedAlbum.getReleaseDate().compareTo(randomAlbum.getReleaseDate());
        if (albumComparison < 0) {
            board.setAlbum(attempts, guessedAlbum.getName() + "↑");
        } else if (albumComparison > 0) {
            board.setAlbum(attempts, guessedAlbum.getName() + "↓");
        } else {
            board.setAlbum(attempts, guessedAlbum.getName() + " ✅");
        }

        if (guessedTrack.getTrackNumber() < randomTrack.getTrackNumber()) {
            board.setTrackNo(attempts, guessedTrack.getTrackNumber() + "↑");
        } else if (guessedTrack.getTrackNumber() > randomTrack.getTrackNumber()) {
            board.setTrackNo(attempts, guessedTrack.getTrackNumber() + "↓");
        } else {
            board.setTrackNo(attempts, guessedTrack.getTrackNumber() + " ✅");
        }

        if (guessedTrack.getDuration() < randomTrack.getDuration()) {
            board.setLength(attempts, formatDuration(guessedTrack.getDuration()) + "↑");
        } else if (guessedTrack.getDuration() > randomTrack.getDuration()) {
            board.setLength(attempts, formatDuration(guessedTrack.getDuration()) + "↓");
        } else {
            board.setLength(attempts, formatDuration(guessedTrack.getDuration()) + " ✅");
        }

        if (guessedTrack.hasFeatures() && randomTrack.hasFeatures()) {
            board.setFt(attempts, "Features ✅");
        } else if (!guessedTrack.hasFeatures() && !randomTrack.hasFeatures()) {
            board.setFt(attempts, "No features ✅");
        } else if (guessedTrack.hasFeatures()) {
            board.setFt(attempts, "Features");
        } else {
            board.setFt(attempts, "No features");
        }

        if (guessedTrack.isExplicit() && randomTrack.isExplicit()) {
            board.setExplicit(attempts, "Explicit ✅");
        } else if (!guessedTrack.isExplicit() && !randomTrack.isExplicit()) {
            board.setExplicit(attempts, "Not explicit ✅");
        } else if (guessedTrack.isExplicit()) {
            board.setExplicit(attempts, "Explicit");
        } else {
            board.setExplicit(attempts, "Not explicit");
        }
    }

    private String formatDuration(double seconds) {
        int minutes = (int) (seconds / 60);
        int secs = (int) (seconds % 60);
        return String.format("%d:%02d", minutes, secs);
    }

    public int getAttempts() {
        return attempts;
    }
}