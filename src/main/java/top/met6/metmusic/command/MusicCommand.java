package top.met6.metmusic.command;

import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import top.met6.metmusic.MetMusicPlugin;
import top.met6.metmusic.data.PlaylistSong;
import top.met6.metmusic.data.SearchResult;
import top.met6.metmusic.data.SearchPage;
import top.met6.metmusic.data.SongData;
import top.met6.metmusic.manager.MusicPlaybackManager;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MusicCommand implements CommandExecutor, TabCompleter {

    private final MetMusicPlugin plugin;
    private static final int SONGS_PER_PAGE = 10;
    private static final int SEARCH_RESULTS_PER_PAGE = 10;
    private final Map<UUID, SearchSession> searchSessions = new ConcurrentHashMap<>();

    private static class SearchSession {
        private final int page;
        private final List<SearchResult> results;

        private SearchSession(int page, List<SearchResult> results) {
            this.page = page;
            this.results = results;
        }
    }

    public MusicCommand(MetMusicPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("§c只有玩家才能使用此命令！");
            return true;
        }

        Player player = (Player) sender;
        MusicPlaybackManager playbackManager = plugin.getPlaybackManager();

        if (args.length == 0 || args[0].equalsIgnoreCase("帮助") || args[0].equalsIgnoreCase("help")) {
            sendHelpMessage(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "playmid":
                if (args.length < 2) {
                    player.sendMessage("§c用法: /mmusic playmid <mid>");
                    return true;
                }
                String mid = args[1];
                addAndPlay(player, mid);
                break;
            case "play":
                playbackManager.play(player.getName());
                break;
            case "pause":
                playbackManager.pause(player.getName());
                break;
            case "next":
                playbackManager.playNextSong(true);
                break;
            case "setseek":
                if (args.length < 2) {
                    player.sendMessage("§c用法: /mmusic setseek <秒数>");
                    return true;
                }
                SongData currentSong = playbackManager.getCurrentSong();
                if (currentSong == null) {
                    player.sendMessage("§c当前没有播放音乐，无法设置进度。");
                    return true;
                }
                try {
                    int seconds = Integer.parseInt(args[1]);
                    long maxSeconds = currentSong.getDurationMillis() / 1000;
                    if (seconds < 0 || seconds > maxSeconds) {
                        player.sendMessage("§c输入秒数不合规。请输入一个介于 0 和 " + maxSeconds + " 之间的整数。");
                        return true;
                    }
                    playbackManager.setSeek(seconds);
                } catch (NumberFormatException e) {
                    player.sendMessage("§c无效的秒数，请输入一个整数。");
                }
                break;
            case "setbossbar":
                plugin.getBossbarDisplay().togglePlayerBossbar(player);
                break;
            case "sid":
                sendSessionInfo(player);
                break;
            case "playlist":
                int page = 1;
                if (args.length > 1) {
                    try {
                        page = Integer.parseInt(args[1]);
                    } catch (NumberFormatException e) {
                        player.sendMessage("§c无效的页码，请输入一个整数。");
                        return true;
                    }
                }
                displayPlaylist(player, page);
                break;
            case "songinfo":
                displaySongInfo(player);
                break;
            case "search":
                if (args.length < 2) {
                    player.sendMessage("§c用法: /mmusic search <关键字> [页码]");
                    return true;
                }
                int searchPage = 1;
                int keywordEnd = args.length;
                if (args.length > 2) {
                    try {
                        searchPage = Integer.parseInt(args[args.length - 1]);
                        keywordEnd--;
                    } catch (NumberFormatException ignored) {
                    }
                }
                if (searchPage < 1) {
                    player.sendMessage("§c页码必须是大于 0 的整数。");
                    return true;
                }
                String keyword = String.join(" ", Arrays.copyOfRange(args, 1, keywordEnd));

                searchAndDisplay(player, keyword, searchPage);
                break;
            case "songlist":
                if (args.length < 2) {
                    player.sendMessage("§c用法: /mmusic songlist <tid> [页码]");
                    return true;
                }
                String tid = args[1];
                int songListPage = 1;
                if (args.length > 2) {
                    try {
                        songListPage = Integer.parseInt(args[2]);
                    } catch (NumberFormatException e) {
                        player.sendMessage("§c无效的页码，请输入一个整数。");
                        return true;
                    }
                }
                displaySonglist(player, tid, songListPage);
                break;
            case "playsonglist":
                if (args.length < 3) {
                    player.sendMessage("§c用法: /mmusic playsonglist <tid> <序号>");
                    return true;
                }
                String playsonglistTid = args[1];
                try {
                    int index = Integer.parseInt(args[2]);
                    playSongFromPlaylist(player, playsonglistTid, index);
                } catch (NumberFormatException e) {
                    player.sendMessage("§c无效的序号，请输入一个整数。");
                }
                break;
            case "playsearch":
                if (args.length < 2) {
                    player.sendMessage("§c用法: /mmusic playsearch <序号>");
                    return true;
                }
                try {
                    int index = Integer.parseInt(args[1]);
                    playSongFromSearch(player, index);
                } catch (NumberFormatException e) {
                    player.sendMessage("§c无效的序号，请输入一个整数。");
                }
                break;
            case "clear":
                playbackManager.pause(player.getName());
                playbackManager.clearPlaylist(player.getName());
                break;
            default:
                player.sendMessage("§c未知命令。使用 /mmusic 帮助 查看可用命令。");
                break;
        }
        return true;
    }

    private void addAndPlay(Player player, String mid) {
        player.sendMessage("§a正在获取歌曲信息，请稍候...");
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            SongData song = plugin.getPlaybackManager().addSongToPlaylist(player.getName(), mid);
            if (song != null) {
                plugin.getPlaybackManager().checkAndStartPlayback();
                player.sendMessage("§a已将歌曲 §e" + song.getFormattedTitle() + "§a 添加到播放队列。");
            } else {
//                player.sendMessage("§c获取歌曲信息失败，该歌曲可能无效或不存在。");
                player.sendMessage("§c播放队列添加失败，该歌曲可能无效或不存在，或播放队列中已存在该歌曲。");
            }
        });
    }

    private void sendHelpMessage(Player player) {
        player.sendMessage("§e--- MeT-Music 帮助 ---");
        player.sendMessage("§6/mmusic playmid <mid>§f: 播放指定 mid 的歌曲");
        player.sendMessage("§6/mmusic search <关键字> [页码]§f: 搜索歌曲");
        player.sendMessage("§6/mmusic playsearch <序号>§f: 播放搜索结果中的指定歌曲");
        player.sendMessage("§6/mmusic songlist <tid> [页码]§f: 查看歌单");
        player.sendMessage("§6/mmusic playsonglist <tid> <序号>§f: 播放歌单中的指定歌曲");
        player.sendMessage("§6/mmusic play§f: 继续播放");
        player.sendMessage("§6/mmusic pause§f: 暂停播放");
        player.sendMessage("§6/mmusic next§f: 下一首");
        player.sendMessage("§6/mmusic clear§f: 清空播放队列");
        player.sendMessage("§6/mmusic setseek <秒数>§f: 设置播放进度");
        player.sendMessage("§6/mmusic songinfo§f: 查看当前歌曲信息");
        player.sendMessage("§6/mmusic setbossbar§f: 切换歌词 Bossbar 的显示状态");
        player.sendMessage("§6/mmusic sid§f: 查看当前播放器 SID 和链接");
        TextComponent openPlayer = new TextComponent("§n§b点击打开播放器");
        openPlayer.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, plugin.getPluginConfig().getPlayerUrl()));
        openPlayer.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("§a点击在浏览器中打开播放器").create()));
        player.spigot().sendMessage(openPlayer);
        player.sendMessage("§e--------------------");
    }

    private void sendSessionInfo(Player player) {
        String sid = plugin.getPluginConfig().getSessionId();
        String playerUrl = plugin.getPluginConfig().getPlayerUrl();

        player.sendMessage("§e--- MeT-Music 播放器 ---");
        TextComponent sidComponent = new TextComponent("§fSID: §b" + sid);
        sidComponent.setClickEvent(new ClickEvent(ClickEvent.Action.COPY_TO_CLIPBOARD, sid));
        sidComponent.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("§a点击复制 SID").create()));
        player.spigot().sendMessage(sidComponent);

        TextComponent linkComponent = new TextComponent("§f播放器链接: §n§b" + playerUrl);
        linkComponent.setClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, playerUrl));
        linkComponent.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("§a点击在浏览器中打开播放器").create()));
        player.spigot().sendMessage(linkComponent);
        player.sendMessage("§e-----------------------");
    }

    private void searchAndDisplay(Player player, String keyword, int page) {
        player.sendMessage("§a正在搜索歌曲: " + keyword + "...");

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            SearchPage searchPage = plugin.getApiClient().searchSongs(keyword, page, SEARCH_RESULTS_PER_PAGE);

            if (searchPage == null) {
                plugin.getServer().getScheduler().runTask(plugin, () -> player.sendMessage("§c搜索失败，请稍后重试。"));
                return;
            }

            List<SearchResult> results = searchPage.getResults();
            int totalCount = searchPage.getTotalCount();
            int totalPages = (int) Math.ceil((double) totalCount / SEARCH_RESULTS_PER_PAGE);

            if (totalCount == 0) {
                plugin.getServer().getScheduler().runTask(plugin, () -> player.sendMessage("§c未找到相关歌曲。"));
                return;
            }
            if (page > totalPages || results.isEmpty()) {
                plugin.getServer().getScheduler().runTask(plugin, () -> player.sendMessage("§c页码超出范围。总页数为: " + totalPages));
                return;
            }

            searchSessions.put(player.getUniqueId(), new SearchSession(page, results));

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                player.sendMessage("§6--- 搜索结果 (第 " + page + " / " + totalPages + " 页) ---");
                for (int i = 0; i < results.size(); i++) {
                    SearchResult result = results.get(i);
                    TextComponent songComponent = new TextComponent("§f" + ((page - 1) * SEARCH_RESULTS_PER_PAGE + i + 1) + ". §e" + result.getFormattedTitle());
                    songComponent.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/mmusic playmid " + result.getMid()));
                    songComponent.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("§a点击添加到播放队列").create()));
                    player.spigot().sendMessage(songComponent);
                }

                TextComponent navigation = new TextComponent("");
                if (page > 1) {
                    TextComponent prev = new TextComponent("§a[上一页]");
                    prev.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/mmusic search " + keyword + " " + (page - 1)));
                    prev.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("§a点击查看上一页").create()));
                    navigation.addExtra(prev);
                }

                if (page > 1 && page < totalPages) {
                    navigation.addExtra(" §7| ");
                }

                if (page < totalPages) {
                    TextComponent next = new TextComponent("§a[下一页]");
                    next.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/mmusic search " + keyword + " " + (page + 1)));
                    next.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("§a点击查看下一页").create()));
                    navigation.addExtra(next);
                }

                if (page > 1 || page < totalPages) {
                    player.spigot().sendMessage(navigation);
                }

                player.sendMessage("§6--------------------");
            });
        });
    }

    private void displaySonglist(Player player, String tid, int page) {
        player.sendMessage("§a正在获取歌单信息，请稍候...");

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            List<PlaylistSong> songs = plugin.getApiClient().getPlaylistSongs(tid);

            if (songs == null || songs.isEmpty()) {
                player.sendMessage("§c获取歌单失败或歌单为空。");
                return;
            }

            int totalSongs = songs.size();
            int totalPages = (int) Math.ceil((double) totalSongs / SONGS_PER_PAGE);

            if (page < 1 || page > totalPages) {
                player.sendMessage("§c页码超出范围。总页数为: " + totalPages);
                return;
            }

            int startIndex = (page - 1) * SONGS_PER_PAGE;
            int endIndex = Math.min(startIndex + SONGS_PER_PAGE, totalSongs);

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                player.sendMessage("§6--- 歌单歌曲 (第 " + page + " / " + totalPages + " 页) ---");
                for (int i = startIndex; i < endIndex; i++) {
                    PlaylistSong song = songs.get(i);
                    TextComponent songComponent = new TextComponent("§f" + (i + 1) + ". §e" + song.getFormattedTitle());
                    songComponent.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/mmusic playmid " + song.getId()));
                    songComponent.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("§a点击添加到播放队列").create()));
                    player.spigot().sendMessage(songComponent);
                }

                TextComponent navigation = new TextComponent("");
                if (page > 1) {
                    TextComponent prev = new TextComponent("§a[上一页]");
                    prev.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/mmusic songlist " + tid + " " + (page - 1)));
                    prev.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("§a点击查看上一页").create()));
                    navigation.addExtra(prev);
                }

                if (page > 1 && page < totalPages) {
                    navigation.addExtra(" §7| ");
                }

                if (page < totalPages) {
                    TextComponent next = new TextComponent("§a[下一页]");
                    next.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/mmusic songlist " + tid + " " + (page + 1)));
                    next.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("§a点击查看下一页").create()));
                    navigation.addExtra(next);
                }

                if (page <= totalPages) {
                    player.spigot().sendMessage(navigation);
                }

                player.sendMessage("§6--------------------");
            });
        });
    }

    private void playSongFromPlaylist(Player player, String tid, int index) {
        player.sendMessage("§a正在获取歌单信息，请稍候...");
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            List<PlaylistSong> songs = plugin.getApiClient().getPlaylistSongs(tid);
            if (songs == null || songs.isEmpty()) {
                player.sendMessage("§c获取歌单失败或歌单为空。");
                return;
            }
            if (index < 1 || index > songs.size()) {
                player.sendMessage("§c序号超出范围。请输入 1 到 " + songs.size() + " 之间的整数。");
                return;
            }
            PlaylistSong songToPlay = songs.get(index - 1);
            addAndPlay(player, songToPlay.getId());
        });
    }

    private void playSongFromSearch(Player player, int index) {
        player.sendMessage("§a正在获取搜索结果，请稍候...");
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            SearchSession session = searchSessions.get(player.getUniqueId());
            if (session == null || session.results.isEmpty()) {
                player.sendMessage("§c没有可用的搜索结果。请先使用 /mmusic search 进行搜索。");
                return;
            }
            int firstIndex = (session.page - 1) * SEARCH_RESULTS_PER_PAGE + 1;
            int lastIndex = firstIndex + session.results.size() - 1;
            if (index < firstIndex || index > lastIndex) {
                player.sendMessage("§c序号超出范围。请输入 " + firstIndex + " 到 " + lastIndex + " 之间的整数。");
                return;
            }
            SearchResult songToPlay = session.results.get(index - firstIndex);
            addAndPlay(player, songToPlay.getMid());
        });
    }

    private void displaySongInfo(Player player) {
        SongData currentSong = plugin.getPlaybackManager().getCurrentSong();
        if (currentSong == null) {
            player.sendMessage("§c当前没有播放音乐。");
        } else {
            player.sendMessage("§e--- 当前歌曲信息 ---");
            player.sendMessage("§6标题: §f" + currentSong.getTitle());
            player.sendMessage("§6歌手: §f" + String.join(" & ", currentSong.getSingers()));
            player.sendMessage("§6专辑: §f" + currentSong.getAlbumName());
            player.sendMessage("§6MID: §f" + currentSong.getMid());
            player.sendMessage("§6添加者: §f" + currentSong.getPlayerName());
            player.sendMessage("§e--------------------");
        }
    }

    private void displayPlaylist(Player player, int page) {
        List<SongData> playlist = plugin.getPlaybackManager().getPlaylist();
        int totalSongs = playlist.size();
        int totalPages = (int) Math.ceil((double) totalSongs / SONGS_PER_PAGE);

        if (totalSongs == 0) {
            player.sendMessage("§e播放队列为空。");
            return;
        }

        if (page < 1 || page > totalPages) {
            player.sendMessage("§c页码超出范围。总页数为: " + totalPages);
            return;
        }

        int startIndex = (page - 1) * SONGS_PER_PAGE;
        int endIndex = Math.min(startIndex + SONGS_PER_PAGE, totalSongs);

        player.sendMessage("§6--- 播放队列 (第 " + page + " / " + totalPages + " 页) ---");
        SongData currentSong = plugin.getPlaybackManager().getCurrentSong();
        for (int i = startIndex; i < endIndex; i++) {
            SongData song = playlist.get(i);
            String isCurrent = (song.equals(currentSong)) ? "§a[当前] " : "";
            // 移除歌曲点击功能
            TextComponent songComponent = new TextComponent(isCurrent + "§f" + (i + 1) + ". §e" + song.getFormattedTitle() + " §7(由 " + song.getPlayerName() + " 添加)");
            player.spigot().sendMessage(songComponent);
        }

        TextComponent navigation = new TextComponent("");
        if (page > 1) {
            TextComponent prev = new TextComponent("§a[上一页]");
            prev.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/mmusic playlist " + (page - 1)));
            prev.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("§a点击查看上一页").create()));
            navigation.addExtra(prev);
        }

        if (page > 1 && page < totalPages) {
            navigation.addExtra(" §7| ");
        }

        if (page < totalPages) {
            TextComponent next = new TextComponent("§a[下一页]");
            next.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/mmusic playlist " + (page + 1)));
            next.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new ComponentBuilder("§a点击查看下一页").create()));
            navigation.addExtra(next);
        }

        if (page > 1 || page < totalPages) {
            player.spigot().sendMessage(navigation);
        }

        player.sendMessage("§6--------------------");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("help", "sid", "playmid", "play", "pause", "setseek", "setbossbar", "playlist", "next", "clear", "songinfo", "search", "songlist", "playsonglist", "playsearch");
        }
        if (args.length > 1) {
            String subCommand = args[0].toLowerCase();
            if (subCommand.equals("playmid")) {
                return Collections.emptyList();
            }
            if (subCommand.equals("songlist") && args.length == 2) {
                return Arrays.asList("<歌单tid>");
            }
            if (subCommand.equals("playsonglist") && args.length == 2) {
                return Arrays.asList("<歌单tid>");
            }
            if (subCommand.equals("playsonglist") && args.length == 3) {
                return Arrays.asList("<序号>");
            }
            if (subCommand.equals("playsearch") && args.length == 2) {
                return Arrays.asList("<序号>");
            }
        }
        return Collections.emptyList();
    }
}
