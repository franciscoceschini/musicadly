import java.util.ArrayList;
import java.util.List;

public class Album {
    private String name;
    private String releaseDate;
    private List<Track> tracks;

    public Album() {
        this.tracks = new ArrayList<>();
    }

    public Album(String name, String releaseDate) {
        this.name = name;
        this.releaseDate = releaseDate;
        this.tracks = new ArrayList<>();
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getReleaseDate() {
        return releaseDate;
    }

    public void setReleaseDate(String releaseDate) {
        this.releaseDate = releaseDate;
    }

    public List<Track> getTracks() {
        return tracks;
    }

    public void setTracks(List<Track> tracks) {
        this.tracks = tracks;
    }

    public void addTrack(Track track) {
        this.tracks.add(track);
    }
}