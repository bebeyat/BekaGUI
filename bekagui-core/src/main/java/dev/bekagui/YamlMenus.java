package dev.bekagui;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * Menus written in YAML, no Java needed. Every {@code *.yml} in the folder is one menu; the file name is its id.
 * <pre>
 * YamlMenus menus = YamlMenus.load(this, new File(getDataFolder(), "menus"));
 * menus.open(player, "shop");          // opens menus/shop.yml
 * </pre>
 * File format (see the README for all keys):
 * <pre>
 * title: "&8Shop"
 * layout:
 *   - "#########"
 *   - "#..A.B..#"
 *   - "#########"
 * items:
 *   "#": { material: GRAY_STAINED_GLASS_PANE, name: " " }
 *   A:
 *     material: DIAMOND
 *     name: "&bHello {player}"
 *     lore: ["&7Click to get one"]
 *     actions: ["console: give {player} diamond 1", "message: &aEnjoy!"]
 *   B: { material: BARRIER, name: "&cClose", actions: [close] }
 * </pre>
 */
public final class YamlMenus {

    private final Plugin plugin;
    private final File folder;
    private final Map<String, Menu> menus = new LinkedHashMap<String, Menu>();
    private BiFunction<Player, String, String> placeholders = (player, text) -> text;

    private YamlMenus(Plugin plugin, File folder) {
        this.plugin = plugin;
        this.folder = folder;
    }

    /** Loads every menu in the folder. Broken files are reported in the console and skipped. */
    public static YamlMenus load(Plugin plugin, File folder) {
        YamlMenus menus = new YamlMenus(plugin, folder);
        menus.reload();
        return menus;
    }

    public synchronized void reload() {
        menus.clear();
        File[] files = folder.listFiles((dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null) {
            return;
        }
        for (File file : files) {
            String id = file.getName().substring(0, file.getName().length() - 4);
            try {
                menus.put(id, parse(YamlConfiguration.loadConfiguration(file)));
            } catch (RuntimeException e) {
                plugin.getLogger().warning("BekaGUI: menu '" + file.getName() + "' skipped: " + e.getMessage());
            }
        }
    }

    /** Extra placeholders, e.g. PlaceholderAPI: {@code menus.placeholders(PlaceholderAPI::setPlaceholders)}. {player} always works. */
    public YamlMenus placeholders(BiFunction<Player, String, String> replacer) {
        this.placeholders = replacer;
        return this;
    }

    public Menu get(String id) {
        return menus.get(id);
    }

    public Set<String> ids() {
        return Collections.unmodifiableSet(menus.keySet());
    }

    /** Opens a menu by id. Returns false (and tells the console) if it does not exist. */
    public boolean open(Player player, String id) {
        Menu menu = menus.get(id);
        if (menu == null) {
            plugin.getLogger().warning("BekaGUI: no menu named '" + id + "' in " + folder.getPath());
            return false;
        }
        menu.open(player);
        return true;
    }

    // ------------------------------------------------------------------ parsing

    private Menu parse(final YamlConfiguration yml) {
        Menu.Builder builder = Menu.builder().title(yml.getString("title", ""));
        List<String> layout = yml.getStringList("layout");
        if (!layout.isEmpty()) {
            builder.layout(layout.toArray(new String[0]));
        } else {
            builder.rows(yml.getInt("rows", 3));
        }
        builder.refresh(yml.getLong("refresh", 0));
        final Layout parsedLayout = layout.isEmpty() ? null : Layout.parse(layout.toArray(new String[0]));
        final int size = (parsedLayout != null ? parsedLayout.rows() : yml.getInt("rows", 3)) * 9;

        final List<ItemDef> defs = new ArrayList<ItemDef>();
        ConfigurationSection items = yml.getConfigurationSection("items");
        if (items != null) {
            for (String key : items.getKeys(false)) {
                ConfigurationSection sec = items.getConfigurationSection(key);
                if (sec == null) {
                    throw new IllegalArgumentException("items." + key + " must be a section");
                }
                defs.add(ItemDef.parse(key, sec, parsedLayout, size));
            }
        }
        builder.render(view -> {
            for (ItemDef def : defs) {
                Button button = def.button(view.player(), this);
                for (int slot : def.slots) {
                    view.set(slot, button);
                }
            }
        });
        return builder.build();
    }

    String fill(Player player, String text) {
        return placeholders.apply(player, text.replace("{player}", player.getName()));
    }

    void run(Click click, List<Action> actions) {
        Player player = click.player();
        for (Action a : actions) {
            String value = fill(player, a.value);
            if (a.type.equals("message")) {
                player.sendMessage(Text.color(value));
            } else if (a.type.equals("command")) {
                player.performCommand(value);
            } else if (a.type.equals("console")) {
                Platform.runGlobal(plugin, () -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), value));
            } else if (a.type.equals("open")) {
                Menu menu = menus.get(value);
                if (menu != null) {
                    click.open(menu);
                } else {
                    plugin.getLogger().warning("BekaGUI: action 'open: " + value + "' points to a missing menu");
                }
            } else if (a.type.equals("back")) {
                click.back();
            } else if (a.type.equals("close")) {
                click.close();
            } else if (a.type.equals("refresh")) {
                click.update();
            } else if (a.type.equals("title")) {
                click.title(value);
            } else if (a.type.equals("sound")) {
                player.playSound(player.getLocation(), value.toLowerCase(Locale.ROOT), 1f, 1f);
            }
        }
    }

    /** One entry under {@code items:}. */
    private static final class ItemDef {
        int[] slots;
        Material material;
        String name;
        List<String> lore;
        int amount;
        int model;
        boolean glow;
        List<Action> any;
        List<Action> left;
        List<Action> right;

        static ItemDef parse(String key, ConfigurationSection sec, Layout layout, int size) {
            ItemDef d = new ItemDef();
            String where = "items." + key;
            if (sec.isList("slots")) {
                List<Integer> list = sec.getIntegerList("slots");
                d.slots = new int[list.size()];
                for (int i = 0; i < d.slots.length; i++) {
                    d.slots[i] = list.get(i);
                }
            } else if (sec.isInt("slot")) {
                d.slots = new int[] {sec.getInt("slot")};
            } else if (key.length() == 1 && layout != null) {
                d.slots = layout.slots(key.charAt(0));
                if (d.slots.length == 0) {
                    throw new IllegalArgumentException(where + ": '" + key + "' is not in the layout (or add slot: N)");
                }
            } else {
                throw new IllegalArgumentException(where + ": use a one-character layout key, or slot: N / slots: [..]");
            }
            for (int slot : d.slots) {
                if (slot < 0 || slot >= size) {
                    throw new IllegalArgumentException(where + ": slot " + slot + " is outside the menu (size " + size + ")");
                }
            }
            String mat = sec.getString("material", "STONE");
            d.material = Material.matchMaterial(mat);
            if (d.material == null) {
                throw new IllegalArgumentException(where + ": unknown material '" + mat + "'");
            }
            d.name = sec.getString("name");
            d.lore = sec.getStringList("lore");
            d.amount = sec.getInt("amount", 1);
            d.model = sec.getInt("model", 0);
            d.glow = sec.getBoolean("glow", false);
            d.any = actions(sec, "actions", where);
            d.left = actions(sec, "left-actions", where);
            d.right = actions(sec, "right-actions", where);
            return d;
        }

        private static List<Action> actions(ConfigurationSection sec, String path, String where) {
            List<Action> out = new ArrayList<Action>();
            for (String line : sec.getStringList(path)) {
                try {
                    out.add(Action.parse(line));
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException(where + "." + path + ": " + e.getMessage());
                }
            }
            return out;
        }

        Button button(Player player, YamlMenus owner) {
            Item item = Item.of(material).amount(amount);
            if (name != null) {
                item.name(owner.fill(player, name));
            }
            if (!lore.isEmpty()) {
                List<String> filled = new ArrayList<String>(lore.size());
                for (String line : lore) {
                    filled.add(owner.fill(player, line));
                }
                item.lore(filled);
            }
            if (model != 0) {
                item.model(model);
            }
            if (glow) {
                item.glow();
            }
            if (any.isEmpty() && left.isEmpty() && right.isEmpty()) {
                return Button.display(item);
            }
            return Button.of(item, click -> {
                owner.run(click, any);
                owner.run(click, click.isRight() ? right : left);
            });
        }
    }
}
