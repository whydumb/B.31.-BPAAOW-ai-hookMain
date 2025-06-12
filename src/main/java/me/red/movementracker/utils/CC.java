
package me.red.movementracker.utils;

import lombok.experimental.UtilityClass;
import org.bukkit.ChatColor;

@UtilityClass
public class CC {

    /**
     * Translates color codes in a string using Bukkit's ChatColor
     * @param input String with color codes using &
     * @return Colored string
     */
    public String translate(String input) {
        return ChatColor.translateAlternateColorCodes('&', input);
    }
}