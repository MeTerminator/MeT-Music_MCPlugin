package top.met6.metmusic.data;

import java.util.List;
import java.util.stream.Collectors;

public class SongData {
    private final String mid;
    private final String url;
    private final List<LrcLine> lrcLines;
    private final long durationMillis;
    private final String title;
    private final List<String> singers;
    private final String albumName;
    private final String playerName; // 新增字段
    private long currentTime = 0;

    // 修改构造函数以接受 playerName 参数
    public SongData(String mid, String url, List<LrcLine> lrcLines, long durationMillis, String title, List<String> singers, String albumName, String playerName) {
        this.mid = mid;
        this.url = url;
        this.lrcLines = lrcLines;
        this.durationMillis = durationMillis;
        this.title = title;
        this.singers = singers;
        this.albumName = albumName;
        this.playerName = playerName; // 初始化新增字段
    }

    public String getMid() {
        return mid;
    }

    public String getUrl() {
        return url;
    }

    public List<LrcLine> getLrcLines() {
        return lrcLines;
    }

    public long getCurrentTime() {
        return currentTime;
    }

    public void setCurrentTime(long currentTime) {
        this.currentTime = currentTime;
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

    // 新增获取玩家名的方法
    public String getPlayerName() {
        return playerName;
    }

    public String getFormattedTitle() {
        String singerString = singers.stream().collect(Collectors.joining(" / "));
        return title + " - " + singerString;
    }

    public String getFormattedSingers() {
        return singers.stream().collect(Collectors.joining(" / "));
    }
}