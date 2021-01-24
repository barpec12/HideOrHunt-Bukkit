package me.latestion.hoh.events;

import java.util.List;

import me.latestion.hoh.game.HOHPlayer;
import me.latestion.hoh.localization.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import me.latestion.hoh.HideOrHunt;
import me.latestion.hoh.game.GameState;
import me.latestion.hoh.game.HOHTeam;
import me.latestion.hoh.utils.Util;

public class InventoryClick implements Listener {
	private HideOrHunt plugin;

	public InventoryClick(final HideOrHunt plugin) {
		this.plugin = plugin;
	}

	@EventHandler
	public void click(final InventoryClickEvent event) {
		if (!(event.getWhoClicked() instanceof Player)) {
			return;
		}
		if (event.getCurrentItem() == null) {
			return;
		}
		if (!event.getCurrentItem().hasItemMeta()) {
			return;
		}
		if (event.getClickedInventory().getType().equals(InventoryType.PLAYER)) {
			return;
		}
		MessageManager messageManager = plugin.getMessageManager();
		if (event.getView().getTitle().equals(messageManager.getMessage("team-inventory-title"))) {
			if (plugin.game.gameState != GameState.PREPARE) {
				return;
			}
			event.setCancelled(true);
			Player player = (Player) event.getWhoClicked();
			HOHPlayer hohPlayer = plugin.game.getHohPlayer(player.getUniqueId());
			if (event.getInventory().equals(plugin.game.inv) && event.getCurrentItem().getItemMeta().hasLore()) {
				List<String> lore = event.getCurrentItem().getItemMeta().getLore();
				if (lore.size() == 1) {
					lore.add(player.getName());
					ItemStack item = event.getCurrentItem();
					ItemMeta meta = item.getItemMeta();
					meta.setLore(lore);
					item.setItemMeta(meta);
					event.getClickedInventory().setItem(event.getSlot(), item);
					for (Player p : plugin.game.getWorld().getPlayers()) {
						p.updateInventory();
					}
					hohPlayer.setNamingTeam(true);
					HOHTeam team = new HOHTeam(event.getSlot());
					plugin.game.addTeam(team);
					hohPlayer.setTeam(team);
					team.addPlayer(hohPlayer);
					player.sendMessage(messageManager.getMessage("enter-team-name"));
					player.closeInventory();
					return;
				} else if (lore.size() - 1 < plugin.game.teamSize) {

					Util util = new Util(plugin);
					Player namingPlayer = Bukkit.getPlayerExact(ChatColor.stripColor(lore.get(1)));
					HOHPlayer namingHohPlayer = plugin.game.getHohPlayer(namingPlayer.getUniqueId());
					if (namingHohPlayer.isNamingTeam()) {
						player.sendMessage(messageManager.getMessage("still-naming-team"));
						return;
					}
					lore.add(player.getName());
					event.getCurrentItem().getItemMeta().setLore(lore);
					for (Player p2 : plugin.game.getWorld().getPlayers()) {
						p2.updateInventory();
					}

					Integer id = event.getSlot();
					HOHTeam team = plugin.game.getTeam(id);
					hohPlayer.setTeam(team);
					team.addPlayer(hohPlayer);

					player.closeInventory();
					if (plugin.game.allPlayersSelectedTeam() && plugin.game.areAllTeamsNamed()) {
						plugin.game.startGame();
					}
					return;
				} else {
					player.sendMessage(messageManager.getMessage("team-is-full"));
					return;
				}
			}
		}
	}
}
