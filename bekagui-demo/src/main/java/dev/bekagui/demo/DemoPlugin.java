package dev.bekagui.demo;

import dev.bekagui.BekaGUI;
import dev.bekagui.Button;
import dev.bekagui.Click;
import dev.bekagui.Item;
import dev.bekagui.Menu;
import dev.bekagui.View;
import dev.bekagui.YamlMenus;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * /bekagui         opens the demo
 * /bekagui yaml X  opens plugins/BekaGUIDemo/menus/X.yml
 * /bekagui reload  reloads the YAML menus
 */
public final class DemoPlugin extends JavaPlugin {

    private static final Item FILLER = Item.of(Material.BLACK_STAINED_GLASS_PANE).name(" ");
    private static final Item BACK = Item.of(Material.ARROW).name("&7« Back");

    private YamlMenus yaml;
    private Menu main;
    private Menu sameSize;
    private Menu bigger;
    private Menu pages;
    private Menu counter;

    @Override
    public void onEnable() {
        BekaGUI.init(this);
        if (!new File(getDataFolder(), "menus/example.yml").exists()) {
            saveResource("menus/example.yml", false);
        }
        yaml = YamlMenus.load(this, new File(getDataFolder(), "menus"));
        buildMenus();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            yaml.reload();
            sender.sendMessage("§aBekaGUI menus reloaded: " + yaml.ids());
            return true;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        Player player = (Player) sender;
        if (args.length >= 2 && args[0].equalsIgnoreCase("yaml")) {
            if (!yaml.open(player, args[1])) {
                player.sendMessage("§cNo menu named " + args[1] + ". Loaded: " + yaml.ids());
            }
            return true;
        }
        main.open(player);
        return true;
    }

    private void buildMenus() {
        // 1) Same size as main -> items and title change inside the same window.
        sameSize = Menu.builder()
                .title("&2Same size: no reopen at all")
                .layout(
                        "#########",
                        "#.A.B.C.#",
                        "<########")
                .item('#', FILLER)
                .button('A', Item.of(Material.APPLE).name("&cApple"), click -> click.player().sendMessage("§cApple!"))
                .button('B', Item.of(Material.BREAD).name("&6Bread"), click -> click.player().sendMessage("§6Bread!"))
                .button('C', Item.of(Material.CAKE).name("&dCake"), click -> click.player().sendMessage("§dCake!"))
                .button('<', BACK, Click::back)
                .build();

        // 2) Different size -> window replaced, still no close packet, mouse stays.
        bigger = Menu.builder()
                .title("&56 rows: replaced without closing")
                .rows(6)
                .render(view -> {
                    for (int slot = 0; slot < 54; slot++) {
                        Material glass = (slot + slot / 9) % 2 == 0 ? Material.PURPLE_STAINED_GLASS_PANE : Material.MAGENTA_STAINED_GLASS_PANE;
                        view.set(slot, Button.display(Item.of(glass).name(" ")));
                    }
                    view.set(49, Button.of(BACK, Click::back));
                })
                .build();

        // 3) Pages: arrows only change items + title.
        final List<Button> blocks = new ArrayList<Button>();
        for (Material m : Material.values()) {
            if (blocks.size() >= 120) {
                break;
            }
            String id = m.name();
            boolean wanted = id.endsWith("_WOOL") || id.endsWith("_CONCRETE") || id.endsWith("_TERRACOTTA") || id.endsWith("_LOG");
            if (wanted && !id.startsWith("LEGACY_") && m.isItem()) {
                final String name = m.name().toLowerCase().replace('_', ' ');
                blocks.add(Button.of(Item.of(m).name("&f" + name), click -> click.player().sendMessage("§7You picked §f" + name)));
            }
        }
        pages = Menu.builder()
                .layout(
                        "xxxxxxxxx",
                        "xxxxxxxxx",
                        "xxxxxxxxx",
                        "xxxxxxxxx",
                        "xxxxxxxxx",
                        "<..p.n..B")
                .button('B', BACK, Click::back)
                .render(view -> {
                    view.paginate('x', blocks);
                    view.title("&8Pages &7" + (view.page() + 1) + "/" + view.pageCount());
                    view.set('p', view.hasPrevious()
                            ? Button.of(Item.of(Material.SPECTRAL_ARROW).name("&ePrevious page"), click -> click.view().previous())
                            : Button.display(FILLER));
                    view.set('n', view.hasNext()
                            ? Button.of(Item.of(Material.SPECTRAL_ARROW).name("&eNext page"), click -> click.view().next())
                            : Button.display(FILLER));
                    view.set('<', Button.display(FILLER));
                })
                .build();

        // 4) Live state + refresh: title and items change every second.
        counter = Menu.builder()
                .layout(
                        "#########",
                        "#-..N..+#",
                        "<###T####")
                .item('#', FILLER)
                .button('<', BACK, Click::back)
                .refresh(20)
                .render(view -> {
                    int n = view.get("n", 0);
                    view.title("&8Counter: &b" + n);
                    view.set('N', Button.display(Item.of(Material.EXPERIENCE_BOTTLE).name("&b" + n).amount(Math.max(1, Math.min(64, n)))));
                    view.set('-', Button.of(Item.of(Material.RED_CONCRETE).name("&c-1 &7(shift: -10)"), click -> add(click, -1)));
                    view.set('+', Button.of(Item.of(Material.LIME_CONCRETE).name("&a+1 &7(shift: +10)"), click -> add(click, 1)));
                    String now = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
                    view.set('T', Button.display(Item.of(Material.CLOCK).name("&e" + now).lore("&7Updated every second", "&7by refresh(20)")));
                })
                .build();

        main = Menu.builder()
                .title("&8BekaGUI demo")
                .layout(
                        "#########",
                        "#S.B.P.C#",
                        "####Y###X")
                .item('#', FILLER)
                .button('S', Item.of(Material.EMERALD).name("&aSame size menu").lore("&7Title and items change", "&7in the same window."),
                        click -> click.open(sameSize))
                .button('B', Item.of(Material.CHEST).name("&56-row menu").lore("&7Bigger window, opened over", "&7this one without closing."),
                        click -> click.open(bigger))
                .button('P', Item.of(Material.BOOK).name("&ePages").lore("&7120 items, arrows switch pages."),
                        click -> click.open(pages))
                .button('C', Item.of(Material.REPEATER).name("&bLive counter").lore("&7State, live title, refresh."),
                        click -> click.open(counter))
                .button('Y', Item.of(Material.WRITABLE_BOOK).name("&fYAML menu").lore("&7menus/example.yml"),
                        click -> {
                            Menu example = yaml.get("example");
                            if (example != null) {
                                click.open(example);
                            }
                        })
                .button('X', Item.of(Material.BARRIER).name("&cClose"), Click::close)
                .build();
    }

    private static void add(Click click, int step) {
        View view = click.view();
        int n = view.get("n", 0) + (click.isShift() ? step * 10 : step);
        view.put("n", Math.max(0, n));
        click.player().playSound(click.player().getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, n > 0 ? 1.2f : 0.8f);
        view.update();
    }
}
