package top.met6.metmusic.data;

import java.util.List;
import java.util.stream.Collectors;

public class SearchResult {
    private final String mid;
    private final String name;
    private final List<String> singers;
    private final String albumName;

    public SearchResult(String mid, String name, List<String> singers, String albumName) {
        this.mid = mid;
        this.name = name;
        this.singers = singers;
        this.albumName = albumName;
    }

    public String getMid() {
        return mid;
    }

    public String getName() {
        return name;
    }

    public List<String> getSingers() {
        return singers;
    }

    public String getAlbumName() {
        return albumName;
    }

    public String getFormattedTitle() {
        String singerString = singers.stream().collect(Collectors.joining(" / "));
        return name + " - " + singerString;
    }
}