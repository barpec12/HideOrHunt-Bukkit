package me.latestion.hoh.events;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import me.latestion.hoh.game.HOHGame;
import me.latestion.hoh.localization.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import me.latestion.hoh.HideOrHunt;
import me.latestion.hoh.game.GameState;

public class EntityDamage implements Listener {

	public List<UUID> antilog = new ArrayList<UUID>();

	private HideOrHunt plugin;

	public EntityDamage(HideOrHunt plugin) {
		this.plugin = plugin;
	}

	@EventHandler
	public void damage(EntityDamageByEntityEvent event) {
		if (plugin.game.gameState != GameState.ON) return;

		MessageManager messageManager = plugin.getMessageManager();

		if (plugin.game.grace && event.getDamager() instanceof Player && event.getEntity() instanceof Player && this.plugin.getConfig().getBoolean("Grace-Period")) {
			event.setCancelled(true);
			event.getDamager().sendMessage(messageManager.getMessage("grace-period-attack"));
			return;
		}
		if (event.getDamager() instanceof Player && event.getEntity() instanceof Player) {

			Player player = (Player) event.getEntity();
			Player target = (Player) event.getDamager();

			if (!plugin.getConfig().getBoolean("Team-Damage")) {
				if (plugin.game.hohPlayers.get(target.getUniqueId()).getTeam().players.contains(plugin.game.hohPlayers.get(player.getUniqueId()))) {
					event.setCancelled(true);
					target.sendMessage(messageManager.getMessage("team-member-attack"));
					return;
				}
			}

			int secs = plugin.getConfig().getInt("Pvp-Log");
			if (secs == 0) return;
			if (!antilog.contains(player.getUniqueId()) && !antilog.contains(target.getUniqueId())) {
				String msg = messageManager.getMessage("combat-start").replace("%time%", Integer.toString(secs));
				player.sendMessage(msg);
				target.sendMessage(msg);
				antilog.add(player.getUniqueId());
				antilog.add(target.getUniqueId());
				Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, new Runnable() {
					@Override
					public void run() {
						if ((antilog.contains(player.getUniqueId())) && (antilog.contains(target.getUniqueId()))) {
							antilog.remove(player.getUniqueId());
							antilog.remove(target.getUniqueId());
							String msg = messageManager.getMessage("combat-end");
							target.sendMessage(msg);
							player.sendMessage(msg);
						}
					}
				}, secs * 20L);
			}
		}
	}

	@EventHandler
	public void onAntiLogQuit(PlayerQuitEvent event) {
		Player p = event.getPlayer();
		if (plugin.game.gameState == GameState.ON) {
			if (this.antilog.contains(p.getUniqueId())) {
				String msg = plugin.getMessageManager().getMessage("combat-logout").replace("%player%", p.getDisplayName());
				Bukkit.getServer().broadcastMessage(msg);
				p.damage(20.0);
			}
		}
	}

}
