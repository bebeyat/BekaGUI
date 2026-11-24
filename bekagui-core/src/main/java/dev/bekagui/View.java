package dev.bekagui;

import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One open menu for one player: the live screen. Holds per-player state ({@link #put}/{@link #get}),
 * pagination and the current title. Every change is sent to the open window without closing it.
 */
public final class View {

    private static final String PAGE = "bekagui:page";

    private final Menu menu;
    private final Player player;
    private final Map<String, Object> state = new HashMap<String, Object>();
    private Object title;
    private Button[] buttons;
    private Button[] composing;
    private int pageCount = 1;
    Session session;

    View(Menu menu, Player player) {
        this.menu = menu;
        this.player = player;
        this.title = menu.titleValue();
        this.buttons = new Button[menu.size()];
    }

    public Player player() {
        return player;
    }

    public Menu menu() {
        return menu;
    }

    // ------------------------------------------------------------------ content

    /** Puts a button on a slot. Inside {@code render} this builds the screen; elsewhere it changes the open window immediately (until the next update). */
    public View set(int slot, Button button) {
        if (slot < 0 || slot >= menu.size()) {
            throw new IllegalArgumentException("Slot " + slot + " is outside the menu (size " + menu.size() + ")");
        }
        if (composing != null) {
            composing[slot] = button;
        } else {
            buttons[slot] = button;
            if (isLive()) {
                session.applySlot(slot, button);
            }
        }
        return this;
    }

    /** Puts the same button on every slot of a layout key. */
    public View set(char key, Button button) {
        for (int slot : slots(key)) {
            set(slot, button);
        }
        return this;
    }

    public View clear(int slot) {
        return set(slot, null);
    }

    /** Slots of a layout key, in reading order. */
    public int[] slots(char key) {
        Layout layout = menu.layout();
        if (layout == null) {
            throw new IllegalStateException("This menu has no layout; use slot numbers");
        }
        return layout.slots(key);
    }

    /** Re-runs {@code render} and sends only the slots that changed. No close, no flicker. */
    public void update() {
        if (isLive()) {
            session.refresh(this);
        }
    }

    // ------------------------------------------------------------------ title

    /** Current title as text ("" for component titles). */
    public String title() {
        return title instanceof String ? (String) title : "";
    }

    /** Changes the window title while it stays open. */
    public View title(String newTitle) {
        return setTitle(newTitle == null ? "" : newTitle);
    }

    /** Same with an Adventure Component (Paper). */
    public View titleComponent(Object component) {
        return setTitle(component == null ? "" : component);
    }

    private View setTitle(Object value) {
        this.title = value;
        if (isLive() && composing == null) {
            session.retitle(this);
        }
        return this;
    }

    Object titleValue() {
        return title;
    }

    // ------------------------------------------------------------------ state

    /** Per-view memory, e.g. selected item, amount. Survives {@link #update()} and {@link Click#back()}. */
    public View put(String key, Object value) {
        state.put(key, value);
        return this;
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) state.get(key);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key, T fallback) {
        Object v = state.get(key);
        return v == null ? fallback : (T) v;
    }

    // ------------------------------------------------------------------ pages

    /**
     * Fills the slots of {@code key} with the current page of {@code items}. Call it inside {@code render}.
     * Then use {@link #next()} / {@link #previous()} on your arrow buttons.
     */
    public void paginate(char key, List<Button> items) {
        int[] slots = slots(key);
        pageCount = Pages.count(items.size(), slots.length);
        int page = Pages.clamp(page(), items.size(), slots.length);
        state.put(PAGE, page);
        List<Button> visible = Pages.slice(items, page, slots.length);
        for (int i = 0; i < slots.length; i++) {
            set(slots[i], i < visible.size() ? visible.get(i) : null);
        }
    }

    /** Current page, 0-based. */
    public int page() {
        return get(PAGE, 0);
    }

    public int pageCount() {
        return pageCount;
    }

    public boolean hasNext() {
        return page() + 1 < pageCount;
    }

    public boolean hasPrevious() {
        return page() > 0;
    }

    /** Goes to the next page (if any) and updates. */
    public void next() {
        if (hasNext()) {
            state.put(PAGE, page() + 1);
            update();
        }
    }

    public void previous() {
        if (hasPrevious()) {
            state.put(PAGE, page() - 1);
            update();
        }
    }

    public void page(int page) {
        state.put(PAGE, Math.max(0, page));
        update();
    }

    // ------------------------------------------------------------------ internal

    boolean isLive() {
        return session != null && session.current() == this && session.menuShown();
    }

    Button[] compose() {
        Button[] next = new Button[menu.size()];
        Layout layout = menu.layout();
        if (layout != null) {
            for (Map.Entry<Character, Button> e : menu.keyed().entrySet()) {
                for (int slot : layout.slots(e.getKey())) {
                    next[slot] = e.getValue();
                }
            }
        }
        for (Map.Entry<Integer, Button> e : menu.slotted().entrySet()) {
            next[e.getKey()] = e.getValue();
        }
        if (menu.render() != null) {
            composing = next;
            try {
                menu.render().accept(this);
            } finally {
                composing = null;
            }
        }
        buttons = next;
        return next;
    }

    Button buttonAt(int slot) {
        return slot >= 0 && slot < buttons.length ? buttons[slot] : null;
    }
}
