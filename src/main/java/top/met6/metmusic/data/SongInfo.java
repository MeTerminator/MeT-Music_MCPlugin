package top.met6.metmusic.data;

import java.util.List;

public class SongInfo {
    private final String songUrl;
    private final long durationMillis;
    private final String title;
    private final List<String> singers;
    private final String albumName;
    private final List<LrcLine> lrcLines;

    public SongInfo(String songUrl, long durationMillis, String title, List<String> singers, String albumName, List<LrcLine> lrcLines) {
        this.songUrl = songUrl;
        this.durationMillis = durationMillis;
        this.title = title;
        this.singers = singers;
        this.albumName = albumName;
        this.lrcLines = lrcLines;
    }

    public String getSongUrl() {
        return songUrl;
    }

    public long getDurationMillis() {
        return durationMillis;
    }

    public String getTitle() {
        return title;
    }

    public List<String> getSingers() {
        return singers;
    }

    public String getAlbumName() {
        return albumName;
    }

    public List<LrcLine> getLrcLines() {
        return lrcLines;
    }
}