package me.red.movementracker.commands;

import me.red.movementracker.MovementTracker;

public class CommandHandler {
    protected final MovementTracker plugin;

    public CommandHandler(MovementTracker plugin) {
        this.plugin = plugin;
        register();
    }

    public void register() {
        // Register the command directly in plugin.yml and here
        plugin.getCommand("track").setExecutor(new TrackerCommand(plugin));
    }
}