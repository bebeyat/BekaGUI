package dev.bekagui;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

/**
 * A button click. Everything you can do from a button lives here.
 * <pre>
 * click -> {
 *     if (click.isRight()) click.view().put("amount", 64);
 *     click.open(confirmMenu);      // switch menu, window stays open
 * }
 * </pre>
 */
public final class Click {

    private final Player player;
    private final View view;
    private final int slot;
    private final ClickType type;

    Click(Player player, View view, int slot, ClickType type) {
        this.player = player;
        this.view = view;
        this.slot = slot;
        this.type = type;
    }

    public Player player() {
        return player;
    }

    public View view() {
        return view;
    }

    public int slot() {
        return slot;
    }

    public ClickType type() {
        return type;
    }

    public boolean isLeft() {
        return type.isLeftClick();
    }

    public boolean isRight() {
        return type.isRightClick();
    }

    public boolean isShift() {
        return type.isShiftClick();
    }

    /** Switches to another menu without closing the window. The current one is remembered for {@link #back()}. */
    public void open(Menu menu) {
        BekaGUI.open(player, menu);
    }

    /** Returns to the previous menu with its state (page, selections). Closes if there is none. */
    public void back() {
        if (!BekaGUI.back(player)) {
            close();
        }
    }

    public void close() {
        BekaGUI.close(player);
    }

    /** Re-renders this menu (see {@link View#update()}). */
    public void update() {
        view.update();
    }

    /** Changes the title of the open window. */
    public void title(String title) {
        view.title(title);
    }
}
