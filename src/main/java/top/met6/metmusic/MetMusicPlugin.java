package top.met6.metmusic;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;
import top.met6.metmusic.command.MusicCommand;
import top.met6.metmusic.config.PluginConfig;
import top.met6.metmusic.manager.MusicPlaybackManager;
import top.met6.metmusic.api.ApiClient;
import top.met6.metmusic.bossbar.LyricBossbarDisplay;

import java.util.logging.Logger;

public class MetMusicPlugin extends JavaPlugin implements Listener {

    private static MetMusicPlugin instance;
    private PluginConfig pluginConfig;
    private ApiClient apiClient;
    private MusicPlaybackManager playbackManager;
    private LyricBossbarDisplay bossbarDisplay;

    @Override
    public void onEnable() {
        instance = this;
        Logger logger = getLogger();

        saveDefaultConfig();
        this.pluginConfig = new PluginConfig(this);
        this.pluginConfig.loadConfig();

        this.apiClient = new ApiClient(logger);
        this.bossbarDisplay = new LyricBossbarDisplay(this);
        this.playbackManager = new MusicPlaybackManager(this, apiClient);

        // 新增 TabCompleter
        MusicCommand musicCommand = new MusicCommand(this);
        getCommand("mmusic").setExecutor(musicCommand);
        getCommand("mmusic").setTabCompleter(musicCommand);

        getServer().getPluginManager().registerEvents(this, this);

        logger.info("MeT-Music 插件已启用！");
    }

    @Override
    public void onDisable() {
        if (bossbarDisplay != null) {
            bossbarDisplay.hideAllBossbars();
        }
        getLogger().info("MeT-Music 插件已禁用！");
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (playbackManager.getCurrentSong() != null) {
            bossbarDisplay.addPlayerToBossbar(event.getPlayer());
        }
    }

    public static MetMusicPlugin getInstance() {
        return instance;
    }

    public PluginConfig getPluginConfig() {
        return pluginConfig;
    }

    public ApiClient getApiClient() {
        return apiClient;
    }

    public MusicPlaybackManager getPlaybackManager() {
        return playbackManager;
    }

    public LyricBossbarDisplay getBossbarDisplay() {
        return bossbarDisplay;
    }
}