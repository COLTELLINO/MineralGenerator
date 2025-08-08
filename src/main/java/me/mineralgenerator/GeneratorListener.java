
package me.mineralgenerator;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.Sign;
// import org.bukkit.block.data.type.Lever; // Non esiste in 1.8.8
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.Location;
import java.util.*;

public class GeneratorListener implements Listener {
    private final MineralGeneratorPlugin plugin;
    private final Map<Location, GeneratorData> generators = new HashMap<>();
    private CompactorListener compactorListener;
    public void setCompactorListener(CompactorListener compactorListener) {
        this.compactorListener = compactorListener;
    }

    public GeneratorListener(MineralGeneratorPlugin plugin) {
        this.plugin = plugin;
    }

    // Controlla la costruzione del generatore
    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlockPlaced();
        if (block.getType() == Material.CHEST || block.getType() == Material.TRAPPED_CHEST) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (isValidGenerator(block)) {
                    // Trova il cartello e coloralo di rosso
                    updateGeneratorSign(block, "§c[GENERATORE]");
                    GeneratorData data = new GeneratorData(player.getUniqueId(), block.getLocation());
                    data.active = false;
                    generators.put(block.getLocation(), data);
                    player.sendMessage("§aGeneratore costruito! Per attivarlo usa la leva.");
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
                Block chest = findChestForLever(block);
                if (chest != null && generators.containsKey(chest.getLocation())) {
                    GeneratorData data = generators.get(chest.getLocation());
                    if (!data.active) {
                        int max = getMaxMachines(event.getPlayer());
                        int activeCount = getTotalActiveMachines(event.getPlayer().getUniqueId());
                        if (activeCount >= max) {
                            event.getPlayer().sendMessage("§cHai raggiunto il numero massimo di macchinari attivi!");
                            return;
                        }
                        data.active = true;
                        updateGeneratorSign(chest, "§a[GENERATORE]");
                        event.getPlayer().sendMessage("§aGeneratore attivato!");
                    } else {
                        data.active = false;
                        updateGeneratorSign(chest, "§c[GENERATORE]");
                        event.getPlayer().sendMessage("§cGeneratore disattivato!");
                    }
                }
            }
        }
    }

    // Aggiorna il colore del cartello del generatore
    private void updateGeneratorSign(Block chest, String text) {
        Block emerald = chest.getRelative(BlockFace.DOWN);
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

    // Conta tutti i macchinari attivi del player (generatori + compattatori)
    private int getTotalActiveMachines(UUID playerId) {
        int count = (int) generators.values().stream().filter(g -> g.owner.equals(playerId) && g.active).count();
        if (compactorListener != null) {
            count += compactorListener.getActiveCount(playerId);
        }
        return count;
    }

    public int getActiveCount(UUID playerId) {
        return (int) generators.values().stream().filter(g -> g.owner.equals(playerId) && g.active).count();
    }

    // Prende il massimo macchinari dal permesso
public int getMaxMachines(Player player) {
        for (int i = 100; i > 0; i--) {
            if (player.hasPermission("mineralgenerator." + i)) return i;
        }
        return 0;
    }

    // Task di generazione
    public void startGenerationTask() {
        int interval = plugin.getConfig().getInt("generation-interval", 60);
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            // Usiamo una lista per evitare ConcurrentModificationException
            List<Location> toRemove = new ArrayList<>();
            for (GeneratorData data : generators.values()) {
                if (data.active) {
                    Block block = data.location.getBlock();
                    if (!isValidGenerator(block)) {
                        // Aggiorna cartello, avvisa player, aggiungi a toRemove
                        updateGeneratorSign(block, "[GENERATORE]");
                        Player owner = Bukkit.getPlayer(data.owner);
                        if (owner != null) {
                            owner.sendMessage("§cIl tuo generatore è stato rimosso perché la struttura non è più valida!");
                        }
                        toRemove.add(data.location);
                        // Non generare e passa al prossimo
                        continue;
                    }
                    if (block.getType() == Material.CHEST || block.getType() == Material.TRAPPED_CHEST) {
                        Inventory inv = ((Chest) block.getState()).getBlockInventory();
                        Material mineral = getRandomMineral();
                        if (mineral != null) {
                            // Solo IRON_ORE e GOLD_ORE generano il blocco grezzo, gli altri l'item
                            ItemStack toAdd;
                            switch (mineral) {
                                case IRON_ORE:
                                case GOLD_ORE:
                                    toAdd = new ItemStack(mineral, 1);
                                    break;
                                case DIAMOND_ORE:
                                    toAdd = new ItemStack(Material.DIAMOND, 1);
                                    break;
                                case EMERALD_ORE:
                                    toAdd = new ItemStack(Material.EMERALD, 1);
                                    break;
                                case COAL_ORE:
                                    toAdd = new ItemStack(Material.COAL, 1);
                                    break;
                                case REDSTONE_ORE:
                                    toAdd = new ItemStack(Material.REDSTONE, 4); // come vanilla
                                    break;
                                case LAPIS_ORE:
                                    toAdd = new ItemStack(Material.INK_SACK, 4, (short)4); // Lapis in 1.8
                                    break;
                                case QUARTZ_ORE:
                                    toAdd = new ItemStack(Material.QUARTZ, 1);
                                    break;
                                case COBBLESTONE:
                                    toAdd = new ItemStack(Material.COBBLESTONE, 1);
                                    break;
                                default:
                                    toAdd = new ItemStack(mineral, 1);
                            }
                            inv.addItem(toAdd);
                        }
                    }
                }
            }
            // Rimuovi generatori non più validi
            for (Location loc : toRemove) {
                generators.remove(loc);
            }
        }, interval * 20L, interval * 20L);
    }

    // Utility: verifica la struttura del generatore
    private boolean isValidGenerator(Block chest) {
        // La chest deve essere sopra uno smeraldo
        Block emerald = chest.getRelative(BlockFace.DOWN);
        if (emerald.getType() != Material.EMERALD_BLOCK) return false;

        // Lo smeraldo deve essere sopra il centro del livello alto
        Block center = emerald.getRelative(BlockFace.DOWN).getRelative(BlockFace.DOWN).getRelative(BlockFace.WEST);

        // --- LIVELLO BASSO: 3x3 stone bricks ---
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Block b = center.getRelative(dx, 0, dz);
                if (b.getType() != Material.SMOOTH_BRICK) return false;
            }
        }

        // --- LIVELLO MEDIO: ogni lato stone, vetro, stone; centro glowstone ---
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Block b = center.getRelative(dx, 1, dz);
                if (dx == 0 && dz == 0) {
                    if (b.getType() != Material.GLOWSTONE) return false;
                } else if ((dx == 0 || dz == 0) && !(dx == 0 && dz == 0)) {
                    // Croce centrale: vetro
                    if (b.getType() != Material.GLASS) return false;
                } else {
                    // Angoli: stone
                    if (b.getType() != Material.SMOOTH_BRICK) return false;
                }
            }
        }

        // --- LIVELLO ALTO: pattern specifico ---
        // Due lati opposti: stone stone stone
        // Gli altri due: stone, ossidiana+cartello, stone  E  stone, smeraldo, stone
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
                // Lati
                else if (dx == 0 && Math.abs(dz) == 1) {
                    // Nord/Sud
                    if (b.getType() != Material.SMOOTH_BRICK) return false;
                } else if (dz == 0 && Math.abs(dx) == 1) {
                    // Est/Ovest
                    // Uno dei due deve essere ossidiana con cartello, l'altro smeraldo
                    if (!foundObsidianSign && b.getType() == Material.OBSIDIAN) {
                        // Deve avere un cartello attaccato con [GENERATORE] in seconda riga
                        for (BlockFace face : BlockFace.values()) {
                            if (face == BlockFace.UP || face == BlockFace.DOWN || face == BlockFace.SELF) continue;
                            Block signBlock = b.getRelative(face);
                            if (signBlock.getState() instanceof Sign) {
                                Sign sign = (Sign) signBlock.getState();
                                if (sign.getLine(1) != null && sign.getLine(1).equalsIgnoreCase("[GENERATORE]")
                                    || sign.getLine(1).equalsIgnoreCase("§c[GENERATORE]")
                                    || sign.getLine(1).equalsIgnoreCase("§a[GENERATORE]")) {
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
                        // Se non è nessuno dei due, deve essere stone
                        if (b.getType() != Material.SMOOTH_BRICK) return false;
                    }
                } else {
                    // Angoli: stone
                    if (b.getType() != Material.SMOOTH_BRICK) return false;
                }
            }
        }
        if (!foundObsidianSign || !foundEmeraldChest) return false;
        return true;
    }

    private Block findChestForLever(Block lever) {
        if (lever.getRelative(BlockFace.DOWN).getRelative(BlockFace.EAST).getRelative(BlockFace.UP).getType() == Material.CHEST ||
            lever.getRelative(BlockFace.DOWN).getRelative(BlockFace.EAST).getRelative(BlockFace.UP).getType() == Material.TRAPPED_CHEST) {
            Block chest = lever.getRelative(BlockFace.DOWN).getRelative(BlockFace.EAST).getRelative(BlockFace.UP);
            return chest;
        } else {
            return null;
        }
    }

    private Material getRandomMineral() {
        Map<String, Object> map = plugin.getConfig().getConfigurationSection("minerals").getValues(false);
        int total = 0;
        for (Object v : map.values()) total += (int) v;
        int rnd = new Random().nextInt(total) + 1;
        int sum = 0;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            sum += (int) e.getValue();
            if (rnd <= sum) {
                try {
                    return Material.valueOf(e.getKey());
                } catch (Exception ex) {
                    continue;
                }
            }
        }
        return null;
    }

    private static class GeneratorData {
        public final UUID owner;
        public final Location location;
        public boolean active = true;
        public GeneratorData(UUID owner, Location location) {
            this.owner = owner;
            this.location = location;
        }
    }
}
