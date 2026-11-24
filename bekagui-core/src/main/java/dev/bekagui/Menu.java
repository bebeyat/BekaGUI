package dev.bekagui;

import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * A menu definition (a template). Build it once, open it for any number of players.
 * Each opening gets its own {@link View} with its own state.
 *
 * <pre>
 * Menu shop = Menu.builder()
 *     .title("&8Shop")
 *     .layout(
 *         "#########",
 *         "#..D.E..#",
 *         "####X####")
 *     .item('#', Item.of(Material.GRAY_STAINED_GLASS_PANE).name(" "))
 *     .button('D', Item.of(Material.DIAMOND).name("&bDiamond"), click -> click.player().sendMessage("Diamond!"))
 *     .button('E', Item.of(Material.EMERALD).name("&aEmerald"), click -> click.open(otherMenu))
 *     .button('X', Item.of(Material.BARRIER).name("&cClose"), Click::close)
 *     .build();
 *
 * shop.open(player);
 * </pre>
 *
 * Switching to another menu with {@link Click#open} never closes the window: same size menus are
 * updated in place (items + title), other sizes replace the window without the close/open flash
 * and without the mouse jumping to the screen center.
 */
public final class Menu {

    private final Object title;
    private final int rows;
    private final Layout layout;
    private final Map<Character, Button> keyed;
    private final Map<Integer, Button> slotted;
    private final Consumer<View> render;
    private final Consumer<View> onOpen;
    private final Consumer<View> onClose;
    private final long refreshTicks;
    private final boolean lockPlayerInventory;

    private Menu(Builder b) {
        this.title = b.title;
        this.layout = b.layout;
        this.rows = b.layout != null ? b.layout.rows() : b.rows;
        this.keyed = Collections.unmodifiableMap(new HashMap<Character, Button>(b.keyed));
        this.slotted = Collections.unmodifiableMap(new HashMap<Integer, Button>(b.slotted));
        this.render = b.render;
        this.onOpen = b.onOpen;
        this.onClose = b.onClose;
        this.refreshTicks = b.refreshTicks;
        this.lockPlayerInventory = b.lockPlayerInventory;
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Opens this menu. If the player already looks at a BekaGUI menu, it is replaced without closing. */
    public void open(Player player) {
        BekaGUI.open(player, this);
    }

    /** The title as text, or "" if it is a component title. */
    public String title() {
        return title instanceof String ? (String) title : "";
    }

    Object titleValue() {
        return title;
    }

    public int rows() {
        return rows;
    }

    public int size() {
        return rows * 9;
    }

    Layout layout() {
        return layout;
    }

    Map<Character, Button> keyed() {
        return keyed;
    }

    Map<Integer, Button> slotted() {
        return slotted;
    }

    Consumer<View> render() {
        return render;
    }

    Consumer<View> onOpen() {
        return onOpen;
    }

    Consumer<View> onClose() {
        return onClose;
    }

    long refreshTicks() {
        return refreshTicks;
    }

    boolean lockPlayerInventory() {
        return lockPlayerInventory;
    }

    public static final class Builder {
        private Object title = "";
        private int rows = 3;
        private Layout layout;
        private final Map<Character, Button> keyed = new HashMap<Character, Button>();
        private final Map<Integer, Button> slotted = new HashMap<Integer, Button>();
        private Consumer<View> render;
        private Consumer<View> onOpen;
        private Consumer<View> onClose;
        private long refreshTicks;
        private boolean lockPlayerInventory;

        private Builder() {
        }

        /** Window title. Colors allowed. Change it later with {@link View#title(String)}. */
        public Builder title(String title) {
            this.title = title == null ? "" : title;
            return this;
        }

        /** Title as an Adventure Component (Paper), e.g. custom-font texture titles. On Spigot use {@link #title(String)}. */
        public Builder titleComponent(Object component) {
            this.title = component == null ? "" : component;
            return this;
        }

        /** 1 to 6. Not needed when you use {@link #layout}. */
        public Builder rows(int rows) {
            if (rows < 1 || rows > 6) {
                throw new IllegalArgumentException("rows must be 1-6, got " + rows);
            }
            this.rows = rows;
            return this;
        }

        /** Draw the menu with characters, 9 per row. {@code '.'} = empty. Sets the row count too. */
        public Builder layout(String... rows) {
            this.layout = Layout.parse(rows);
            return this;
        }

        /** Puts a clickable button on every slot of {@code key} in the layout. */
        public Builder button(char key, Item icon, ClickHandler onClick) {
            return button(key, Button.of(icon, onClick));
        }

        public Builder button(char key, Button button) {
            keyed.put(key, button);
            return this;
        }

        /** Decoration on every slot of {@code key} (not clickable). */
        public Builder item(char key, Item icon) {
            return button(key, Button.display(icon));
        }

        /** A button on a raw slot number (0 = top left). Works with or without a layout. */
        public Builder slot(int slot, Button button) {
            slotted.put(slot, button);
            return this;
        }

        public Builder slot(int slot, Item icon, ClickHandler onClick) {
            return slot(slot, Button.of(icon, onClick));
        }

        /**
         * Dynamic content, called on open and on every {@link View#update()}.
         * Use it for anything that depends on the player or changes over time.
         * <pre>.render(view -> view.set(13, Button.display(Item.of(Material.GOLD_INGOT).name("&6Coins: " + coins(view.player())))))</pre>
         */
        public Builder render(Consumer<View> render) {
            this.render = render;
            return this;
        }

        /** Called after the menu is shown (also when coming back with {@link Click#back()}). */
        public Builder onOpen(Consumer<View> onOpen) {
            this.onOpen = onOpen;
            return this;
        }

        /** Called when the player really closes the window (ESC, another plugin's inventory, quit). Not called when switching menus. */
        public Builder onClose(Consumer<View> onClose) {
            this.onClose = onClose;
            return this;
        }

        /** Re-render every {@code ticks} ticks (20 = 1 second) while open. 0 = off. */
        public Builder refresh(long ticks) {
            this.refreshTicks = Math.max(0, ticks);
            return this;
        }

        /** Also block clicks in the player's own inventory while this menu is open. Default: only shift/collect are blocked. */
        public Builder lockPlayerInventory(boolean lock) {
            this.lockPlayerInventory = lock;
            return this;
        }

        public Menu build() {
            if (layout != null) {
                for (Character key : keyed.keySet()) {
                    if (layout.slots(key).length == 0) {
                        throw new IllegalStateException("Key '" + key + "' is bound but does not appear in the layout");
                    }
                }
            } else if (!keyed.isEmpty()) {
                throw new IllegalStateException("button(char, ...) needs a layout(...). Use slot(int, ...) without a layout.");
            }
            for (Integer slot : slotted.keySet()) {
                int size = (layout != null ? layout.rows() : rows) * 9;
                if (slot < 0 || slot >= size) {
                    throw new IllegalStateException("Slot " + slot + " is outside the menu (size " + size + ")");
                }
            }
            return new Menu(this);
        }
    }
}
