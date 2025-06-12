package me.red.movementracker.tracker;

import org.bson.Document;
import org.bukkit.ChatColor;

public record TrackerAction(int order, long time, ActionType action, float yaw, float pitch) {

    public String toString() {
        return ChatColor.GREEN + "Direction: " + ChatColor.YELLOW + action.name() +
                ChatColor.GREEN + " | Yaw: " + ChatColor.YELLOW + String.format("%.2f", yaw) +
                ChatColor.GREEN + " | Pitch: " + ChatColor.YELLOW + String.format("%.2f", pitch);
    }

    public Document toDocument() {
        return new Document("action", action.name().toLowerCase())
                .append("yaw", yaw)
                .append("pitch", pitch);
    }

    @Override
    public boolean equals(Object object) {
        if (!(object instanceof TrackerAction trackerAction)) return false;

        return trackerAction.action().equals(action) && trackerAction.yaw() == yaw && trackerAction.pitch() == pitch && trackerAction.order() == order;
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