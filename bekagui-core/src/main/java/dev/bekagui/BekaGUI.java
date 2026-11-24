package dev.bekagui;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Entry point. Call {@link #init(Plugin)} once in {@code onEnable}, then build {@link Menu}s.
 * <pre>
 * public void onEnable() {
 *     BekaGUI.init(this);
 * }
 * </pre>
 * Works on Spigot, Paper and Folia (1.14+). On Folia everything runs on the player's region thread.
 */
public final class BekaGUI {

    private static MenuManager manager;

    private BekaGUI() {
    }

    /** Registers the listener. Safe to call again (e.g. after a reload). */
    public static synchronized void init(Plugin plugin) {
        if (manager != null && manager.plugin() == plugin && plugin.isEnabled()) {
            return;
        }
        manager = new MenuManager(plugin);
        plugin.getServer().getPluginManager().registerEvents(manager, plugin);
    }

    /** Opens a menu. Safe from any thread. Replaces an open BekaGUI menu without closing the window. */
    public static void open(Player player, Menu menu) {
        manager().open(player, menu);
    }

    /** Goes back to the previous menu. Returns false if there is none. */
    public static boolean back(Player player) {
        return manager().back(player);
    }

    /** Closes the window. */
    public static void close(Player player) {
        manager().close(player);
    }

    /** The menu the player is looking at, or null. */
    public static View view(Player player) {
        return manager().view(player);
    }

    /** Minimum time between two clicks of one player, in milliseconds (default 100). Stops double buys. */
    public static void clickCooldown(long millis) {
        manager().clickCooldown(Math.max(0, millis));
    }

    static MenuManager manager() {
        if (manager == null) {
            throw new IllegalStateException("Call BekaGUI.init(this) in your plugin's onEnable first");
        }
        return manager;
    }
}
