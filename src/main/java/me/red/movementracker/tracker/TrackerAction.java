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
