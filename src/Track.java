public class Track {
    private String name;
    private int trackNumber;
    private double duration;
    private boolean features;
    private boolean explicit;

    public Track() {
    }

    public Track(String name, int trackNumber, double duration, boolean features, boolean explicit) {
        this.name = name;
        this.trackNumber = trackNumber;
        this.duration = duration;
        this.features = features;
        this.explicit = explicit;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getTrackNumber() {
        return trackNumber;
    }

    public void setTrackNumber(int trackNumber) {
        this.trackNumber = trackNumber;
    }

    public double getDuration() {
        return duration;
    }

    public void setDuration(double duration) {
        this.duration = duration;
    }

    public boolean hasFeatures() {
        return features;
    }

    public void setFeatures(boolean features) {
        this.features = features;
    }

    public boolean isExplicit() {
        return explicit;
    }

    public void setExplicit(boolean explicit) {
        this.explicit = explicit;
    }
}