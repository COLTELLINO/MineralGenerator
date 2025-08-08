package me.mineralgenerator;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Location;
import java.util.*;

public class BattleStationListener implements Listener {
    private final JavaPlugin plugin;
    private final Map<Location, BattleStationData> stations = new HashMap<>();

    public BattleStationListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlockPlaced();
        if (block.getType() == Material.BREWING_STAND) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (isValidBattleStation(block)) {
                    updateBattleStationSign(block, "§c[BATTLE STATION]");
                    BattleStationData data = new BattleStationData(player.getUniqueId(), block.getLocation());
                    data.active = false;
                    stations.put(block.getLocation(), data);
                    player.sendMessage("§aBattle Station costruita! Per attivarla usa la leva.");
                }
            }, 10L);
        }
    }

    // Attiva/disattiva tramite leva
    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Block block = event.getClickedBlock();
            if (block.getType() == Material.LEVER) {
                Block brewingStand = findBrewingStandForLever(block);
                if (brewingStand != null && stations.containsKey(brewingStand.getLocation())) {
                    BattleStationData data = stations.get(brewingStand.getLocation());
                    if (!data.active) {
                        data.active = true;
                        updateBattleStationSign(brewingStand, "§a[BATTLE STATION]");
                        event.getPlayer().sendMessage("§aBattle Station attivata!");
                    } else {
                        data.active = false;
                        updateBattleStationSign(brewingStand, "§c[BATTLE STATION]");
                        event.getPlayer().sendMessage("§cBattle Station disattivata!");
                    }
                }
            }
        }
    }

        // Aggiorna il colore del cartello della battle station
    private void updateBattleStationSign(Block brewingStand, String text) {
        Block diamond = brewingStand.getRelative(BlockFace.DOWN);
        if (diamond.getType() == Material.DIAMOND_BLOCK) {
            for (BlockFace signFace : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
                Block signBlock = diamond.getRelative(signFace);
                if (signBlock.getState() instanceof Sign) {
                    Sign sign = (Sign) signBlock.getState();
                    sign.setLine(0, "");
                    sign.setLine(1, text);
                    sign.update();
                    break; // Update only the first sign found
                }
            }
        }
    }

    public void startBattleStationTask() {
        int interval = plugin.getConfig().getInt("battle-station-interval", 120);
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            List<Location> toRemove = new ArrayList<>();
            for (BattleStationData data : stations.values()) {
                if (data.active) {
                    Block block = data.location.getBlock();
                    if (!isValidBattleStation(block)) {
                        updateBattleStationSign(block, "[BATTLE STATION]");
                        Player owner = Bukkit.getPlayer(data.owner);
                        if (owner != null) {
                            owner.sendMessage("§cLa tua Battle Station è stata rimossa perché la struttura non è più valida!");
                        }
                        toRemove.add(data.location);
                        continue;
                    }
                    if (block.getType() == Material.BREWING_STAND) {
                        Inventory inv = ((org.bukkit.block.BrewingStand) block.getState()).getInventory();
                        for (ItemStack item : inv.getContents()) {
                            if (item != null && item.getType() == Material.POTION) {
                                ItemStack clone = item.clone();
                                inv.addItem(clone);
                                break; // Only duplicate one potion per interval
                            }
                        }
                    }
                }
            }
            for (Location loc : toRemove) {
                stations.remove(loc);
            }
        }, interval * 20L, interval * 20L);
    }

    // Utility: verifica la struttura della battle station (semplice blocco singolo)
    private boolean isValidBattleStation(Block brewingStand) {
        // La brewing stand deve essere sopra un blocco di diamante
        Block diamond = brewingStand.getRelative(BlockFace.DOWN);
        if (diamond.getType() != Material.DIAMOND_BLOCK) return false;

        // Deve esserci un cartello attaccato al blocco di diamante
        boolean hasSign = false;
        for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            Block signBlock = diamond.getRelative(face);
            if (signBlock.getState() instanceof Sign) {
                Sign sign = (Sign) signBlock.getState();
                if (sign.getLine(1) != null && (sign.getLine(1).equalsIgnoreCase("[BATTLE STATION]")
                    || sign.getLine(1).equalsIgnoreCase("§c[BATTLE STATION]")
                    || sign.getLine(1).equalsIgnoreCase("§a[BATTLE STATION]"))) {
                    hasSign = true;
                    break;
                }
            }
        }
        
        return hasSign;
    }

    private Block findBrewingStandForLever(Block lever) {
        // Check a 3x3x3 area around the lever for brewing stands
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    Block block = lever.getRelative(x, y, z);
                    if (block.getType() == Material.BREWING_STAND && stations.containsKey(block.getLocation())) {
                        return block;
                    }
                }
            }
        }
        return null;
    }

    private static class BattleStationData {
        public final UUID owner;
        public final Location location;
        public boolean active = true;
        public BattleStationData(UUID owner, Location location) {
            this.owner = owner;
            this.location = location;
        }
    }
}
