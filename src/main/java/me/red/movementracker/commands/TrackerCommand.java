package me.red.movementracker.commands;

import com.mongodb.client.FindIterable;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;
import me.red.movementracker.MovementTracker;
import me.red.movementracker.mongo.MongoManager;
import me.red.movementracker.tracker.PlayerTracker;
import me.red.movementracker.utils.CC;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentBuilder;
import net.kyori.adventure.text.TextComponent;
import org.bson.Document;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import revxrsal.commands.annotation.Command;
import revxrsal.commands.bukkit.actor.BukkitCommandActor;
import revxrsal.commands.bukkit.annotation.CommandPermission;

import java.util.concurrent.CompletableFuture;

public class TrackerCommand {
    private final MovementTracker plugin;

    public TrackerCommand(MovementTracker plugin) {
        this.plugin = plugin;
    }

    @Command("track help")
    @CommandPermission("movementtracker.help")
    public void onHelp(BukkitCommandActor actor) {
        actor.reply(CC.translate(
                """
                        &c&d&3
                        &aAvailable commands:
                        &7/track help &8- &fPrint this help message.
                        &7/track <target> <trackName> &8- &fStart tracking with track name.
                        &7/track <target> &8- &fStart tracking with default name.
                        &7/untrack <target> &8- &fStop tracking a determined target.
                        &7/track log <target> &8- &fPrint the target movement log from MongoDB.
                        
                        &cAll movement data is now saved directly to MongoDB.
                        &cNo memory storage is used - all data goes straight to database.
                        """
        ));
    }

    @Command("track")
    @CommandPermission("movementtracker.track")
    public void onTrack(Player player, Player target, String trackName) {
        if (!plugin.getTrackerHandler().trackPlayer(target, trackName)) {
            player.sendMessage(ChatColor.RED + "This player is being already tracked.");
            return;
        }
        player.sendMessage(ChatColor.GREEN + "Tracking " + target.getName() + " as '" + trackName + "'");
    }

    @Command("track")
    @CommandPermission("movementtracker.track")
    public void onTrackDefault(Player player, Player target) {
        if (!plugin.getTrackerHandler().trackPlayer(target)) {
            player.sendMessage(ChatColor.RED + "This player is being already tracked.");
            return;
        }
        player.sendMessage(ChatColor.GREEN + "Tracking " + target.getName());
    }

    @Command("untrack")
    @CommandPermission("movementtracker.untrack")
    public void onUntrack(Player player, Player target) {
        if (!plugin.getTrackerHandler().isTracked(target)) {
            player.sendMessage(ChatColor.RED + "This player is not tracked.");
            return;
        }
        plugin.getTrackerHandler().removePlayer(target);
        player.sendMessage(ChatColor.GREEN + "Untracked player " + target.getName());
    }

    @Command("track log")
    @CommandPermission("movementtracker.log")
    public void onTrackLog(Player player, Player target) {
        if (!plugin.getTrackerHandler().isTracked(target)) {
            player.sendMessage(ChatColor.RED + "This player is not tracked.");
            return;
        }

        PlayerTracker tracker = plugin.getTrackerHandler().getTracker(target);
        if (tracker == null) {
            player.sendMessage(ChatColor.RED + "Tracker not found for " + target.getName());
            return;
        }

        CompletableFuture.runAsync(() -> {
            try {
                // MongoDB에서 해당 플레이어의 추적 데이터 조회
                FindIterable<Document> documents = MongoManager.get().getMovements()
                        .find(Filters.and(
                                Filters.eq("player_id", target.getUniqueId().toString()),
                                Filters.eq("replay_name", tracker.getTrackName())
                        ))
                        .sort(Sorts.ascending("order"))
                        .limit(50); // 최대 50개만 표시

                ComponentBuilder<TextComponent, TextComponent.Builder> builder = Component.text();
                builder.append(Component.text(ChatColor.GREEN + "Movement log for " + target.getName() + ":"))
                        .appendNewline();

                int count = 0;
                for (Document doc : documents) {
                    if (count >= 50) break;

                    String action = doc.getString("action");
                    double yaw = doc.getDouble("yaw");
                    double pitch = doc.getDouble("pitch");
                    long duration = doc.getLong("duration");

                    builder.append(Component.text(CC.translate(
                            "&7Order: &f" + doc.getInteger("order") +
                                    " &7Action: &e" + action +
                                    " &7Yaw: &f" + String.format("%.2f", yaw) +
                                    " &7Pitch: &f" + String.format("%.2f", pitch) +
                                    " &7Duration: &b" + duration
                    ))).appendNewline();

                    count++;
                }

                if (count == 0) {
                    player.sendMessage(ChatColor.YELLOW + "No movement data found for " + target.getName());
                } else {
                    player.sendMessage(builder.build());
                    if (count >= 50) {
                        player.sendMessage(ChatColor.YELLOW + "Showing first 50 entries. Check MongoDB for complete data.");
                    }
                }

            } catch (Exception e) {
                player.sendMessage(ChatColor.RED + "Failed to retrieve movement log: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    // save, saveflat, saveunified, updatestats 명령어들은 제거
    // 이제 데이터가 실시간으로 MongoDB에 저장되므로 별도의 저장 과정이 필요 없음
}
