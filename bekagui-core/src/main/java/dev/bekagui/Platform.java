package dev.bekagui;

import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.util.function.Consumer;

/**
 * Server differences, all through reflection so one jar runs everywhere:
 * <ul>
 *   <li>Folia: entity scheduler instead of the Bukkit scheduler.</li>
 *   <li>InventoryView became an interface in 1.21; calling it directly from code compiled against older
 *       versions breaks, so its methods are looked up at runtime.</li>
 *   <li>InventoryView#setTitle exists from 1.20; older servers fall back to reopening (still without a close).</li>
 * </ul>
 */
final class Platform {

    static final boolean FOLIA = exists("io.papermc.paper.threadedregions.RegionizedServer");

    private static final Method GET_TOP = method("org.bukkit.inventory.InventoryView", "getTopInventory");
    private static final Method SET_TITLE = method("org.bukkit.inventory.InventoryView", "setTitle", String.class);
    private static final Method IS_OWNED = FOLIA ? method("org.bukkit.Bukkit", "isOwnedByCurrentRegion", Entity.class) : null;
    private static final Method GET_SCHEDULER = FOLIA ? method("org.bukkit.entity.Entity", "getScheduler") : null;
    private static final Method RUN = FOLIA ? method("io.papermc.paper.threadedregions.scheduler.EntityScheduler", "run",
            Plugin.class, Consumer.class, Runnable.class) : null;
    private static final Method RUN_AT_RATE = FOLIA ? method("io.papermc.paper.threadedregions.scheduler.EntityScheduler",
            "runAtFixedRate", Plugin.class, Consumer.class, Runnable.class, long.class, long.class) : null;
    private static final Method GET_GLOBAL = FOLIA ? method("org.bukkit.Bukkit", "getGlobalRegionScheduler") : null;
    private static final Method GLOBAL_EXECUTE = FOLIA ? method("io.papermc.paper.threadedregions.scheduler.GlobalRegionScheduler",
            "execute", Plugin.class, Runnable.class) : null;
    private static final Method CANCEL =FOLIA ? method("io.papermc.paper.threadedregions.scheduler.ScheduledTask", "cancel") : null;

    private Platform() {
    }

    /** Runs now if we are on the player's thread, otherwise on it as soon as possible. */
    static void run(Plugin plugin, Player player, Runnable task) {
        if (isOwner(player)) {
            task.run();
        } else if (FOLIA) {
            Consumer<Object> body = t -> task.run();
            invoke(RUN, invoke(GET_SCHEDULER, player), plugin, body, null);
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    /** Runs on the global thread (console commands on Folia must run there). */
    static void runGlobal(Plugin plugin, Runnable task) {
        if (FOLIA) {
            invoke(GLOBAL_EXECUTE, invoke(GET_GLOBAL, null), plugin, task);
        } else if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }

    /** Repeats on the player's thread. Returns a canceller. */
    static Runnable repeat(Plugin plugin, Player player, long periodTicks, Runnable task) {
        if (FOLIA) {
            Consumer<Object> body = t -> task.run();
            final Object handle = invoke(RUN_AT_RATE, invoke(GET_SCHEDULER, player), plugin, body, null, periodTicks, periodTicks);
            return () -> {
                if (handle != null) {
                    invoke(CANCEL, handle);
                }
            };
        }
        final BukkitTask handle = Bukkit.getScheduler().runTaskTimer(plugin, task, periodTicks, periodTicks);
        return handle::cancel;
    }

    static boolean isOwner(Player player) {
        if (FOLIA) {
            Object owned = invoke(IS_OWNED, null, player);
            return Boolean.TRUE.equals(owned);
        }
        return Bukkit.isPrimaryThread();
    }

    static Inventory topInventory(Player player) {
        Object view = player.getOpenInventory();
        return view == null ? null : (Inventory) invoke(GET_TOP, view);
    }

    /** Changes the title of the open window in place. False if the server is too old for that. */
    static boolean setTitle(Player player, String title) {
        if (SET_TITLE == null) {
            return false;
        }
        try {
            SET_TITLE.invoke(player.getOpenInventory(), title);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ------------------------------------------------------------------ titles

    private static final Class<?> COMPONENT = type("net.kyori.adventure.text.Component");
    private static final Method CREATE_WITH_COMPONENT = COMPONENT == null ? null
            : method("org.bukkit.Bukkit", "createInventory", org.bukkit.inventory.InventoryHolder.class, int.class, COMPONENT);

    /** Strings get colored, Adventure components pass through. */
    static Object title(Object value) {
        if (value == null) {
            return "";
        }
        return value instanceof String ? Text.color((String) value) : value;
    }

    static Inventory createInventory(org.bukkit.inventory.InventoryHolder holder, int size, Object title) {
        if (title instanceof String) {
            return Bukkit.createInventory(holder, size, (String) title);
        }
        if (CREATE_WITH_COMPONENT == null || !COMPONENT.isInstance(title)) {
            throw new IllegalArgumentException("Component titles need Paper (Adventure); got " + title.getClass().getName());
        }
        return (Inventory) invoke(CREATE_WITH_COMPONENT, null, holder, size, title);
    }

    // ------------------------------------------------------------------ anvil text input

    private static final Method OPEN_VIEW = method("org.bukkit.entity.HumanEntity", "openInventory", type("org.bukkit.inventory.InventoryView"));
    private static final Method PAPER_OPEN_ANVIL = method("org.bukkit.entity.HumanEntity", "openAnvil", org.bukkit.Location.class, boolean.class);

    /** Opens a real anvil (rename works) over the current window and returns its inventory, or null if impossible. */
    static Inventory openAnvil(Player player, Object title) {
        Object view = anvilViaMenuType(player, title);
        if (view != null) {
            invoke(OPEN_VIEW, player, view);
        } else if (PAPER_OPEN_ANVIL != null) {
            view = invoke(PAPER_OPEN_ANVIL, player, null, true);
            if (view != null && title instanceof String) {
                setTitle(player, (String) title);
            }
        }
        return view == null ? null : (Inventory) invoke(GET_TOP, view);
    }

    /** 1.21+: MenuType.ANVIL.create(player, title) on Spigot (String) and Paper (Component). */
    private static Object anvilViaMenuType(Player player, Object title) {
        Class<?> typed = type("org.bukkit.inventory.MenuType$Typed");
        Class<?> menuType = type("org.bukkit.inventory.MenuType");
        if (typed == null || menuType == null) {
            return null;
        }
        try {
            Object anvil = menuType.getField("ANVIL").get(null);
            Method byString = null;
            Method byComponent = null;
            for (Method m : typed.getMethods()) {
                if (!m.getName().equals("create") || m.getParameterCount() != 2) {
                    continue;
                }
                Class<?> p = m.getParameterTypes()[1];
                if (p == String.class) {
                    byString = m;
                } else if (COMPONENT != null && p == COMPONENT) {
                    byComponent = m;
                }
            }
            if (title instanceof String && byString != null) {
                return byString.invoke(anvil, player, title);
            }
            if (byComponent != null) {
                Object component = title instanceof String ? legacyToComponent((String) title) : title;
                return byComponent.invoke(anvil, player, component);
            }
            return byString == null ? null : byString.invoke(anvil, player, String.valueOf(title));
        } catch (Exception e) {
            return null;
        }
    }

    private static Object legacyToComponent(String text) throws Exception {
        Class<?> serializer = Class.forName("net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer");
        Object legacy = serializer.getMethod("legacySection").invoke(null);
        return serializer.getMethod("deserialize", String.class).invoke(legacy, text);
    }

    /** Text the player typed. Reads AnvilView (1.21+) or AnvilInventory (older). */
    static String renameText(org.bukkit.event.inventory.PrepareAnvilEvent event) {
        Object text = call(event.getView(), "getRenameText");
        if (text == null) {
            text = call(event.getInventory(), "getRenameText");
        }
        return text == null ? "" : text.toString();
    }

    /** No level cost label, no "Too Expensive!". */
    static void zeroCost(org.bukkit.event.inventory.PrepareAnvilEvent event) {
        if (call(event.getView(), "setRepairCost", 0) == null) {
            call(event.getInventory(), "setRepairCost", 0);
        }
    }

    /** Item name without italics or color, so the anvil field starts with exactly {@code text} ("" = empty field). */
    static void setPlainName(org.bukkit.inventory.ItemStack stack, String text) {
        org.bukkit.inventory.meta.ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        boolean done = false;
        if (COMPONENT != null) {
            try {
                Object component = COMPONENT.getMethod("text", String.class).invoke(null, text);
                meta.getClass().getMethod("customName", COMPONENT).invoke(meta, component);
                done = true;
            } catch (Exception ignored) {
                // not Paper
            }
        }
        if (!done) {
            meta.setDisplayName("§r" + text);
        }
        stack.setItemMeta(meta);
    }

    /** Calls a public method by name if it exists. Returns its result, Boolean.TRUE for void, null if missing/failed. */
    private static Object call(Object target, String name, Object... args) {
        if (target == null) {
            return null;
        }
        for (Method m : target.getClass().getMethods()) {
            if (m.getName().equals(name) && m.getParameterCount() == args.length) {
                try {
                    m.setAccessible(true);
                    Object r = m.invoke(target, args);
                    return r == null ? Boolean.TRUE : r;
                } catch (Exception e) {
                    return null;
                }
            }
        }
        return null;
    }

    private static Class<?> type(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    private static Object invoke(Method method, Object target, Object... args) {
        try {
            return method.invoke(target, args);
        } catch (Exception e) {
            throw new IllegalStateException("BekaGUI: " + method.getName() + " failed", e);
        }
    }

    private static boolean exists(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private static Method method(String owner, String name, Class<?>... params) {
        try {
            return Class.forName(owner).getMethod(name, params);
        } catch (Exception e) {
            return null;
        }
    }
}
