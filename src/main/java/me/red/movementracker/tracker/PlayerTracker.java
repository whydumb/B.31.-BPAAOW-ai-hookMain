package me.red.movementracker.tracker;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import lombok.Data;
import lombok.Getter;
import me.red.movementracker.mongo.MongoManager;
import org.bson.Document;

import java.util.ArrayList;
import java.util.Comparator;
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

    private Document toDocument(String name) {
        List<Document> steps = new ArrayList<>();
        actions.keySet()
                .stream()
                .sorted(Comparator.comparingLong(TrackerAction::time))
                .forEach(action ->
                        steps.add(action.toDocument().append("duration", actions.getLong(action)))
                );

        return new Document()
                .append("replay_name", name)
                .append("player_id", uuid.toString())
                .append("steps", steps);
    }

    public void saveData(String name) {
        try {
            MongoManager.get().getMovements().replaceOne(Filters.eq("_id", UUID.randomUUID().toString()), toDocument(name), new ReplaceOptions().upsert(true));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}