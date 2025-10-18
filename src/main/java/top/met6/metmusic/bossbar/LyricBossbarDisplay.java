package top.met6.metmusic.bossbar;

import org.bukkit.Bukkit;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import top.met6.metmusic.MetMusicPlugin;
import top.met6.metmusic.data.LrcLine;
import top.met6.metmusic.data.SongData;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class LyricBossbarDisplay {

    private final MetMusicPlugin plugin;
    private final Map<UUID, BossBar> playerBossbars = new HashMap<>();
    private BukkitTask globalBossbarTask;
    private SongData currentSong;

    public LyricBossbarDisplay(MetMusicPlugin plugin) {
        this.plugin = plugin;
    }

    public void startDisplay(SongData songData) {
        stopDisplay();
        this.currentSong = songData;

        for (Player player : Bukkit.getOnlinePlayers()) {
            // 重新创建 Bossbar
            addPlayerToBossbar(player);
        }

        resumeDisplay(songData);
    }

    /**
     * 为单个玩家添加或重新创建 Bossbar
     * 解决 Bossbar 在玩家重新登录后不显示的bug
     * @param player 玩家对象
     */
    public void addPlayerToBossbar(Player player) {
        UUID playerId = player.getUniqueId();

        // 移除旧的Bossbar
        if (playerBossbars.containsKey(playerId)) {
            playerBossbars.get(playerId).removeAll();
            playerBossbars.remove(playerId);
        }

        BossBar bossBar = Bukkit.createBossBar("§b正在加载歌词...", BarColor.BLUE, BarStyle.SEGMENTED_10);
        bossBar.addPlayer(player);
        bossBar.setVisible(true);
        playerBossbars.put(playerId, bossBar);

        if (currentSong != null) {
            updateProgressAndLyric(currentSong, currentSong.getCurrentTime());
        }
    }

    public void resumeDisplay(SongData songData) {
        if (globalBossbarTask != null) {
            globalBossbarTask.cancel();
        }

        this.currentSong = songData;

        globalBossbarTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (currentSong == null) {
                stopDisplay();
                return;
            }

            long currentTimeMillis = currentSong.getCurrentTime();
            updateProgressAndLyric(currentSong, currentTimeMillis);
            currentSong.setCurrentTime(currentTimeMillis + 50);

            if (currentSong.getCurrentTime() >= currentSong.getDurationMillis()) {
                plugin.getPlaybackManager().playNextSong(false);
            }
        }, 0L, 1L);
    }

    public void updateProgressAndLyric(SongData songData, long currentTimeMillis) {
        if (songData == null) return;

        List<LrcLine> lrcLines = songData.getLrcLines();
        String currentLyric = "§f~暂无歌词~";

        if (lrcLines != null && !lrcLines.isEmpty()) {
            for (int i = 0; i < lrcLines.size(); i++) {
                LrcLine line = lrcLines.get(i);
                if (currentTimeMillis >= line.getTimeMillis()) {
                    if (i + 1 < lrcLines.size()) {
                        LrcLine nextLine = lrcLines.get(i + 1);
                        if (currentTimeMillis < nextLine.getTimeMillis()) {
                            currentLyric = line.getText();
                            break;
                        }
                    } else {
                        currentLyric = line.getText();
                        break;
                    }
                }
            }
        }

        double progress = (double) currentTimeMillis / songData.getDurationMillis();
        if (progress > 1.0) {
            progress = 1.0;
        } else if (progress < 0.0) {
            progress = 0.0;
        }

        String finalLyric = "§6♫ §f" + currentLyric + " §6♫";
        double finalProgress = progress;
        playerBossbars.values().forEach(bossBar -> {
            bossBar.setTitle(finalLyric);
            bossBar.setProgress(finalProgress);
        });
    }

    public void stopDisplay() {
        if (globalBossbarTask != null) {
            globalBossbarTask.cancel();
            globalBossbarTask = null;
        }
        playerBossbars.values().forEach(bossBar -> bossBar.setVisible(false));
    }

    public void hideAllBossbars() {
        stopDisplay();
        playerBossbars.values().forEach(bossBar -> bossBar.removeAll());
        playerBossbars.clear();
    }

    public void togglePlayerBossbar(Player player) {
        if (currentSong == null) {
            player.sendMessage("§c当前没有播放音乐，无法切换歌词状态。");
            return;
        }

        UUID playerId = player.getUniqueId();
        BossBar bossBar = playerBossbars.get(playerId);

        if (bossBar == null) {
            // 如果Bossbar不存在，就创建一个新的
            addPlayerToBossbar(player);
            player.sendMessage("§a歌词已显示。");
        } else {
            // 如果Bossbar已存在，就切换其可见性
            boolean isVisible = bossBar.isVisible();

            // 每次切换时都重新创建，解决 Bossbar 对象失效问题
            bossBar.removeAll();
            playerBossbars.remove(playerId);

            BossBar newBossBar = Bukkit.createBossBar("§b正在加载歌词...", BarColor.BLUE, BarStyle.SEGMENTED_10);
            newBossBar.addPlayer(player);
            newBossBar.setVisible(!isVisible);
            playerBossbars.put(playerId, newBossBar);

            if (!isVisible) {
                player.sendMessage("§a歌词已显示。");
            } else {
                player.sendMessage("§e歌词已隐藏。");
            }

            // 更新Bossbar内容
            updateProgressAndLyric(currentSong, currentSong.getCurrentTime());
        }
    }
}