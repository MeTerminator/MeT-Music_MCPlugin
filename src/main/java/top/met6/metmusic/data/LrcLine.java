package top.met6.metmusic.data;

public class LrcLine {
    private final long timeMillis; // 时间戳，单位：毫秒
    private final String text;

    public LrcLine(long timeMillis, String text) {
        this.timeMillis = timeMillis;
        this.text = text;
    }

    public long getTimeMillis() {
        return timeMillis;
    }

    public String getText() {
        return text;
    }
}