package me.red.movementracker.commands;

import me.red.movementracker.MovementTracker;
import me.red.movementracker.tracker.PlayerTracker;
import me.red.movementracker.tracker.TrackerAction;
import me.red.movementracker.tracker.handler.TrackerHandler;
import me.red.movementracker.utils.CC;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentBuilder;
import net.kyori.adventure.text.TextComponent;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.bukkit.actor.BukkitCommandActor;
import revxrsal.commands.bukkit.annotation.CommandPermission;

import java.util.Comparator;
import java.util.concurrent.CompletableFuture;

public class TrackerCommand {

    private final TrackerHandler trackerHandler;

    public TrackerCommand(MovementTracker plugin) {
        this.trackerHandler = plugin.getTrackerHandler();
    }

    @Command("track help")
    @CommandPermission("movementtracker.help")
    public void onHelp(BukkitCommandActor actor) {
        actor.reply(CC.translate(
                """
                        &c&d&3
                        &aAvailable commands:
                        &7/track help &8- &fPrint this help message.
                        &7/track <target> &8- &fStart tracking a determined target.
                        &7/untrack <target> &8- &fStop tracking a determined target.
                        &7/track log <target> &8- &fPrint the target movement log from the start of the tracking to this moment.
                        &7/track save <target> <trackName> &8- &fSave the current movement log of the tracker inside the mongodb database and clear the current log.
                        
                        &cAll durations within the track logs are given in ticks so 1/20th of a second.
                        &cIn order to get the mongodb database to work, you need to put the crediantials inside the config.
                        """
        ));
    }

    @Command("track")
    @CommandPermission("movementtracker.track")
    public void onTrack(Player player, Player target) {
        if (!trackerHandler.trackPlayer(target)) {
            player.sendMessage(ChatColor.RED + "This player is being already tracked.");
            return;
        }

        player.sendMessage(ChatColor.GREEN + "Tracking " + target.getName());
    }

    @Command("untrack")
    @CommandPermission("movementtracker.untrack")
    public void onUntrack(Player player, Player target) {
        if (!trackerHandler.isTracked(target)) {
            player.sendMessage(ChatColor.RED + "This player is not tracked.");
            return;
        }

        trackerHandler.removePlayer(target);
        player.sendMessage(ChatColor.GREEN + "Untracked player " + target.getName());
    }

    @Command("track log")
    @CommandPermission("movementtracker.log")
    public void onTrackLog(Player player, Player target) {
        if (!trackerHandler.isTracked(target)) {
            player.sendMessage(ChatColor.RED + "This player is not tracked.");
            return;
        }

        PlayerTracker tracker = trackerHandler.getActions().get(target.getUniqueId());
        //Display

        ComponentBuilder<TextComponent, TextComponent.Builder> builder = Component.text();

        tracker.getActions().keySet()
                .stream()
                .sorted(Comparator.comparingLong(TrackerAction::time))
                .forEach(action ->
                        builder.append(
                                Component.text(CC.translate(action + "&b (&fx" + tracker.getActions().getLong(action) + "&b)"))
                                        .appendNewline()
                        )
                );

        player.sendMessage(builder.build());
    }

    @Command("track save")
    @CommandPermission("movementtracker.save")
    public void onSaveTrack(Player player, Player target, String trackName) {
        if (!trackerHandler.isTracked(target)) {
            player.sendMessage(ChatColor.RED + "This player is not tracked.");
            return;
        }

        //Save logic
        PlayerTracker tracker = trackerHandler.getActions().get(target.getUniqueId());

        CompletableFuture.runAsync(() -> {
            tracker.saveData(trackName);
            player.sendMessage(ChatColor.GREEN + "Saved track " + trackName);

            tracker.getActions().clear();
        });
    }

}
