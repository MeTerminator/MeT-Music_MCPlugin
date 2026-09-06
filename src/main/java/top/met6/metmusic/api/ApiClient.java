package top.met6.metmusic.api;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import top.met6.metmusic.MetMusicPlugin;
import top.met6.metmusic.data.LrcLine;
import top.met6.metmusic.data.PlaylistSong;
import top.met6.metmusic.data.SongInfo;
import top.met6.metmusic.data.SearchResult;
import top.met6.metmusic.data.SearchPage;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ApiClient {

    private static final String USER_AGENT = "MeT-Music_MCPlugin";
    private static final String REPORT_URL = "https://music.met6.top:444/api/v1/collect/feedback/mcplugin";
    private static final String SONG_URL_API = "https://music.met6.top:444/api/web/song/url/v1?id=%s&level=hq";
    private static final String LYRIC_API = "https://music.met6.top:444/api/v1/lrc?mid=%s";
    private static final String SEARCH_API = "https://music.met6.top:444/api/web/cloudsearch?keywords=%s&limit=%d&offset=%d&type=1";
    private static final String PLAYLIST_DETAIL_API = "https://music.met6.top:444/api/web/playlist/detail?id=%s";
    private static final String PLAYLIST_TRACKS_API = "https://music.met6.top:444/api/web/playlist/track/all?id=%s&limit=%d&offset=%d";
    private final Map<String, List<PlaylistSong>> playlistCache = new ConcurrentHashMap<>();
    private final Map<String, Integer> playlistTotalCountCache = new ConcurrentHashMap<>();
    private final Gson gson = new Gson();
    private final Logger logger;
    private final Pattern lrcPattern = Pattern.compile("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})\\](.*)");

    private final Map<String, SongInfo> songInfoCache = new ConcurrentHashMap<>();
    private final Map<String, List<LrcLine>> lyricCache = new ConcurrentHashMap<>();

    public ApiClient(Logger logger) {
        this.logger = logger;
    }

    public void reportPlaybackStatus(String songMid, boolean status, long currentTime, String eventType) {
        JsonObject data = new JsonObject();
        data.addProperty("event", eventType);
        data.addProperty("sessionId", MetMusicPlugin.getInstance().getPluginConfig().getSessionId());
        data.addProperty("userId", (String) null);
        data.addProperty("songMid", songMid);
        data.addProperty("status", status);
        data.addProperty("currentTime", currentTime);
        data.addProperty("systemTime", System.currentTimeMillis());

        JsonObject requestBody = new JsonObject();
        requestBody.add("data", data);

        MetMusicPlugin.getInstance().getServer().getScheduler().runTaskAsynchronously(MetMusicPlugin.getInstance(), () -> {
            try {
                URL url = new URL(REPORT_URL);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("User-Agent", USER_AGENT);
                conn.setDoOutput(true);

                try (OutputStream os = conn.getOutputStream()) {
                    byte[] input = gson.toJson(requestBody).getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }

                int responseCode = conn.getResponseCode();
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    logger.warning("上报播放状态失败，响应码: " + responseCode);
                }
            } catch (Exception e) {
                logger.severe("上报播放状态发生错误: " + e.getMessage());
            }
        });
    }

    public SongInfo getSongInfo(String mid) {
        if (songInfoCache.containsKey(mid)) {
            return songInfoCache.get(mid);
        }

        try {
            URL url = new URL(String.format(SONG_URL_API, mid));
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    StringBuilder response = new StringBuilder();
                    while ((line = in.readLine()) != null) {
                        response.append(line);
                    }
                    JsonObject jsonResponse = JsonParser.parseString(response.toString()).getAsJsonObject();
                    JsonArray dataArray = jsonResponse.getAsJsonArray("data");
                    if (dataArray != null && dataArray.size() > 0) {
                        JsonObject trackData = dataArray.get(0).getAsJsonObject();
                        JsonObject trackInfo = trackData.getAsJsonObject("track_info");

                        // 新增无效歌曲检查
                        if (trackInfo.get("id").getAsInt() == 0) {
                            logger.warning("歌曲ID为0，判定为无效歌曲。");
                            return null;
                        }

                        String songUrl = trackData.get("url").getAsString();
                        long durationMillis = trackData.get("time").getAsLong();
                        String title = trackInfo.get("title").getAsString();
                        String albumName = trackInfo.getAsJsonObject("album").get("name").getAsString();

                        List<String> singers = new ArrayList<>();
                        for (var singer : trackInfo.getAsJsonArray("singer")) {
                            singers.add(singer.getAsJsonObject().get("name").getAsString());
                        }

                        List<LrcLine> lrcLines = getLyric(mid);

                        SongInfo songInfo = new SongInfo(songUrl, durationMillis, title, singers, albumName, lrcLines);
                        songInfoCache.put(mid, songInfo);
                        return songInfo;
                    }
                }
            } else {
                logger.warning("获取歌曲信息失败，MID: " + mid + ", 响应码: " + responseCode);
            }
        } catch (Exception e) {
            logger.severe("获取歌曲信息发生错误，MID: " + mid + ", 错误: " + e.getMessage());
        }
        return null;
    }

    public List<LrcLine> getLyric(String mid) {
        if (lyricCache.containsKey(mid)) {
            return lyricCache.get(mid);
        }

        List<LrcLine> lrcLines = new ArrayList<>();
        try {
            URL url = new URL(String.format(LYRIC_API, mid));
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = in.readLine()) != null) {
                        Matcher matcher = lrcPattern.matcher(line);
                        if (matcher.matches()) {
                            int minutes = Integer.parseInt(matcher.group(1));
                            int seconds = Integer.parseInt(matcher.group(2));
                            int milliseconds = Integer.parseInt(matcher.group(3));
                            if (matcher.group(3).length() == 2) {
                                milliseconds *= 10;
                            }
                            long timeMillis = minutes * 60 * 1000 + seconds * 1000 + milliseconds;
                            String lyricText = matcher.group(4).trim();
                            if (!lyricText.isEmpty()) {
                                lrcLines.add(new LrcLine(timeMillis, lyricText));
                            }
                        }
                    }
                }
            } else {
                logger.warning("获取LRC歌词失败，MID: " + mid + ", 响应码: " + responseCode);
            }
        } catch (Exception e) {
            logger.severe("获取LRC歌词发生错误，MID: " + mid + ", 错误: " + e.getMessage());
        }
        lyricCache.put(mid, lrcLines);
        return lrcLines;
    }

    public SearchPage searchSongs(String keyword, int page, int limit) {
        if (page < 1 || limit < 1) {
            throw new IllegalArgumentException("page and limit must be positive");
        }
        int offset = (page - 1) * limit;

        List<SearchResult> results = new ArrayList<>();
        int totalCount = 0;
        try {
            String encodedKeyword = java.net.URLEncoder.encode(keyword, StandardCharsets.UTF_8.toString());
            URL url = new URL(String.format(SEARCH_API, encodedKeyword, limit, offset));
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("User-Agent", USER_AGENT);
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);

            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    StringBuilder response = new StringBuilder();
                    while ((line = in.readLine()) != null) {
                        response.append(line);
                    }
                    JsonObject jsonResponse = JsonParser.parseString(response.toString()).getAsJsonObject();
                    JsonObject resultObject = jsonResponse.getAsJsonObject("result");
                    JsonArray songsArray = resultObject.getAsJsonArray("songs");
                    totalCount = resultObject.get("songCount").getAsInt();

                    for (var songJson : songsArray) {
                        JsonObject song = songJson.getAsJsonObject();
                        String mid = song.get("id").getAsString();
                        String name = song.get("name").getAsString();
                        String albumName = song.getAsJsonObject("al").get("name").getAsString();

                        List<String> singers = new ArrayList<>();
                        for (var singerJson : song.getAsJsonArray("ar")) {
                            singers.add(singerJson.getAsJsonObject().get("name").getAsString());
                        }

                        results.add(new SearchResult(mid, name, singers, albumName));
                    }
                }
            } else {
                logger.warning("搜索失败，关键字: " + keyword + ", 响应码: " + responseCode);
                return null;
            }
        } catch (Exception e) {
            logger.severe("搜索发生错误，关键字: " + keyword + ", 错误: " + e.getMessage());
            return null;
        }

        return new SearchPage(results, totalCount);
    }

    /**
     * 获取歌单中的所有歌曲，并进行缓存
     * @param playlistId 歌单ID
     * @return 歌单中的歌曲列表
     */
    public List<PlaylistSong> getPlaylistSongs(String playlistId) {
        if (playlistCache.containsKey(playlistId)) {
            return playlistCache.get(playlistId);
        }

        List<PlaylistSong> allSongs = new ArrayList<>();
        try {
            // 首先获取歌单详情，得到总歌曲数
            URL detailUrl = new URL(String.format(PLAYLIST_DETAIL_API, playlistId));
            HttpURLConnection detailConn = (HttpURLConnection) detailUrl.openConnection();
            detailConn.setRequestProperty("User-Agent", USER_AGENT);
            detailConn.setRequestMethod("GET");
            int detailResponseCode = detailConn.getResponseCode();

            if (detailResponseCode != HttpURLConnection.HTTP_OK) {
                logger.warning("获取歌单详情失败，ID: " + playlistId + ", 响应码: " + detailResponseCode);
                return null;
            }

            String detailResponse = new BufferedReader(new InputStreamReader(detailConn.getInputStream(), StandardCharsets.UTF_8))
                    .lines().collect(Collectors.joining("\n"));
            JsonObject detailJson = JsonParser.parseString(detailResponse).getAsJsonObject();

            JsonObject playlistObject = detailJson.getAsJsonObject("playlist");
            if (playlistObject == null || playlistObject.get("createTime").getAsLong() == 0) {
                logger.warning("歌单ID " + playlistId + " 无效或不存在。");
                return null;
            }

            int totalSongs = detailJson.getAsJsonObject("playlist").get("trackCount").getAsInt();
            playlistTotalCountCache.put(playlistId, totalSongs);

            // 然后根据总歌曲数获取所有歌曲
            URL tracksUrl = new URL(String.format(PLAYLIST_TRACKS_API, playlistId, totalSongs, 0));
            HttpURLConnection tracksConn = (HttpURLConnection) tracksUrl.openConnection();
            tracksConn.setRequestProperty("User-Agent", USER_AGENT);
            tracksConn.setRequestMethod("GET");
            int tracksResponseCode = tracksConn.getResponseCode();

            if (tracksResponseCode == HttpURLConnection.HTTP_OK) {
                String tracksResponse = new BufferedReader(new InputStreamReader(tracksConn.getInputStream(), StandardCharsets.UTF_8))
                        .lines().collect(Collectors.joining("\n"));
                JsonObject tracksJson = JsonParser.parseString(tracksResponse).getAsJsonObject();
                JsonArray songsArray = tracksJson.getAsJsonArray("songs");

                for (var songJson : songsArray) {
                    JsonObject song = songJson.getAsJsonObject();
                    String id = song.get("id").getAsString();
                    String name = song.get("name").getAsString();
                    String albumName = song.getAsJsonObject("al").get("name").getAsString();

                    List<String> singers = new ArrayList<>();
                    for (var singerJson : song.getAsJsonArray("ar")) {
                        singers.add(singerJson.getAsJsonObject().get("name").getAsString());
                    }

                    allSongs.add(new PlaylistSong(id, name, singers, albumName));
                }
            } else {
                logger.warning("获取歌单歌曲失败，ID: " + playlistId + ", 响应码: " + tracksResponseCode);
                return null;
            }
        } catch (Exception e) {
            logger.severe("获取歌单信息发生错误，ID: " + playlistId + ", 错误: " + e.getMessage());
            return null;
        }

        playlistCache.put(playlistId, allSongs);
        return allSongs;
    }

    public int getPlaylistTotalCount(String playlistId) {
        return playlistTotalCountCache.getOrDefault(playlistId, 0);
    }

}
