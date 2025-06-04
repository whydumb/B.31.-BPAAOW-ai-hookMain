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
                        &7/track save <target> <trackName> &8- &fSave the current movement log (2-layer structure).
                        &7/track saveflat <target> <trackName> &8- &fSave as flat structure (1-layer, better for queries).
                        
                        &cAll durations within the track logs are given in ticks so 1/20th of a second.
                        &cFlat structure saves each action as a separate document for easier analysis.
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

    /**
     * 기존 2층 구조 저장
     */
    @Command("track save")
    @CommandPermission("movementtracker.save")
    public void onSaveTrack(Player player, Player target, String trackName) {
        if (!trackerHandler.isTracked(target)) {
            player.sendMessage(ChatColor.RED + "This player is not tracked.");
            return;
        }

        PlayerTracker tracker = trackerHandler.getActions().get(target.getUniqueId());

        CompletableFuture.runAsync(() -> {
            tracker.saveData(trackName);
            player.sendMessage(ChatColor.GREEN + "Saved track " + trackName + " (2-layer structure)");
            tracker.getActions().clear();
        });
    }

    /**
     * 새로운 1층 구조 저장
     */
    @Command("track saveflat")
    @CommandPermission("movementtracker.save")
    public void onSaveFlatTrack(Player player, Player target, String trackName) {
        if (!trackerHandler.isTracked(target)) {
            player.sendMessage(ChatColor.RED + "This player is not tracked.");
            return;
        }

        PlayerTracker tracker = trackerHandler.getActions().get(target.getUniqueId());

        CompletableFuture.runAsync(() -> {
            tracker.saveFlatData(trackName);
            player.sendMessage(ChatColor.GREEN + "Saved track " + trackName + " (flat structure)");
            tracker.getActions().clear();
        });
    }
}
