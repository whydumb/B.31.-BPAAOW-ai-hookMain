package me.red.movementracker.tracker.handler;

import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap;
import lombok.Getter;
import me.red.movementracker.tracker.PlayerTracker;
import org.bukkit.entity.Player;

import java.util.UUID;

@Getter
public class TrackerHandler {

    private final Object2ObjectArrayMap<UUID, PlayerTracker> actions = new Object2ObjectArrayMap<>();

    public boolean trackPlayer(Player player) {
        if (actions.containsKey(player.getUniqueId())) return false;

        actions.put(player.getUniqueId(), new PlayerTracker(player.getUniqueId()));
        return true;
    }

    public void removePlayer(Player player) {
        actions.remove(player.getUniqueId());
    }

    public boolean isTracked(Player player) {
        return actions.containsKey(player.getUniqueId());
    }

}
