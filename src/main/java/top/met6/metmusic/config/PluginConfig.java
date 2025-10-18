package top.met6.metmusic.config;

import org.bukkit.configuration.file.FileConfiguration;
import top.met6.metmusic.MetMusicPlugin;

import java.util.UUID;

public class PluginConfig {

    private final MetMusicPlugin plugin;
    private FileConfiguration config;
    private String sessionId;

    public PluginConfig(MetMusicPlugin plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfig();
    }

    public void loadConfig() {
        if (!config.contains("sid")) {
            sessionId = UUID.randomUUID().toString();
            config.set("sid", sessionId);
            plugin.saveConfig();
            plugin.getLogger().info("首次启动，已生成新的会话ID (sid): " + sessionId);
        } else {
            sessionId = config.getString("sid");
            plugin.getLogger().info("加载会话ID (sid): " + sessionId);
        }
    }

    public String getSessionId() {
        return sessionId;
    }
}