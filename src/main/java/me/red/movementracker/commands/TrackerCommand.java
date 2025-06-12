package me.red.movementracker.commands;

import me.red.movementracker.MovementTracker;
import me.red.movementracker.tracker.PlayerTracker;
import me.red.movementracker.tracker.TrackerAction;
import me.red.movementracker.utils.CC;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Comparator;
import java.util.concurrent.CompletableFuture;

public class TrackerCommand implements CommandExecutor {

    private final MovementTracker plugin;

    public TrackerCommand(MovementTracker plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "help":
                if (!player.hasPermission("movementtracker.help")) {
                    player.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
                    return true;
                }
                sendHelp(player);
                break;
            case "track":
                if (!player.hasPermission("movementtracker.track")) {
                    player.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
                    return true;
                }

                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /track track <player>");
                    return true;
                }

                Player target = plugin.getServer().getPlayer(args[1]);
                if (target == null) {
                    player.sendMessage(ChatColor.RED + "Player not found.");
                    return true;
                }

                if (!plugin.getTrackerHandler().trackPlayer(target)) {
                    player.sendMessage(ChatColor.RED + "This player is being already tracked.");
                    return true;
                }

                player.sendMessage(ChatColor.GREEN + "Tracking " + target.getName());
                break;
            case "untrack":
                if (!player.hasPermission("movementtracker.untrack")) {
                    player.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
                    return true;
                }

                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /track untrack <player>");
                    return true;
                }

                Player targetUntrack = plugin.getServer().getPlayer(args[1]);
                if (targetUntrack == null) {
                    player.sendMessage(ChatColor.RED + "Player not found.");
                    return true;
                }

                if (!plugin.getTrackerHandler().isTracked(targetUntrack)) {
                    player.sendMessage(ChatColor.RED + "This player is not tracked.");
                    return true;
                }

                plugin.getTrackerHandler().removePlayer(targetUntrack);
                player.sendMessage(ChatColor.GREEN + "Untracked player " + targetUntrack.getName());
                break;
            case "log":
                if (!player.hasPermission("movementtracker.log")) {
                    player.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
                    return true;
                }

                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Usage: /track log <player>");
                    return true;
                }

                Player targetLog = plugin.getServer().getPlayer(args[1]);
                if (targetLog == null) {
                    player.sendMessage(ChatColor.RED + "Player not found.");
                    return true;
                }

                if (!plugin.getTrackerHandler().isTracked(targetLog)) {
                    player.sendMessage(ChatColor.RED + "This player is not tracked.");
                    return true;
                }

                PlayerTracker tracker = plugin.getTrackerHandler().getActions().get(targetLog.getUniqueId());

                StringBuilder builder = new StringBuilder();
                builder.append(ChatColor.YELLOW).append("Movement log for ").append(targetLog.getName()).append(":\n");

                tracker.getActions().keySet()
                        .stream()
                        .sorted(Comparator.comparingLong(TrackerAction::time))
                        .forEach(action -> {
                            builder.append(action.toString()).append(ChatColor.AQUA)
                                    .append(" (x").append(tracker.getActions().getLong(action)).append(")")
                                    .append("\n");
                        });

                player.sendMessage(builder.toString());
                break;
            case "save":
                if (!player.hasPermission("movementtracker.save")) {
                    player.sendMessage(ChatColor.RED + "You don't have permission to use this command.");
                    return true;
                }

                if (args.length < 3) {
                    player.sendMessage(ChatColor.RED + "Usage: /track save <player> <trackName>");
                    return true;
                }

                Player targetSave = plugin.getServer().getPlayer(args[1]);
                if (targetSave == null) {
                    player.sendMessage(ChatColor.RED + "Player not found.");
                    return true;
                }

                if (!plugin.getTrackerHandler().isTracked(targetSave)) {
                    player.sendMessage(ChatColor.RED + "This player is not tracked.");
                    return true;
                }

                String trackName = args[2];
                PlayerTracker trackerSave = plugin.getTrackerHandler().getActions().get(targetSave.getUniqueId());

                CompletableFuture.runAsync(() -> {
                    trackerSave.saveData(trackName);
                    player.sendMessage(ChatColor.GREEN + "Saved track " + trackName);

                    trackerSave.getActions().clear();
                });
                break;
            default:
                sendHelp(player);
                break;
        }

        return true;
    }

    private void sendHelp(Player player) {
        player.sendMessage(CC.translate(
                """
                        &c&d&3
                        &aAvailable commands:
                        &7/track help &8- &fPrint this help message.
                        &7/track track <target> &8- &fStart tracking a determined target.
                        &7/track untrack <target> &8- &fStop tracking a determined target.
                        &7/track log <target> &8- &fPrint the target movement log from the start of the tracking to this moment.
                        &7/track save <target> <trackName> &8- &fSave the current movement log of the tracker inside the mongodb database and clear the current log.
                        
                        &cAll durations within the track logs are given in ticks so 1/20th of a second.
                        &cIn order to get the mongodb database to work, you need to put the crediantials inside the config.
                        """
        ));
    }
}