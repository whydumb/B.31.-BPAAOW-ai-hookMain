package me.red.movementracker.tracker;

import org.bson.Document;
import org.bukkit.ChatColor;

public record TrackerAction(int order, long time, ActionType action, float yaw, float pitch) {
    
    public String toString() {
        return ChatColor.GREEN + "Direzione: " + ChatColor.YELLOW + action.name() +
                ChatColor.GREEN + " | Yaw: " + ChatColor.YELLOW + String.format("%.2f", yaw) +
                ChatColor.GREEN + " | Pitch: " + ChatColor.YELLOW + String.format("%.2f", pitch);
    }
    
    /**
     * 1층 구조로 평면화된 Document 생성
     * 기존의 중첩 구조 대신 모든 필드를 최상위 레벨에 배치
     */
    public Document toFlatDocument(String replayName, String playerId, long duration) {
        return new Document()
                .append("_id", java.util.UUID.randomUUID().toString())
                .append("replay_name", replayName)
                .append("player_id", playerId)
                .append("order", order)
                .append("timestamp", time)
                .append("action", action.name().toLowerCase())
                .append("yaw", yaw)
                .append("pitch", pitch)
                .append("duration", duration)
                .append("created_at", System.currentTimeMillis());
    }
    
    /**
     * 기존 호환성을 위한 2층 구조 Document (기존 방식)
     */
    public Document toDocument() {
        return new Document("action", action.name().toLowerCase())
                .append("yaw", yaw)
                .append("pitch", pitch)
                .append("order", order)
                .append("timestamp", time);
    }
    
    @Override
    public boolean equals(Object object) {
        if (!(object instanceof TrackerAction trackerAction)) return false;
        return trackerAction.action().equals(action) && 
               trackerAction.yaw() == yaw && 
               trackerAction.pitch() == pitch && 
               trackerAction.order() == order;
    }
    
    @Override
    public int hashCode() {
        int result = Integer.hashCode(order);
        result = 31 * result + Float.hashCode(yaw);
        result = 31 * result + Float.hashCode(pitch);
        result = 31 * result + action.hashCode();
        return result;
    }
}

// PlayerTracker도 1층 구조를 지원하도록 수정
package me.red.movementracker.tracker;

import com.mongodb.client.model.InsertManyOptions;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import lombok.Data;
import lombok.Getter;
import me.red.movementracker.mongo.MongoManager;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
public class PlayerTracker {
    private final UUID uuid;
    private TrackerAction previousAction;
    
    @Getter
    private int currentOrder;
    
    @Getter
    private Object2LongOpenHashMap<TrackerAction> actions = new Object2LongOpenHashMap<>();
    
    public void add(TrackerAction action) {
        if (previousAction != null && !previousAction.equals(action)) {
            currentOrder++;
            action = new TrackerAction(currentOrder, action.time(), action.action(), action.yaw(), action.pitch());
        }
        
        previousAction = action;
        actions.put(action, actions.getOrDefault(action, 0) + 1);
    }
    
    /**
     * 1층 구조로 데이터 저장 (각 액션을 개별 Document로)
     */
    public void saveFlatData(String name) {
        try {
            List<Document> documents = new ArrayList<>();
            
            actions.keySet().forEach(action -> {
                long duration = actions.getLong(action);
                Document doc = action.toFlatDocument(name, uuid.toString(), duration);
                documents.add(doc);
            });
            
            // 배치 삽입으로 성능 최적화
            if (!documents.isEmpty()) {
                MongoManager.get().getMovements().insertMany(documents, new InsertManyOptions().ordered(false));
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * 기존 방식의 2층 구조 저장 (호환성 유지)
     */
    public void saveData(String name) {
        try {
            List<Document> steps = new ArrayList<>();
            actions.keySet().forEach(action ->
                steps.add(action.toDocument().append("duration", actions.getLong(action)))
            );
            
            Document document = new Document()
                    .append("replay_name", name)
                    .append("player_id", uuid.toString())
                    .append("steps", steps);
                    
            MongoManager.get().getMovements().insertOne(document);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}

// 명령어도 새로운 저장 방식을 지원하도록 수정
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
