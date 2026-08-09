package top.met6.metmusic.config;

import org.bukkit.configuration.file.FileConfiguration;
import top.met6.metmusic.MetMusicPlugin;

import java.util.UUID;

public class PluginConfig {

    private final MetMusicPlugin plugin;
    private FileConfiguration config;
    private String sessionId;
    private String playerUrlTemplate;

    private static final String DEFAULT_PLAYER_URL_TEMPLATE = "https://music.met6.top:444/player/?sid={sid}";

    public PluginConfig(MetMusicPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
    }

    public void loadConfig() {
        if (!config.contains("sid") || config.getString("sid", "").trim().isEmpty()) {
            sessionId = UUID.randomUUID().toString();
            config.set("sid", sessionId);
            plugin.saveConfig();
            plugin.getLogger().info("首次启动，已生成新的会话ID (sid): " + sessionId);
        } else {
            sessionId = config.getString("sid");
            plugin.getLogger().info("加载会话ID (sid): " + sessionId);
        }

        playerUrlTemplate = config.getString("player-url", DEFAULT_PLAYER_URL_TEMPLATE);
        if (!config.contains("player-url")) {
            config.set("player-url", playerUrlTemplate);
            plugin.saveConfig();
        }
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getPlayerUrl() {
        return playerUrlTemplate.replace("{sid}", sessionId);
    }
}
