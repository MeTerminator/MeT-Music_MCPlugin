package top.met6.metmusic.data;

import java.util.List;

public class PlaylistSong {
    private String id;
    private String name;
    private List<String> singers;
    private String albumName;

    public PlaylistSong(String id, String name, List<String> singers, String albumName) {
        this.id = id;
        this.name = name;
        this.singers = singers;
        this.albumName = albumName;
    }

    public String getId() {
        return id;
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
        return name + " - " + String.join(" & ", singers);
    }
}