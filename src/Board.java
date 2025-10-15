import java.util.Arrays;

public class Board {
    private String[] name = new String[8];
    private String[] album = new String[8];
    private String[] trackNo = new String[8];
    private String[] length = new String[8];
    private String[] ft = new String[8];
    private String[] explicit = new String[8];

    public Board() {
        Arrays.fill(name, "_");
        Arrays.fill(album, "_");
        Arrays.fill(trackNo, "_");
        Arrays.fill(length, "_");
        Arrays.fill(ft, "_");
        Arrays.fill(explicit, "_");
    }

    public void setName(int index, String value) {
        if (index >= 0 && index < 8) {
            name[index] = value;
        }
    }

    public void setAlbum(int index, String value) {
        if (index >= 0 && index < 8) {
            album[index] = value;
        }
    }

    public void setTrackNo(int index, String value) {
        if (index >= 0 && index < 8) {
            trackNo[index] = value;
        }
    }

    public void setLength(int index, String value) {
        if (index >= 0 && index < 8) {
            length[index] = value;
        }
    }

    public void setFt(int index, String value) {
        if (index >= 0 && index < 8) {
            ft[index] = value;
        }
    }

    public void setExplicit(int index, String value) {
        if (index >= 0 && index < 8) {
            explicit[index] = value;
        }
    }

    public void print() {
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