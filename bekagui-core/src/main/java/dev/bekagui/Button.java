package dev.bekagui;

import org.bukkit.inventory.ItemStack;

/**
 * An item in a menu, optionally clickable. Buttons are immutable and can be shared between menus and players.
 * <pre>
 * Button.of(Item.of(Material.EMERALD).name("&aBuy"), click -> click.player().sendMessage("Bought!"))
 * Button.display(Item.of(Material.GRAY_STAINED_GLASS_PANE).name(" "))   // decoration, not clickable
 * </pre>
 */
public final class Button {

    private final ItemStack icon;
    private final ClickHandler handler;

    private Button(ItemStack icon, ClickHandler handler) {
        this.icon = icon;
        this.handler = handler;
    }

    public static Button of(Item icon, ClickHandler onClick) {
        return new Button(icon.build(), onClick);
    }

    public static Button of(ItemStack icon, ClickHandler onClick) {
        return new Button(icon.clone(), onClick);
    }

    /** Not clickable (filler, info). */
    public static Button display(Item icon) {
        return new Button(icon.build(), null);
    }

    public static Button display(ItemStack icon) {
        return new Button(icon.clone(), null);
    }

    /** Same click action, new look. Handy inside {@code render}. */
    public Button withIcon(Item icon) {
        return new Button(icon.build(), handler);
    }

    ItemStack icon() {
        return icon;
    }

    ClickHandler handler() {
        return handler;
    }
}
