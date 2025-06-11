package me.red.movementracker.tracker;

import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.UpdateOptions;
import com.mongodb.client.model.Updates;
import lombok.Data;
import lombok.Getter;
import me.red.movementracker.mongo.MongoManager;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.UUID;

@Data
public class PlayerTracker {
    private final UUID uuid;
    private final String trackName;
    private TrackerAction previousAction;

    @Getter
    private int currentOrder;

    // 메모리 저장용 Object2LongOpenHashMap 제거

    public PlayerTracker(UUID uuid, String trackName) {
        this.uuid = uuid;
        this.trackName = trackName;
    }

    public void add(TrackerAction action) {
        if (previousAction != null && !previousAction.equals(action)) {
            currentOrder++;
            action = new TrackerAction(currentOrder, action.time(), action.action(), action.yaw(), action.pitch());
        }

        if (previousAction == null || !previousAction.equals(action)) {
            // 메모리에 저장하지 않고 바로 MongoDB에 저장
            saveActionDirectly(action);
        } else {
            // 같은 액션이 계속되면 duration 업데이트
            updateLastActionDuration(action.time());
        }

        previousAction = action;
    }

    /**
     * 액션을 바로 MongoDB에 저장
     */
    private void saveActionDirectly(TrackerAction action) {
        try {
            Document actionDoc = new Document()
                    .append("_id", trackName + "_" + uuid.toString() + "_" + action.order())
                    .append("replay_name", trackName)
                    .append("player_id", uuid.toString())
                    .append("order", action.order())
                    .append("timestamp", action.time())
                    .append("action", action.action().name().toLowerCase())
                    .append("yaw", action.yaw())
                    .append("pitch", action.pitch())
                    .append("duration", 1L) // 초기값 1
                    .append("start_time", action.time())
                    .append("last_updated", action.time());

            MongoManager.get().getMovements().replaceOne(
                    new Document("_id", trackName + "_" + uuid.toString() + "_" + action.order()),
                    actionDoc,
                    new ReplaceOptions().upsert(true)
            );

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 마지막 액션의 duration 업데이트
     */
    private void updateLastActionDuration(long currentTime) {
        if (previousAction == null) return;

        try {
            String actionId = trackName + "_" + uuid.toString() + "_" + previousAction.order();
            
            Bson update = Updates.combine(
                    Updates.inc("duration", 1L),
                    Updates.set("last_updated", currentTime)
            );

            MongoManager.get().getMovements().updateOne(
                    new Document("_id", actionId),
                    update
            );

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 통계 정보 업데이트 (필요시)
     */
    public void updateStatistics(String name) {
        try {
            // 통계 계산을 위해 MongoDB에서 데이터를 조회
            // 실제 구현은 필요에 따라 추가
            
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 기존의 saveData, saveFlatData, saveUnifiedData 메소드들은 제거
    // 이제 메모리에 저장하지 않으므로 별도의 저장 과정이 필요 없음
}
