package me.mineralgenerator;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.ChatColor;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class MineralGeneratorPlugin extends JavaPlugin {
    private static MineralGeneratorPlugin instance;
    private GeneratorListener generatorListener;
    private CompactorListener compactorListener;
    private BattleStationListener battleStationListener;
    private DropFarmerListener dropFarmerListener;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        generatorListener = new GeneratorListener(this);
        compactorListener = new CompactorListener(this);
        battleStationListener = new BattleStationListener(this);
        dropFarmerListener = new DropFarmerListener(this);
        getServer().getPluginManager().registerEvents(generatorListener, this);
        getServer().getPluginManager().registerEvents(compactorListener, this);
        getServer().getPluginManager().registerEvents(battleStationListener, this);
        getServer().getPluginManager().registerEvents(dropFarmerListener, this);
        generatorListener.setCompactorListener(compactorListener);
        generatorListener.startGenerationTask();
        compactorListener.startCompactorTask();
        battleStationListener.startBattleStationTask();
        dropFarmerListener.startDropFarmerTask();
        getLogger().info("MineralGenerator abilitato!");
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
        getLogger().info("MineralGenerator disabilitato!");
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
