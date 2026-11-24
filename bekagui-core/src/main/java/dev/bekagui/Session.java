package dev.bekagui;

import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * The window of one player. The core rule of BekaGUI lives here: the window is never closed to switch menus.
 * <ul>
 *   <li>Same size: items are diffed and changed in place, the title is changed in place.</li>
 *   <li>Other size (or a {@link TextInput}): the new window is opened directly over the old one. The server sends no
 *       close packet, so the client goes screen to screen: no world flash, the mouse stays where it is.</li>
 * </ul>
 */
final class Session {

    private final MenuManager manager;
    private final Player player;
    private final Deque<View> history = new ArrayDeque<View>();
    private View current;
    private Inventory inventory;
    private Object shownTitle;
    private boolean switching;
    private Runnable stopRefresh;
    private TextInput input;
    private String inputText = "";
    long lastClick;

    Session(MenuManager manager, Player player) {
        this.manager = manager;
        this.player = player;
    }

    View current() {
        return current;
    }

    Inventory inventory() {
        return inventory;
    }

    boolean switching() {
        return switching;
    }

    TextInput input() {
        return input;
    }

    String inputText() {
        return inputText;
    }

    void inputText(String text) {
        this.inputText = text == null ? "" : text;
    }

    /** True while one of our windows (menu or text input) is on the player's screen. */
    boolean isOpen() {
        Inventory top = inventory == null ? null : Platform.topInventory(player);
        return top != null && (top == inventory || top.equals(inventory));
    }

    /** True while a menu (not a text input) is on screen. */
    boolean menuShown() {
        return input == null && isOpen();
    }

    void show(View view, boolean remember) {
        boolean open = isOpen();
        if (open && remember && current != null && current != view) {
            history.push(current);
            if (history.size() > 32) {
                history.removeLast();
            }
        }
        if (!open) {
            history.clear();
        }
        boolean wasInput = input != null;
        if (wasInput) {
            inventory.clear();                       // otherwise the anvil hands its left item to the player
        }
        input = null;
        current = view;
        view.session = this;
        Button[] buttons = view.compose();
        Object title = Platform.title(view.titleValue());
        int size = view.menu().size();

        if (open && !wasInput && inventory.getSize() == size) {
            applyAll(buttons);
            if (!title.equals(shownTitle)) {
                changeTitle(title);
            }
        } else {
            replaceWindow(size, title, buttons);
        }
        restartRefresh(view.menu().refreshTicks());
        if (view.menu().onOpen() != null) {
            view.menu().onOpen().accept(view);
        }
    }

    /** Shows an anvil text input over the current window (no close). The menu stays in history for back(). */
    void showInput(TextInput next) {
        boolean open = isOpen();
        if (open && current != null && input == null) {
            history.push(current);
        }
        if (!open) {
            history.clear();
        }
        stopRefresh();
        if (input != null) {
            inventory.clear();
        }
        switching = true;
        try {
            Inventory anvil = Platform.openAnvil(player, Platform.title(next.titleValue()));
            if (anvil == null) {
                throw new IllegalStateException("This server cannot open an anvil text input (needs Paper, or Spigot 1.21+)");
            }
            inventory = anvil;
            input = next;
            inputText = next.text();
            shownTitle = null;
            anvil.setItem(0, next.leftIcon());
            anvil.setItem(2, next.resultIcon());
        } finally {
            switching = false;
        }
    }

    boolean back() {
        View previous = history.poll();
        if (previous == null || !isOpen()) {
            return false;
        }
        show(previous, false);
        return true;
    }

    void refresh(View view) {
        if (view != current || !menuShown()) {
            return;
        }
        applyAll(view.compose());
        Object title = Platform.title(view.titleValue());
        if (!title.equals(shownTitle)) {
            changeTitle(title);
        }
    }

    void retitle(View view) {
        if (view == current && menuShown()) {
            Object title = Platform.title(view.titleValue());
            if (!title.equals(shownTitle)) {
                changeTitle(title);
            }
        }
    }

    void applySlot(int slot, Button button) {
        ItemStack want = button == null ? null : button.icon();
        if (!same(inventory.getItem(slot), want)) {
            inventory.setItem(slot, want == null ? null : want.clone());
        }
    }

    void end() {
        if (input != null && inventory != null) {
            inventory.clear();
        }
        stopRefresh();
        history.clear();
        input = null;
    }

    // ------------------------------------------------------------------ window work

    private void applyAll(Button[] buttons) {
        for (int slot = 0; slot < buttons.length; slot++) {
            applySlot(slot, buttons[slot]);
        }
    }

    private void changeTitle(Object title) {
        shownTitle = title;
        if (title instanceof String && Platform.setTitle(player, (String) title)) {
            return;                                  // 1.20+: same window, new title
        }
        ItemStack[] contents = inventory.getContents();  // components / older servers: same contents, new window, no close packet
        Inventory next = create(inventory.getSize(), title);
        next.setContents(contents);
        openOver(next);
    }

    private void replaceWindow(int size, Object title, Button[] buttons) {
        Inventory next = create(size, title);
        for (int slot = 0; slot < buttons.length; slot++) {
            if (buttons[slot] != null) {
                next.setItem(slot, buttons[slot].icon().clone());
            }
        }
        shownTitle = title;
        openOver(next);
    }

    private void openOver(Inventory next) {
        // Player#openInventory closes the old container on the server only; the client receives just the new screen.
        switching = true;
        try {
            inventory = next;
            player.openInventory(next);
        } finally {
            switching = false;
        }
    }

    private Inventory create(int size, Object title) {
        Holder holder = new Holder(this);
        Inventory inv = Platform.createInventory(holder, size, title);
        holder.inventory = inv;
        return inv;
    }

    private void restartRefresh(long ticks) {
        stopRefresh();
        if (ticks > 0) {
            final View view = current;
            stopRefresh = Platform.repeat(manager.plugin(), player, ticks, () -> {
                if (current == view && menuShown()) {
                    refresh(view);
                }
            });
        }
    }

    private void stopRefresh() {
        if (stopRefresh != null) {
            stopRefresh.run();
            stopRefresh = null;
        }
    }

    private static boolean same(ItemStack a, ItemStack b) {
        if (a == null || a.getType().isAir()) {
            return b == null || b.getType().isAir();
        }
        return a.equals(b);
    }

    /** Marks our inventories. {@code getHolder() instanceof Holder} = a BekaGUI window. */
    static final class Holder implements InventoryHolder {
        final Session session;
        Inventory inventory;

        Holder(Session session) {
            this.session = session;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
