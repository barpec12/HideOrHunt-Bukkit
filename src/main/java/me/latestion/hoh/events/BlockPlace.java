package me.latestion.hoh.events;

import me.latestion.hoh.localization.MessageManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Sign;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;

import me.latestion.hoh.HideOrHunt;
import me.latestion.hoh.game.GameState;
import me.latestion.hoh.game.HOHPlayer;

public class BlockPlace implements Listener {

	private HideOrHunt plugin;
	
	public BlockPlace(HideOrHunt plugin) {
		this.plugin = plugin;
	}

	@EventHandler
	public void onPlace(BlockPlaceEvent event) {
		
		if (GameState.getCurrentGameState() != GameState.ON) return;
		
		if (event.getBlockPlaced().getType() == Material.BEACON) {
			HOHPlayer player = plugin.hohPlayer.get(event.getPlayer().getUniqueId());
			Location loc = event.getBlockPlaced().getLocation();
			player.getTeam().hasBeacon = true;
			player.getTeam().setBeacon(event.getBlockPlaced());
			MessageManager messageManager = plugin.getMessageManager();
			Bukkit.broadcastMessage(messageManager.getMessage("beacon-placed-broadcast").replace("%team%", player.getTeam().getName()));
			setSign(loc, player);
			plugin.sbUtil.beaconPlaceTeam(player.getTeam().getName());
			player.getTeam().hasBeacon = true;
		}
	}
	
	public void setSign(Location loc, HOHPlayer player) {
        loc.getWorld().getBlockAt(loc.clone().add(0, 1, 0)).setType(Material.OAK_SIGN);
        Sign sign =  (Sign) loc.getWorld().getBlockAt(loc.clone().add(0, 1, 0)).getState();
        sign.setLine(0, player.getTeam().getName());
        sign.update();
	}
	
}