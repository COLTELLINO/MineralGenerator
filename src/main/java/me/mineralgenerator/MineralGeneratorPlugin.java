package me.mineralgenerator;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.ChatColor;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import org.bukkit.Location;

public class MineralGeneratorPlugin extends JavaPlugin {
    private static MineralGeneratorPlugin instance;
    private GeneratorListener generatorListener;
    private CompactorListener compactorListener;

    private File machinesFile;
    private FileConfiguration machinesConfig;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        generatorListener = new GeneratorListener(this);
        compactorListener = new CompactorListener(this);
        getServer().getPluginManager().registerEvents(generatorListener, this);
        getServer().getPluginManager().registerEvents(compactorListener, this);
        generatorListener.setCompactorListener(compactorListener);
        generatorListener.startGenerationTask();
        compactorListener.startCompactorTask();
        getLogger().info("MineralGenerator abilitato!");

        // Carica macchinari da machines.yml
        loadMachines();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (label.equalsIgnoreCase("mineralgenerator") || label.equalsIgnoreCase("mg")) {
            if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                if (!sender.hasPermission("mineralgenerator.reload")) {
                    sender.sendMessage(ChatColor.RED + "Non hai il permesso di eseguire questo comando.");
                    return true;
                }
                reloadConfig();
                sender.sendMessage(ChatColor.GREEN + "Config ricaricato!");
                return true;
            }
            sender.sendMessage(ChatColor.YELLOW + "/mineralgenerator reload - Ricarica il config.yml");
            return true;
        }
        return false;
    }

    @Override
    public void onDisable() {
        // Salva macchinari su machines.yml
        saveMachines();
        getLogger().info("MineralGenerator disabilitato!");
    }

    private void loadMachines() {
    getLogger().info("\u001B[32m[DEBUG] Sto caricando le macchine...\u001B[0m");
        machinesFile = new File(getDataFolder(), "machines.yml");
        if (!machinesFile.exists()) {
            saveResource("machines.yml", false);
        }
        machinesConfig = YamlConfiguration.loadConfiguration(machinesFile);

        // Carica generatori
        if (machinesConfig.isList("generators")) {
            for (Object obj : machinesConfig.getList("generators")) {
                if (obj instanceof java.util.Map) {
                    java.util.Map<?,?> map = (java.util.Map<?,?>) obj;
                    try {
                        UUID owner = UUID.fromString(map.get("owner").toString());
                        String world = map.get("world").toString();
                        double x = Double.parseDouble(map.get("x").toString());
                        double y = Double.parseDouble(map.get("y").toString());
                        double z = Double.parseDouble(map.get("z").toString());
                        boolean active = Boolean.parseBoolean(map.get("active").toString());
                        Location loc = new Location(getServer().getWorld(world), x, y, z);
                        generatorListener.addLoadedGenerator(owner, loc, active);
                    } catch (Exception e) {
                        getLogger().warning("[DEBUG] Errore caricando un generatore: " + e.getMessage());
                    }
                }
            }
        }
        // Carica compattatori
        if (machinesConfig.isList("compactors")) {
            for (Object obj : machinesConfig.getList("compactors")) {
                if (obj instanceof java.util.Map) {
                    java.util.Map<?,?> map = (java.util.Map<?,?>) obj;
                    try {
                        UUID owner = UUID.fromString(map.get("owner").toString());
                        String world = map.get("world").toString();
                        double x = Double.parseDouble(map.get("x").toString());
                        double y = Double.parseDouble(map.get("y").toString());
                        double z = Double.parseDouble(map.get("z").toString());
                        boolean active = Boolean.parseBoolean(map.get("active").toString());
                        Location loc = new Location(getServer().getWorld(world), x, y, z);
                        compactorListener.addLoadedCompactor(owner, loc, active);
                    } catch (Exception e) {
                        getLogger().warning("[DEBUG] Errore caricando un compattatore: " + e.getMessage());
                    }
                }
            }
        }
    }

    public void saveMachines() {
        machinesFile = new File(getDataFolder(), "machines.yml");
        machinesConfig = new YamlConfiguration();

        // Salva generatori
        java.util.List<java.util.Map<String, Object>> gens = new java.util.ArrayList<>();
        for (Object[] gen : generatorListener.getAllGeneratorsForSave()) {
            UUID owner = (UUID) gen[0];
            Location loc = (Location) gen[1];
            boolean active = (Boolean) gen[2];
            java.util.Map<String, Object> map = new java.util.HashMap<>();
            map.put("owner", owner.toString());
            map.put("world", loc.getWorld().getName());
            map.put("x", loc.getX());
            map.put("y", loc.getY());
            map.put("z", loc.getZ());
            map.put("active", active);
            gens.add(map);
        }
        machinesConfig.set("generators", gens);

        // Salva compattatori
        java.util.List<java.util.Map<String, Object>> comps = new java.util.ArrayList<>();
        for (Object[] comp : compactorListener.getAllCompactorsForSave()) {
            UUID owner = (UUID) comp[0];
            Location loc = (Location) comp[1];
            boolean active = (Boolean) comp[2];
            java.util.Map<String, Object> map = new java.util.HashMap<>();
            map.put("owner", owner.toString());
            map.put("world", loc.getWorld().getName());
            map.put("x", loc.getX());
            map.put("y", loc.getY());
            map.put("z", loc.getZ());
            map.put("active", active);
            comps.add(map);
        }
        machinesConfig.set("compactors", comps);

        try {
            machinesConfig.save(machinesFile);
        } catch (IOException e) {
            getLogger().warning("Impossibile salvare machines.yml: " + e.getMessage());
        }
    }

    public static MineralGeneratorPlugin getInstance() {
        return instance;
    }

    // Espone il conteggio unico
    public int getTotalActiveMachines(UUID playerId) {
        int gen = generatorListener.getActiveCount(playerId);
        int comp = compactorListener.getActiveCount(playerId);
        return gen + comp;
    }
    public int getMaxMachines(Player player) {
        return generatorListener.getMaxMachines(player);
    }
}
