package top.met6.metmusic.manager;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import top.met6.metmusic.MetMusicPlugin;
import top.met6.metmusic.api.ApiClient;
import top.met6.metmusic.bossbar.LyricBossbarDisplay;
import top.met6.metmusic.data.SongData;
import top.met6.metmusic.data.SongInfo;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

public class MusicPlaybackManager {

    private final MetMusicPlugin plugin;
    private final ApiClient apiClient;
    private final LyricBossbarDisplay bossbarDisplay;

    private final LinkedList<SongData> playlist = new LinkedList<>();
    private SongData currentSong;
    private BukkitTask reportTask;

    public MusicPlaybackManager(MetMusicPlugin plugin, ApiClient apiClient) {
        this.plugin = plugin;
        this.apiClient = apiClient;
        this.bossbarDisplay = plugin.getBossbarDisplay();
    }

    public SongData addSongToPlaylist(String playerName, String mid) {
        // 检查歌曲是否已存在于播放队列，如果存在则不重复添加
        if (playlist.stream().anyMatch(song -> song.getMid().equals(mid))) {
            // 可以选择在这里发送消息通知玩家
            Bukkit.getPlayer(playerName).sendMessage("§e歌曲已在播放队列中，不会重复添加。");
            return null;
        }

        SongInfo songInfo = apiClient.getSongInfo(mid);
        if (songInfo == null) {
            return null;
        }

        SongData newSong = new SongData(mid, songInfo.getSongUrl(), songInfo.getLrcLines(), songInfo.getDurationMillis(), songInfo.getTitle(), songInfo.getSingers(), songInfo.getAlbumName(), playerName);
        playlist.addLast(newSong);
        return newSong;
    }

    public void checkAndStartPlayback() {
        if (currentSong == null && !playlist.isEmpty()) {
            startPlayback();
        }
    }

    private void startPlayback() {
        if (playlist.isEmpty()) {
            return;
        }

        this.currentSong = playlist.getFirst();
        Bukkit.broadcastMessage("§a正在播放歌曲: §e" + currentSong.getFormattedTitle() + " §7(由 " + currentSong.getPlayerName() + " 播放)");

        apiClient.reportPlaybackStatus(currentSong.getMid(), true, 0, "play");

        if (reportTask != null) {
            reportTask.cancel();
        }

        reportTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (currentSong != null) {
                long currentTime = currentSong.getCurrentTime() / 1000;
                apiClient.reportPlaybackStatus(currentSong.getMid(), true, currentTime, "progress");
                if (currentSong.getCurrentTime() >= currentSong.getDurationMillis()) {
                    Bukkit.getScheduler().runTask(plugin, () -> playNextSong(false));
                }
            }
        }, 20 * 10, 20 * 10);

        bossbarDisplay.startDisplay(currentSong);
    }

    public void play(String playerName) {
        if (currentSong != null) {
            Bukkit.broadcastMessage("§a音乐继续播放: §e" + currentSong.getFormattedTitle() + " §7(由 " + playerName + " 继续)");

            apiClient.reportPlaybackStatus(currentSong.getMid(), true, currentSong.getCurrentTime() / 1000, "play");

            bossbarDisplay.startDisplay(currentSong);

            if (reportTask != null) {
                reportTask.cancel();
            }
            reportTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
                if (currentSong != null) {
                    long currentTime = currentSong.getCurrentTime() / 1000;
                    apiClient.reportPlaybackStatus(currentSong.getMid(), true, currentTime, "progress");
                    if (currentSong.getCurrentTime() >= currentSong.getDurationMillis()) {
                        Bukkit.getScheduler().runTask(plugin, () -> playNextSong(false));
                    }
                }
            }, 20 * 10, 20 * 10);
        } else if (!playlist.isEmpty()) {
            startPlayback();
        } else {
            Bukkit.broadcastMessage("§c当前没有可播放的音乐。");
        }
    }

    public void play() {
        play("系统");
    }

    public void pause(String playerName) {
        if (currentSong != null) {
            Bukkit.broadcastMessage("§e音乐已暂停: §e" + currentSong.getTitle() + " §7(由 " + playerName + " 暂停)");

            apiClient.reportPlaybackStatus(currentSong.getMid(), false, currentSong.getCurrentTime() / 1000, "pause");

            bossbarDisplay.stopDisplay();

            if (reportTask != null) {
                reportTask.cancel();
                reportTask = null;
            }
        } else {
            Bukkit.broadcastMessage("§c当前没有可暂停的音乐。");
        }
    }

    public void playNextSong(boolean fromCommand) {
        if (currentSong != null) {
            // 移除当前歌曲
            playlist.remove(currentSong);
        }

        if (playlist.isEmpty()) {
            stopMusic();
            if (fromCommand) {
                Bukkit.broadcastMessage("§e播放队列为空，无法播放下一首。");
            } else {
                Bukkit.broadcastMessage("§e播放队列已结束。");
            }
            return;
        }

        this.currentSong = playlist.getFirst();
        Bukkit.broadcastMessage("§a正在播放下一首: §e" + currentSong.getFormattedTitle() + " §7(由 " + currentSong.getPlayerName() + " 添加)");

        currentSong.setCurrentTime(0);

        apiClient.reportPlaybackStatus(currentSong.getMid(), true, 0, "play");

        if (reportTask != null) {
            reportTask.cancel();
        }

        reportTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            if (currentSong != null) {
                long currentTime = currentSong.getCurrentTime() / 1000;
                apiClient.reportPlaybackStatus(currentSong.getMid(), true, currentTime, "progress");
                if (currentSong.getCurrentTime() >= currentSong.getDurationMillis()) {
                    Bukkit.getScheduler().runTask(plugin, () -> playNextSong(false));
                }
            }
        }, 20 * 10, 20 * 10);

        bossbarDisplay.startDisplay(currentSong);
    }

    public void clearPlaylist(String playerName) {
        if (playlist.isEmpty()) {
            Bukkit.broadcastMessage("§e播放队列已为空，无需清空。");
            return;
        }

        stopMusic();
        playlist.clear();
        Bukkit.broadcastMessage("§a播放队列已被 " + playerName + " 清空。");
    }

    public void setSeek(int seconds) {
        if (currentSong != null) {
            long seekTimeMillis = (long) seconds * 1000;
            Bukkit.broadcastMessage("§a设置播放进度到: §e" + seconds + " 秒");

            currentSong.setCurrentTime(seekTimeMillis);

            apiClient.reportPlaybackStatus(currentSong.getMid(), true, seconds, "play");

            bossbarDisplay.startDisplay(currentSong);
        } else {
            Bukkit.broadcastMessage("§c当前没有播放音乐，无法设置进度。");
        }
    }

    public void stopMusic() {
        if (reportTask != null) {
            reportTask.cancel();
            reportTask = null;
        }
        currentSong = null;
        bossbarDisplay.stopDisplay();
    }

    public SongData getCurrentSong() {
        return currentSong;
    }

    public List<SongData> getPlaylist() {
        return new ArrayList<>(playlist);
    }
}