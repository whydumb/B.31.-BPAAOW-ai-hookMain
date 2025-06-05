
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
