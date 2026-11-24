package dev.bekagui;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Asks the player for a line of text in an anvil window. Opened over the current menu without closing it;
 * the menu before it stays in the back history.
 * <pre>
 * TextInput.create()
 *     .title("&8Search")
 *     .onSubmit((player, text) -> resultsMenu(text).open(player))   // clicking the result slot
 *     .onCancel(player -> player.sendMessage("Cancelled"))          // ESC
 *     .open(player);
 * </pre>
 * The window stays open after a submit until you open something else or close it, so you can simply
 * ignore invalid input (or tell the player) and let them type again.
 * Needs Paper (any supported version) or Spigot 1.21+.
 */
public final class TextInput {

    private Object title = "";
    private String text = "";
    private Item left = Item.of(Material.PAPER);
    private Item result = Item.of(Material.LIME_DYE).name("&a✔");
    private BiConsumer<Player, String> onSubmit = (player, value) -> { };
    private Consumer<Player> onCancel = player -> { };

    private TextInput() {
    }

    public static TextInput create() {
        return new TextInput();
    }

    /** Window title (colors allowed). */
    public TextInput title(String title) {
        this.title = title == null ? "" : title;
        return this;
    }

    /** Window title as an Adventure Component (Paper), e.g. a custom-font texture title. */
    public TextInput titleComponent(Object component) {
        this.title = component;
        return this;
    }

    /** Text already in the field when it opens. Default: empty. */
    public TextInput text(String text) {
        this.text = text == null ? "" : text;
        return this;
    }

    /** Item in the left slot (its tooltip can hold hints). Its name is replaced by {@link #text(String)}. */
    public TextInput icon(Item icon) {
        this.left = icon;
        return this;
    }

    /** Item in the result slot; clicking it submits. */
    public TextInput result(Item icon) {
        this.result = icon;
        return this;
    }

    public TextInput onSubmit(BiConsumer<Player, String> handler) {
        this.onSubmit = handler;
        return this;
    }

    /** ESC or any other real close. */
    public TextInput onCancel(Consumer<Player> handler) {
        this.onCancel = handler;
        return this;
    }

    /** Opens it. Safe from any thread. */
    public void open(Player player) {
        BekaGUI.manager().openInput(player, this);
    }

    Object titleValue() {
        return title;
    }

    String text() {
        return text;
    }

    ItemStack leftIcon() {
        ItemStack stack = left.build();
        Platform.setPlainName(stack, text);       // the field starts with exactly this text
        return stack;
    }

    ItemStack resultIcon() {
        return result.build();
    }

    BiConsumer<Player, String> submitHandler() {
        return onSubmit;
    }

    Consumer<Player> cancelHandler() {
        return onCancel;
    }
}
