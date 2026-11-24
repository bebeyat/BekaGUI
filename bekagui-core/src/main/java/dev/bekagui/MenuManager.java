package dev.bekagui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/** Listener + one {@link Session} per player. */
final class MenuManager implements Listener {

    private final Plugin plugin;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<UUID, Session>();
    private volatile long cooldown = 100;

    MenuManager(Plugin plugin) {
        this.plugin = plugin;
    }

    Plugin plugin() {
        return plugin;
    }

    void clickCooldown(long millis) {
        this.cooldown = millis;
    }

    void open(final Player player, final Menu menu) {
        Platform.run(plugin, player, () -> {
            if (!player.isOnline()) {
                return;
            }
            Session session = sessions.get(player.getUniqueId());
            if (session == null) {
                session = new Session(this, player);
                sessions.put(player.getUniqueId(), session);
            }
            session.show(new View(menu, player), true);
        });
    }

    void openInput(final Player player, final TextInput input) {
        Platform.run(plugin, player, () -> {
            if (!player.isOnline()) {
                return;
            }
            Session session = sessions.get(player.getUniqueId());
            if (session == null) {
                session = new Session(this, player);
                sessions.put(player.getUniqueId(), session);
            }
            session.showInput(input);
        });
    }

    boolean back(Player player) {
        Session session = sessions.get(player.getUniqueId());
        return session != null && session.back();
    }

    void close(final Player player) {
        Platform.run(plugin, player, player::closeInventory);
    }

    View view(Player player) {
        Session session = sessions.get(player.getUniqueId());
        return session != null && session.isOpen() ? session.current() : null;
    }

    // ------------------------------------------------------------------ events

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(InventoryClickEvent event) {
        if (inputClick(event)) {
            return;
        }
        Session.Holder holder = holder(event.getInventory());
        if (holder == null || !(event.getWhoClicked() instanceof Player)) {
            return;
        }
        Player player = (Player) event.getWhoClicked();
        Session session = holder.session;
        View view = session.current();
        int raw = event.getRawSlot();
        int topSize = event.getInventory().getSize();

        if (raw >= topSize || raw < 0) {
            // Player's own inventory: normal clicks allowed, anything that could move items into the menu is blocked.
            boolean risky = event.isShiftClick() || event.getAction() == InventoryAction.COLLECT_TO_CURSOR;
            if (risky || view == null || view.menu().lockPlayerInventory()) {
                event.setCancelled(true);
            }
            return;
        }
        event.setCancelled(true);
        if (view == null || event.getClick() == ClickType.DOUBLE_CLICK) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - session.lastClick < cooldown) {
            return;
        }
        session.lastClick = now;
        Button button = view.buttonAt(raw);
        if (button == null || button.handler() == null) {
            return;
        }
        try {
            button.handler().onClick(new Click(player, view, raw, event.getClick()));
        } catch (Throwable t) {
            plugin.getLogger().log(Level.SEVERE, "BekaGUI: button in slot " + raw + " threw an error", t);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(InventoryDragEvent event) {
        if (holder(event.getInventory()) == null && inputSession(event.getWhoClicked(), event.getInventory()) == null) {
            return;
        }
        int topSize = event.getInventory().getSize();
        for (int raw : event.getRawSlots()) {
            if (raw < topSize) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        if (event.getViewers().isEmpty()) {
            return;
        }
        Session session = inputSession(event.getViewers().get(0), event.getInventory());
        if (session == null) {
            return;
        }
        session.inputText(Platform.renameText(event));
        event.setResult(session.input().resultIcon());
        Platform.zeroCost(event);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        Session input = inputSession(event.getPlayer(), event.getInventory());
        if (input != null) {
            event.getInventory().clear();            // a real anvil would hand the left item to the player
            if (!input.switching()) {
                TextInput closed = input.input();
                finish(event.getPlayer().getUniqueId(), input);
                run("onCancel", () -> closed.cancelHandler().accept((Player) event.getPlayer()));
            }
            return;
        }
        Session.Holder holder = holder(event.getInventory());
        if (holder == null) {
            return;
        }
        Session session = holder.session;
        if (session.switching() || !event.getInventory().equals(session.inventory())) {
            return;                                  // a menu switch, not a real close
        }
        finish(event.getPlayer().getUniqueId(), session);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Session session = sessions.get(event.getPlayer().getUniqueId());
        if (session != null) {
            finish(event.getPlayer().getUniqueId(), session);
        }
    }

    @EventHandler
    public void onDisable(PluginDisableEvent event) {
        if (event.getPlugin() != plugin) {
            return;
        }
        for (Session session : new ArrayList<Session>(sessions.values())) {
            session.end();
            View view = session.current();
            if (view != null && session.isOpen()) {
                try {
                    view.player().closeInventory();  // never leave a menu without its click handlers
                } catch (Throwable ignored) {
                    // Folia may refuse off-thread closes during shutdown; the window dies with the plugin anyway.
                }
            }
        }
        sessions.clear();
    }

    private void finish(UUID id, Session session) {
        sessions.remove(id, session);
        session.end();
        View view = session.current();
        if (view != null && view.menu().onClose() != null) {
            try {
                view.menu().onClose().accept(view);
            } catch (Throwable t) {
                plugin.getLogger().log(Level.SEVERE, "BekaGUI: onClose threw an error", t);
            }
        }
    }

    /** Anvil text input clicks: everything in the anvil is cancelled, the result slot submits. */
    private boolean inputClick(InventoryClickEvent event) {
        Session session = inputSession(event.getWhoClicked(), event.getInventory());
        if (session == null) {
            return false;
        }
        int raw = event.getRawSlot();
        if (raw >= 3 || raw < 0) {
            if (event.isShiftClick() || event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
                event.setCancelled(true);
            }
            return true;
        }
        event.setCancelled(true);
        long now = System.currentTimeMillis();
        if (raw != 2 || now - session.lastClick < cooldown) {
            return true;
        }
        session.lastClick = now;
        final TextInput input = session.input();
        final String text = session.inputText();
        final Player player = (Player) event.getWhoClicked();
        run("onSubmit", () -> input.submitHandler().accept(player, text));
        return true;
    }

    private Session inputSession(HumanEntity who, Inventory inventory) {
        if (!(who instanceof Player) || inventory == null) {
            return null;
        }
        Session session = sessions.get(who.getUniqueId());
        return session != null && session.input() != null && inventory.equals(session.inventory()) ? session : null;
    }

    private void run(String what, Runnable task) {
        try {
            task.run();
        } catch (Throwable t) {
            plugin.getLogger().log(Level.SEVERE, "BekaGUI: " + what + " threw an error", t);
        }
    }

    private static Session.Holder holder(Inventory inventory) {
        return inventory != null && inventory.getHolder() instanceof Session.Holder ? (Session.Holder) inventory.getHolder() : null;
    }
}
