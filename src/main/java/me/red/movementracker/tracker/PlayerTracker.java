package me.red.movementracker.tracker;

import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import lombok.Data;
import lombok.Getter;
import me.red.movementracker.mongo.MongoManager;
import org.bson.Document;
import org.bson.conversions.Bson;

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
     * 통합된 Document로 저장하는 새로운 방식
     * 하나의 Document에 모든 액션 데이터를 포함하여 저장
     */
    public void saveUnifiedData(String name) {
        try {
            String documentId = name + "_" + uuid.toString();
            
            // 액션 데이터를 배열로 구성
            List<Document> actionsList = new ArrayList<>();
            actions.keySet().forEach(action -> {
                long duration = actions.getLong(action);
                Document actionDoc = new Document()
                        .append("order", action.order())
                        .append("timestamp", action.time())
                        .append("action", action.action().name().toLowerCase())
                        .append("yaw", action.yaw())
                        .append("pitch", action.pitch())
                        .append("duration", duration);
                actionsList.add(actionDoc);
            });
            
            // 통합 Document 생성
            Document unifiedDoc = new Document()
                    .append("_id", documentId)
                    .append("replay_name", name)
                    .append("player_id", uuid.toString())
                    .append("actions", actionsList)
                    .append("total_actions", actionsList.size())
                    .append("created_at", System.currentTimeMillis())
                    .append("last_updated", System.currentTimeMillis());
            
            // upsert를 사용하여 존재하면 업데이트, 없으면 삽입
            MongoManager.get().getMovements().replaceOne(
                    new Document("_id", documentId),
                    unifiedDoc,
                    new UpdateOptions().upsert(true)
            );
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * 기존 Document에 새로운 액션만 추가/업데이트
     * 전체를 다시 저장하지 않고 특정 액션만 수정
     */
    public void updateSpecificAction(String name, TrackerAction action, long duration) {
        try {
            String documentId = name + "_" + uuid.toString();
            
            Document actionDoc = new Document()
                    .append("order", action.order())
                    .append("timestamp", action.time())
                    .append("action", action.action().name().toLowerCase())
                    .append("yaw", action.yaw())
                    .append("pitch", action.pitch())
                    .append("duration", duration);
            
            // 특정 액션을 배열에 추가하고 last_updated 시간 갱신
            Bson update = Updates.combine(
                    Updates.push("actions", actionDoc),
                    Updates.inc("total_actions", 1),
                    Updates.set("last_updated", System.currentTimeMillis())
            );
            
            MongoManager.get().getMovements().updateOne(
                    new Document("_id", documentId),
                    update,
                    new UpdateOptions().upsert(true)
            );
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * 특정 액션의 duration만 업데이트
     */
    public void updateActionDuration(String name, int actionOrder, long newDuration) {
        try {
            String documentId = name + "_" + uuid.toString();
            
            // 배열 내 특정 요소의 duration 필드만 업데이트
            Bson filter = new Document("_id", documentId)
                    .append("actions.order", actionOrder);
            
            Bson update = Updates.combine(
                    Updates.set("actions.$.duration", newDuration),
                    Updates.set("last_updated", System.currentTimeMillis())
            );
            
            MongoManager.get().getMovements().updateOne(filter, update);
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Document 내 통계 정보 업데이트
     */
    public void updateStatistics(String name) {
        try {
            String documentId = name + "_" + uuid.toString();
            
            // 현재 액션들의 통계 계산
            long totalDuration = actions.values().stream().mapToLong(Long::longValue).sum();
            int uniqueActions = actions.size();
            
            Bson update = Updates.combine(
                    Updates.set("total_duration", totalDuration),
                    Updates.set("unique_actions", uniqueActions),
                    Updates.set("last_updated", System.currentTimeMillis())
            );
            
            MongoManager.get().getMovements().updateOne(
                    new Document("_id", documentId),
                    update
            );
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    /**
     * 1층 구조로 평면화된 Document 생성 (기존 방식 유지)
     */
    public void saveFlatData(String name) {
        try {
            List<Document> documents = new ArrayList<>();
            
            actions.keySet().forEach(action -> {
                long duration = actions.getLong(action);
                Document doc = action.toFlatDocument(name, uuid.toString(), duration);
                documents.add(doc);
            });
            
            if (!documents.isEmpty()) {
                MongoManager.get().getMovements().insertMany(documents);
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
