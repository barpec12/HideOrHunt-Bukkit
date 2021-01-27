package me.latestion.hoh.game;

import me.latestion.hoh.HideOrHunt;
import me.latestion.hoh.api.HOHGameEvent;
import me.latestion.hoh.data.flat.FlatHOHGame;
import me.latestion.hoh.localization.MessageManager;
import me.latestion.hoh.myrunnables.Episodes;
import me.latestion.hoh.myrunnables.SupplyDrop;
import me.latestion.hoh.stats.Metrics;
import me.latestion.hoh.utils.Bar;
import me.latestion.hoh.utils.ScoreBoardUtil;
import me.latestion.hoh.utils.Util;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.io.File;
import java.util.*;

public class HOHGame {

    private final HideOrHunt plugin;
    private final Util util;
    public Location loc;
    public int teamSize;
    public Map<UUID, HOHPlayer> hohPlayers = new HashMap<>();
    public Bar bar;
    public boolean freeze = false;
    public boolean grace = false;
    public GameState gameState = GameState.OFF;
    public int ep = 1;
    public Inventory inv;
    private Map<Integer, HOHTeam> teams = new HashMap<>();
    private List<UUID> chatSpies = new ArrayList<>();
    public boolean allowCrafting = false;

    public HOHGame(HideOrHunt plugin) {
        this.plugin = plugin;
        this.util = new Util(plugin);
    }

    public HOHGame(HideOrHunt plugin, Location loc, int teamSize) {
        this.plugin = plugin;
        this.loc = loc;
        this.util = new Util(plugin);
        this.teamSize = teamSize;
    }

    public void loadGame() {
        HOHGameEvent event = new HOHGameEvent(GameState.ON, loc, teamSize);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return;
        }
        double neededTeams = Math.ceil(hohPlayers.size() / (double) teamSize);
        int totalTeams = (int) neededTeams;
        this.inv = util.createInv(totalTeams);
        teams.values().stream().forEach(t -> plugin.sbUtil.addTeam(t.getName()));
        this.bar = new Bar(plugin);
        if (plugin.getConfig().getBoolean("Auto-Episodes")) new Episodes(plugin);
        if (plugin.getConfig().getBoolean("Auto-Supply-Drops")) new SupplyDrop(plugin);
    }

    public void prepareGame() {
        HOHGameEvent event = new HOHGameEvent(GameState.PREPARE, loc, teamSize);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return;
        }
        this.gameState = GameState.PREPARE;
        for (Player player : getWorld().getPlayers()) {
            if (player.isOp() && !util.getAllowOp()) {
                continue;
            }
            HOHPlayer p = new HOHPlayer(this, player.getUniqueId());
            hohPlayers.put(player.getUniqueId(), p);
        }

        double neededTeams = Math.ceil(hohPlayers.size() / (double) teamSize);
        int totalTeams = (int) neededTeams;

        if (plugin.getConfig().getBoolean("Auto-Team-Join")) {
            List<String> teamNames = plugin.getConfig().getStringList("Team-Names");
            parentLoop:
            for (int i = 0; i < totalTeams; i++) {
                HOHTeam team = new HOHTeam(i);
                addTeam(team);
                team.setName(teamNames.get(i));
                if (plugin.support != null && plugin.party != null) {
                    for (int ii = 0; ii < plugin.support.getCurrentServerState().teams.size(); ii++) {
                        List<UUID> add = plugin.support.getCurrentServerState().teams.get(0);
                        for (UUID pId : add) {
                            HOHPlayer player = hohPlayers.get(pId);
                            player.getPlayer().closeInventory();
                            if (!player.hasTeam()) {
                                player.setTeam(team);
                            }
                        }
                        plugin.support.getCurrentServerState().teams.remove(0);
                    }
                }
                for (HOHPlayer player : hohPlayers.values()) {
                    if (!player.hasTeam()) {
                        if (!team.addPlayer(player)) continue parentLoop;
                        player.setTeam(team);
                    }
                    player.getPlayer().closeInventory();
                }
            }
            if (allPlayersSelectedTeam() && areAllTeamsNamed()) {
                startGame();
            }
            return;
        }

        this.inv = util.createInv(totalTeams);
        for (HOHPlayer player : hohPlayers.values()) {
            player.prepareTeam(inv);
        }

        if (plugin.getConfig().getInt("Force-Team-After") > 0) {
            int secs = plugin.getConfig().getInt("Force-Team-After");
            Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, () -> {
                if (getGameState() == GameState.PREPARE) {
                    int totalNeededTeam = (int) Math.ceil(hohPlayers.size() / (double) teamSize);
                    int totalTeam = teams.size();
                    if (totalNeededTeam >= totalTeam) {
                        if (allPlayersSelectedTeam() && areAllTeamsNamed()) {
                            startGame();
                            return;
                        }
                    }
                    int neededTeam = totalNeededTeam - totalTeam;
                    List<String> teamNames = plugin.getConfig().getStringList("Team-Names");
                    parentLoop:
                    for (int i = 0; i < neededTeam; i++) {
                        HOHTeam team = new HOHTeam(i);
                        addTeam(team);
                        team.setName(teamNames.get(i));
                        for (HOHPlayer player : hohPlayers.values()) {
                            if (!player.hasTeam()) {
                                if (!team.addPlayer(player)) {
                                    continue parentLoop;
                                }
                                player.setTeam(team);
                            }
                        }
                    }
                    if (allPlayersSelectedTeam() && areAllTeamsNamed()) {
                        startGame();
                    }
                }
                return;
            }, secs * 20L);
        }
    }

    public void startGame() {
        HOHGameEvent event = new HOHGameEvent(GameState.ON, loc, teamSize);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return;
        }
        if (gameState != GameState.PREPARE) return;
        Bukkit.getServer().broadcastMessage(plugin.getMessageManager().getMessage("starting-game"));
        setBorder();
        for (HOHTeam team : teams.values()) {
            String name = team.getName();
            plugin.sbUtil.addTeam(name);
            for (HOHPlayer player : team.players) {
                Player p = player.getPlayer();
                util.givePlayerKit(p, team, name);
            }
        }
        getWorld().setStorm(false);
        getWorld().setThundering(false);
        plugin.sbUtil.addAllPlayers();
        this.bar = new Bar(plugin);
        gameState = GameState.ON;
        sendStartTitle();
        plugin.sbUtil.setAsthetic();
        if (plugin.getConfig().getBoolean("Grace-Period")) grace = true;
        if (plugin.getConfig().getBoolean("Enable-Effect-On-Start")) addStartPotEffects();
        if (plugin.getConfig().getBoolean("Enable-Effect-After-Start")) addAfterPotEffects();
        if (plugin.getConfig().getBoolean("Auto-Episodes")) new Episodes(plugin);
        if (plugin.getConfig().getBoolean("Auto-Supply-Drops")) new SupplyDrop(plugin);
        if (plugin.xray != null) plugin.xray.start();
    }

    public void addTeam(HOHTeam team) {
        teams.put(team.getID(), team);
    }

    public Map<Integer, HOHTeam> getTeams() {
        return teams;
    }

    public void setTeams(Map<Integer, HOHTeam> teams) {
        this.teams = teams;
    }

    public HOHTeam getTeam(Integer id) {
        return teams.get(id);
    }

    public List<HOHTeam> getAliveTeams() {
        List<HOHTeam> send = new ArrayList<>();
        for (HOHTeam team : teams.values()) {
            if (!team.eliminated) {
                send.add(team);
            }
        }
        return send;
    }

    private void setBorder() {
        int wb = util.getWorldBorder();
        loc.getWorld().getWorldBorder().setCenter(loc);
        loc.getWorld().getWorldBorder().setSize(wb);
        loc.getWorld().setSpawnLocation(loc);
    }

    public void setSpawnLocation(Location loc) {
        this.loc = loc;
    }

    public boolean checkEndConditions() {
        List<HOHTeam> aliveTeams = new ArrayList<>();
        for (Player player : getWorld().getPlayers()) {
            HOHPlayer p = hohPlayers.get(player.getUniqueId());
            if (!p.dead) {
                if (!aliveTeams.contains(p.getTeam())) {
                    aliveTeams.add(p.getTeam());
                }
            }
        }
        return aliveTeams.size() == 1;
    }

    public void endGame(String winnerTeam) {
        HOHGameEvent event = new HOHGameEvent(GameState.ON, loc, teamSize);
        Bukkit.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return;
        }
        MessageManager messageManager = plugin.getMessageManager();
        Bukkit.broadcastMessage(messageManager.getMessage("win-message").replace("%winner-team%", winnerTeam));
        loc.getWorld().getWorldBorder().reset();
        for (HOHTeam team : teams.values()) {
            if (team.getBeacon() != null) team.getBeacon().setType(Material.AIR);
            for (HOHPlayer player : team.players) {
                if (player.getPlayer().isOnline()) {
                    player.getPlayer().getInventory().clear();
                    player.getPlayer().setScoreboard(plugin.sbUtil.manager.getNewScoreboard());
                    if (plugin.getConfig().getBoolean("Teleport-To-Spawn")) player.getPlayer().teleport(loc);
                }
            }
        }
        Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
            @Override
            public void run() {
                for (UUID p : plugin.game.hohPlayers.keySet()) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "pardon " + Bukkit.getPlayer(p).getName());
                }
            }
        }, 0L);
        gameState = GameState.OFF;
        hohPlayers.clear();
        teams.clear();
        bar.stop();
        plugin.sbUtil = new ScoreBoardUtil(plugin);
        if (plugin.xray != null) plugin.xray.stop();
        Bukkit.getScheduler().cancelTasks(plugin);
        ep = 1;

        File gameFile = new File(plugin.getDataFolder(), "hohGame.yml");
        gameFile.delete();
        File teamFile = new File(plugin.getDataFolder(), "teams.yml");
        teamFile.delete();
        File playersFile = new File(plugin.getDataFolder(), "players.yml");
        playersFile.delete();

        Bukkit.broadcastMessage(plugin.getMessageManager().getMessage("ending-game"));
    }

    public void serverStop() {
        FlatHOHGame.save(this, plugin, new File(plugin.getDataFolder(), "hohGame.yml"));
        Bukkit.getScheduler().cancelTasks(plugin);
        plugin.game = null;
        new Metrics(plugin, 79307);
    }

    private void sendStartTitle() {
        MessageManager messageManager = plugin.getMessageManager();
        for (Player p : getWorld().getPlayers()) {
            p.sendTitle(messageManager.getMessage("start-title-first-line")
                    , messageManager.getMessage("start-title-second-line")
                    , 10
                    , 50
                    , 10);
        }
    }

    private void addStartPotEffects() {
        for (String s : plugin.getConfig().getStringList("Effect-On-Start")) {
            String[] split = s.split(", ");
            for (HOHPlayer player : hohPlayers.values()) {
                if (player.getPlayer().isOnline()) player.getPlayer()
                        .addPotionEffect(new PotionEffect(PotionEffectType.getByName(split[0]),
                                (Integer.parseInt(split[1]) * 20),
                                (Integer.parseInt(split[2]) - 1), false, false));
            }
        }
    }

    private void addAfterPotEffects() {
        for (String s : plugin.getConfig().getStringList("Effect-After-Start")) {
            String[] split = s.split(", ");
            PotionEffect effect = new PotionEffect(PotionEffectType.getByName(split[0]), (Integer.parseInt(split[1]) * 20), (Integer.parseInt(split[2]) - 1), false, false);
            Bukkit.getScheduler().scheduleSyncDelayedTask(plugin, () -> {
                for (HOHPlayer player : hohPlayers.values()) {
                    if (player.getPlayer().isOnline())
                        player.getPlayer().addPotionEffect(effect);
                }
            }, Integer.parseInt(split[3]) * 20L);
        }
    }

    public void graceOff() {
        MessageManager messageManager = plugin.getMessageManager();
        grace = false;
        Bukkit.broadcastMessage(messageManager.getMessage("grace-period-ended-1"));
        Bukkit.broadcastMessage(messageManager.getMessage("grace-period-ended-2"));
    }

    public HOHTeam getWinnerTeam() {
        return teams.values().stream().filter(t -> !t.eliminated).findAny().orElse(null);
    }

    public boolean areAllTeamsNamed() {
        return !plugin.game.getHohPlayers().values().stream().anyMatch(HOHPlayer::isNamingTeam);
    }

    public boolean allPlayersSelectedTeam() {
        return plugin.game.getHohPlayers().values().stream().allMatch(HOHPlayer::hasTeam);
    }

    public Map<UUID, HOHPlayer> getHohPlayers() {
        return hohPlayers;
    }

    public void setHohPlayers(Map<UUID, HOHPlayer> hohPlayers) {
        this.hohPlayers = hohPlayers;
    }

    public HOHPlayer getHohPlayer(UUID uuid) {
        return hohPlayers.get(uuid);
    }

    public World getWorld() {
        return loc.getWorld();
    }

    public GameState getGameState() {
        return this.gameState;
    }

    public void setGameState(GameState gameState) {
        this.gameState = gameState;
    }

    public boolean isSpying(Player player) {
        return chatSpies.contains(player.getUniqueId());
    }

    public void setSpying(Player player, boolean spying) {
        if (spying) chatSpies.add(player.getUniqueId());
        else chatSpies.removeIf(uuid -> uuid.equals(player.getUniqueId()));
    }
}
