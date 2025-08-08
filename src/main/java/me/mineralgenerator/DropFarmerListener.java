package me.mineralgenerator;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.CreatureSpawner;
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

public class DropFarmerListener implements Listener {
    private final JavaPlugin plugin;
    private final Map<Location, DropFarmerData> farmers = new HashMap<>();

    public DropFarmerListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlockPlaced();
        
        if (block.getType() == Material.MOB_SPAWNER) {
            if (isValidDropFarmer(block)) {
                farmers.put(block.getLocation(), new DropFarmerData(player.getUniqueId(), block.getLocation()));
                updateDropFarmerSign(block, false);
                player.sendMessage("§aDrop Farmer created! Use a lever to activate it.");
            }
        }
    }

    // Validate a 3x3x3 structure with specific block pattern for drop farmer
    private boolean isValidDropFarmer(Block mobSpawner) {
        Block center = mobSpawner.getRelative(BlockFace.DOWN);
        
        // Check 3x3x3 cube below the spawner (spawner is on top center)
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y >= -3; y--) {
                for (int z = -1; z <= 1; z++) {
                    Block block = center.getRelative(x, y, z);
                    if (block.getType() != Material.SMOOTH_BRICK) {
                        return false;
                    }
                }
            }
        }
        
        // Check for at least one sign attached to any block in the structure
        boolean hasSign = false;
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y >= -3; y--) {
                for (int z = -1; z <= 1; z++) {
                    Block block = center.getRelative(x, y, z);
                    for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
                        Block adjacent = block.getRelative(face);
                        if (adjacent.getType() == Material.WALL_SIGN) {
                            hasSign = true;
                            break;
                        }
                    }
                    if (hasSign) break;
                }
                if (hasSign) break;
            }
            if (hasSign) break;
        }
        
        return hasSign;
    }

    private void updateDropFarmerSign(Block mobSpawner, boolean active) {
        Block center = mobSpawner.getRelative(BlockFace.DOWN);
        
        // Find any sign attached to the structure
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y >= -3; y--) {
                for (int z = -1; z <= 1; z++) {
                    Block block = center.getRelative(x, y, z);
                    for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
                        Block signBlock = block.getRelative(face);
                        if (signBlock.getType() == Material.WALL_SIGN) {
                            Sign sign = (Sign) signBlock.getState();
                            sign.setLine(0, "§8[Drop Farmer]");
                            if (active) {
                                sign.setLine(1, "§aACTIVE");
                                sign.setLine(2, "§7Generating drops");
                                sign.setLine(3, "§7every 3 minutes");
                            } else {
                                sign.setLine(1, "§cINACTIVE");
                                sign.setLine(2, "§7Use lever to");
                                sign.setLine(3, "§7activate");
                            }
                            sign.update();
                            return; // Update only the first sign found
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Block clickedBlock = event.getClickedBlock();
            if (clickedBlock != null && clickedBlock.getType() == Material.LEVER) {
                // Check if this lever is part of a drop farmer structure
                Block mobSpawner = findNearbyDropFarmer(clickedBlock);
                if (mobSpawner != null) {
                    DropFarmerData data = farmers.get(mobSpawner.getLocation());
                    if (data != null) {
                        data.active = !data.active;
                        updateDropFarmerSign(mobSpawner, data.active);
                        
                        Player player = event.getPlayer();
                        if (data.active) {
                            player.sendMessage("§aDrop Farmer activated! It will generate mob drops every 3 minutes.");
                        } else {
                            player.sendMessage("§cDrop Farmer deactivated.");
                        }
                    }
                }
            }
        }
    }

    private Block findNearbyDropFarmer(Block lever) {
        // Check a 5x5x5 area around the lever for mob spawners
        for (int x = -2; x <= 2; x++) {
            for (int y = -2; y <= 2; y++) {
                for (int z = -2; z <= 2; z++) {
                    Block block = lever.getRelative(x, y, z);
                    if (block.getType() == Material.MOB_SPAWNER && farmers.containsKey(block.getLocation())) {
                        return block;
                    }
                }
            }
        }
        return null;
    }

    public void startDropFarmerTask() {
        int interval = plugin.getConfig().getInt("drop-farmer-interval", 180);
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            List<Location> toRemove = new ArrayList<>();
            for (DropFarmerData data : farmers.values()) {
                if (!data.active) continue;
                
                Block block = data.location.getBlock();
                if (block.getType() == Material.MOB_SPAWNER) {
                    // Check if structure is still valid
                    if (!isValidDropFarmer(block)) {
                        updateDropFarmerSign(block, false);
                        Player owner = Bukkit.getPlayer(data.owner);
                        if (owner != null) {
                            owner.sendMessage("§cYour Drop Farmer was removed because the structure is no longer valid!");
                        }
                        toRemove.add(data.location);
                        continue;
                    }
                    
                    CreatureSpawner spawner = (CreatureSpawner) block.getState();
                    
                    // Generate mob drops based on spawner type
                    List<ItemStack> drops = getMobDrops(spawner.getSpawnedType());
                    
                    // Try to put drops in nearby chest
                    Block chest = findNearbyChest(block);
                    if (chest != null && chest.getState() instanceof org.bukkit.block.Chest) {
                        Inventory inv = ((org.bukkit.block.Chest) chest.getState()).getBlockInventory();
                        for (ItemStack drop : drops) {
                            inv.addItem(drop);
                        }
                    }
                }
            }
            // Remove invalid farmers
            for (Location loc : toRemove) {
                farmers.remove(loc);
            }
        }, interval * 20L, interval * 20L);
    }

    private Block findNearbyChest(Block spawner) {
        // Check in a 3x3x3 area around the spawner for chests
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    Block block = spawner.getRelative(x, y, z);
                    if (block.getType() == Material.CHEST) {
                        return block;
                    }
                }
            }
        }
        return null;
    }

    public int getActiveCount(UUID playerId) {
        return (int) farmers.values().stream().filter(f -> f.owner.equals(playerId) && f.active).count();
    }

    private List<ItemStack> getMobDrops(org.bukkit.entity.EntityType type) {
        // Example: returns basic drops, can be expanded
        List<ItemStack> drops = new ArrayList<>();
        switch (type) {
            case ZOMBIE:
                drops.add(new ItemStack(Material.ROTTEN_FLESH, 1));
                break;
            case SKELETON:
                drops.add(new ItemStack(Material.BONE, 1));
                drops.add(new ItemStack(Material.ARROW, 1));
                break;
            case CREEPER:
                drops.add(new ItemStack(Material.SULPHUR, 1));
                break;
            default:
                drops.add(new ItemStack(Material.DIRT, 1)); // Placeholder
        }
        return drops;
    }

    private static class DropFarmerData {
        public final UUID owner;
        public final Location location;
        public boolean active;
        
        public DropFarmerData(UUID owner, Location location) {
            this.owner = owner;
            this.location = location;
            this.active = false;
        }
    }
}
