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
