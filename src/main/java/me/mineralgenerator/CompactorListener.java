package me.mineralgenerator;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Location;
import java.util.*;

public class CompactorListener implements Listener {
    private final MineralGeneratorPlugin plugin;
    // Nuova struttura: location -> CompactorData (owner, location, attivo)
    private final Map<Location, CompactorData> compactors = new HashMap<>();
    // Carica un compattatore da file
    public void addLoadedCompactor(UUID owner, Location loc, boolean active) {
        CompactorData data = new CompactorData(owner, loc);
        data.active = active;
        compactors.put(loc, data);
    plugin.getLogger().info("\u001B[32m[DEBUG] Caricato compattatore: " + owner + " @ " + loc + " attivo=" + active + "\u001B[0m");
    }

    // Restituisce tutti i compattatori per il salvataggio
    public List<Object[]> getAllCompactorsForSave() {
        List<Object[]> list = new ArrayList<>();
        for (CompactorData data : compactors.values()) {
            list.add(new Object[]{data.owner, data.location, data.active});
            plugin.getLogger().info("\u001B[32m[DEBUG] Salvo compattatore: " + data.owner + " @ " + data.location + " attivo=" + data.active + "\u001B[0m");
        }
        return list;
    }

    public CompactorListener(MineralGeneratorPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlockPlaced();
        if (block.getType() == Material.CHEST || block.getType() == Material.TRAPPED_CHEST) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (isValidCompactor(block)) {
                    updateCompactorSign(block, "§c[COMPATTATORE]");
                    compactors.put(block.getLocation(), new CompactorData(player.getUniqueId(), block.getLocation()));
                    CompactorData compactorData = new CompactorData(player.getUniqueId(), block.getLocation());
                    compactorData.active = false;
                    compactors.put(block.getLocation(), compactorData);
                    player.sendMessage("§aCompattatore costruito! Per attivarlo usa la leva.");
                    plugin.saveMachines();
                }
            }, 10L);
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getAction() == org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) {
            Block block = event.getClickedBlock();
            if (block.getType() == Material.LEVER) {
                Block chest = findChestForLever(block);
                if (chest != null && compactors.containsKey(chest.getLocation())) {
                    CompactorData data = compactors.get(chest.getLocation());
                    if (!data.active) {
                        int max = plugin.getMaxMachines(event.getPlayer());
                        int activeCount = plugin.getTotalActiveMachines(event.getPlayer().getUniqueId());
                        if (activeCount >= max) {
                            event.getPlayer().sendMessage("§cHai raggiunto il numero massimo di macchinari attivi!");
                            return;
                        }
                        data.active = true;
                        updateCompactorSign(chest, "§a[COMPATTATORE]");
                        event.getPlayer().sendMessage("§aCompattatore attivato!");
                        plugin.saveMachines();
                    } else {
                        data.active = false;
                        updateCompactorSign(chest, "§c[COMPATTATORE]");
                        event.getPlayer().sendMessage("§cCompattatore disattivato!");
                        plugin.saveMachines();
                    }
                }
            }
        }
    }
    // Task automatico
    public void startCompactorTask() {
        int interval = plugin.getConfig().getInt("compactor-interval", 1); // default 1 secondo
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            List<Location> toRemove = new ArrayList<>();
            for (CompactorData data : compactors.values()) {
                if (!data.active) continue; // solo se attivo
                Block block = data.location.getBlock();
                if (!isValidCompactor(block)) {
                    updateCompactorSign(block, "[COMPATTATORE]");
                    Player owner = Bukkit.getPlayer(data.owner);
                    if (owner != null) {
                        owner.sendMessage("§cIl tuo compattatore è stato rimosso perché la struttura non è più valida!");
                    }
                    toRemove.add(data.location);
                    continue;
                }
                if (block.getType() == Material.CHEST || block.getType() == Material.TRAPPED_CHEST) {
                    compact(block);
                }
            }
            for (Location loc : toRemove) {
                compactors.remove(loc);
                plugin.saveMachines();
            }
        }, interval * 20L, interval * 20L);
    }

    // Rende accessibile il conteggio dei compattatori attivi
    public int getActiveCount(UUID playerId) {
        int count = 0;
        for (CompactorData data : compactors.values()) {
            if (data.owner.equals(playerId) && data.active) {
                count++;
            }
        }
        return count;
    }

    // Nuova classe dati per compattatore
    private static class CompactorData {
        public final UUID owner;
        public final Location location;
        public boolean active = false;
        public CompactorData(UUID owner, Location location) {
            this.owner = owner;
            this.location = location;
        }
    }

    private void compact(Block chestBlock) {
        Inventory inv = ((Chest) chestBlock.getState()).getBlockInventory();
        // Mappa: materiale base -> blocco corrispondente
        Map<Material, Material> craftMap = new HashMap<>();
        craftMap.put(Material.IRON_INGOT, Material.IRON_BLOCK);
        craftMap.put(Material.GOLD_INGOT, Material.GOLD_BLOCK);
        craftMap.put(Material.DIAMOND, Material.DIAMOND_BLOCK);
        craftMap.put(Material.EMERALD, Material.EMERALD_BLOCK);
        craftMap.put(Material.COAL, Material.COAL_BLOCK);
        craftMap.put(Material.REDSTONE, Material.REDSTONE_BLOCK);
        craftMap.put(Material.INK_SACK, Material.LAPIS_BLOCK); // 1.8 compat
        // Ignora il quartz (non compattare né eliminare)
        List<Material> ignore = Arrays.asList(Material.QUARTZ);
        // Rimuovi cobblestone e ore grezzi (COBBLESTONE, IRON_ORE, GOLD_ORE)
        List<Material> toDelete = Arrays.asList(Material.COBBLESTONE, Material.IRON_ORE, Material.GOLD_ORE);
        for (Map.Entry<Material, Material> entry : craftMap.entrySet()) {
            Material mat = entry.getKey();
            if (ignore.contains(mat)) continue;
            Material block = entry.getValue();
            int count = 0;
            for (ItemStack item : inv.getContents()) {
                if (item != null && item.getType() == mat) {
                    count += item.getAmount();
                }
            }
            while (count >= 9) {
                // Rimuovi 9 item
                int toRemove = 9;
                for (int i = 0; i < inv.getSize(); i++) {
                    ItemStack item = inv.getItem(i);
                    if (item != null && item.getType() == mat) {
                        int remove = Math.min(item.getAmount(), toRemove);
                        item.setAmount(item.getAmount() - remove);
                        if (item.getAmount() == 0) inv.setItem(i, null);
                        toRemove -= remove;
                        if (toRemove == 0) break;
                    }
                }
                // Aggiungi 1 blocco
                inv.addItem(new ItemStack(block, 1));
                count -= 9;
            }
        }
        // Elimina completamente cobblestone e ore grezzi
        for (Material mat : toDelete) {
            for (int i = 0; i < inv.getSize(); i++) {
                ItemStack item = inv.getItem(i);
                if (item != null && item.getType() == mat) {
                    inv.setItem(i, null);
                }
            }
        }
    }

    private boolean isValidCompactor(Block chest) {
        // Smeraldo sotto la chest
        Block emerald = chest.getRelative(BlockFace.DOWN);
        if (emerald.getType() != Material.EMERALD_BLOCK) return false;

        // Il centro della struttura è 2 blocchi sotto la chest
          Block center = emerald.getRelative(BlockFace.DOWN).getRelative(BlockFace.DOWN).getRelative(BlockFace.WEST);

        // Piano basso: 3x3 blocchi di diamante
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Block b = center.getRelative(dx, 0, dz);
                if (b.getType() != Material.DIAMOND_BLOCK) return false;
            }
        }

        // Piano medio: 3x3 blocchi di ferro
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Block b = center.getRelative(dx, 1, dz);
                if (b.getType() != Material.IRON_BLOCK) return false;
            }
        }

        // Piano alto: pattern specifico
        // Due lati opposti: 3 diamante
        // Gli altri due: diamante, ossidiana+cartello (seconda riga), diamante  E  diamante, smeraldo+chest, diamante
        // Centro: redstone block, sopra leva
        boolean foundObsidianSign = false;
        boolean foundEmeraldChest = false;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Block b = center.getRelative(dx, 2, dz);
                // Centro
                if (dx == 0 && dz == 0) {
                    if (b.getType() != Material.REDSTONE_BLOCK) return false;
                    Block lever = b.getRelative(BlockFace.UP);
                    if (lever.getType() != Material.LEVER) return false;
                }
                // Nord/Sud: solo diamante
                else if (dx == 0 && Math.abs(dz) == 1) {
                    if (b.getType() != Material.DIAMOND_BLOCK) return false;
                }
                // Est/Ovest: pattern
                else if (dz == 0 && Math.abs(dx) == 1) {
                    // Uno dei due deve essere ossidiana con cartello, l'altro smeraldo
                    if (!foundObsidianSign && b.getType() == Material.OBSIDIAN) {
                        // Deve avere un cartello attaccato con [COMPATTATORE] in seconda riga
                        for (BlockFace face : BlockFace.values()) {
                            if (face == BlockFace.UP || face == BlockFace.DOWN || face == BlockFace.SELF) continue;
                            Block signBlock = b.getRelative(face);
                            if (signBlock.getState() instanceof Sign) {
                                Sign sign = (Sign) signBlock.getState();
                                if (sign.getLine(1) != null && sign.getLine(1).equalsIgnoreCase("[COMPATTATORE]")
                                    || sign.getLine(1).equalsIgnoreCase("§c[COMPATTATORE]")
                                    || sign.getLine(1).equalsIgnoreCase("§a[COMPATTATORE]")) {
                                    foundObsidianSign = true;
                                    break;
                                }
                            }
                        }
                        if (!foundObsidianSign) return false;
                    } else if (!foundEmeraldChest && b.getType() == Material.EMERALD_BLOCK) {
                        // Sopra deve esserci la chest
                        Block chestBlock = b.getRelative(BlockFace.UP);
                        if (chestBlock.getType() != Material.CHEST && chestBlock.getType() != Material.TRAPPED_CHEST) return false;
                        if (!chestBlock.equals(chest)) return false;
                        foundEmeraldChest = true;
                    } else {
                        // Se non è nessuno dei due, deve essere diamante
                        if (b.getType() != Material.DIAMOND_BLOCK) return false;
                    }
                } else {
                    // Angoli: diamante
                    if (b.getType() != Material.DIAMOND_BLOCK) return false;
                }
            }
        }
        if (!foundObsidianSign || !foundEmeraldChest) return false;
        return true;
    }

    private Block findChestForLever(Block lever) {
        if (lever.getRelative(BlockFace.DOWN).getRelative(BlockFace.EAST).getRelative(BlockFace.UP).getType() == Material.CHEST ||
            lever.getRelative(BlockFace.DOWN).getRelative(BlockFace.WEST).getRelative(BlockFace.UP).getType() == Material.TRAPPED_CHEST) {
            Block chest = lever.getRelative(BlockFace.DOWN).getRelative(BlockFace.EAST).getRelative(BlockFace.UP);
            return chest;
        } else {
            return null;
        }
    }

    public void updateCompactorSign(org.bukkit.block.Block block, String text) {
        Block emerald = block.getRelative(BlockFace.DOWN);
        Block obsidian = emerald.getRelative(BlockFace.WEST).getRelative(BlockFace.WEST);
        if (obsidian.getType() == Material.OBSIDIAN) {
            for (BlockFace signFace : BlockFace.values()) {
                if (signFace == BlockFace.UP || signFace == BlockFace.DOWN || signFace == BlockFace.SELF) continue;
                Block signBlock = obsidian.getRelative(signFace);
                if (signBlock.getState() instanceof Sign) {
                    Sign sign = (Sign) signBlock.getState();
                    sign.setLine(0, ""); // Svuota la prima riga
                    sign.setLine(1, text); // Scrivi sulla seconda riga
                    sign.update();
                }
            }
        }
    }
}
