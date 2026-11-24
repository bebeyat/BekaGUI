package dev.bekagui;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Fluent item builder. Colors work everywhere ({@code &a}, {@code &#FF8800}).
 * <pre>Item.of(Material.DIAMOND).name("&bDiamond").lore("&7Line 1", "&7Line 2").glow()</pre>
 */
public final class Item {

    private final ItemStack stack;

    private Item(ItemStack stack) {
        this.stack = stack;
    }

    public static Item of(Material material) {
        return new Item(new ItemStack(material));
    }

    /** Starts from a copy of an existing item (the original is never changed). */
    public static Item of(ItemStack item) {
        return new Item(item.clone());
    }

    public Item name(String name) {
        return meta(m -> {
            m.setDisplayName(Text.color(name));
        });
    }

    public Item lore(String... lines) {
        return lore(Arrays.asList(lines));
    }

    public Item lore(List<String> lines) {
        final List<String> colored = Text.color(lines);
        return meta(m -> {
            m.setLore(colored);
        });
    }

    /** Adds lines under the existing lore. */
    public Item addLore(String... lines) {
        final List<String> extra = Text.color(Arrays.asList(lines));
        return meta(m -> {
            List<String> lore = m.hasLore() ? new ArrayList<String>(m.getLore()) : new ArrayList<String>();
            lore.addAll(extra);
            m.setLore(lore);
        });
    }

    public Item amount(int amount) {
        stack.setAmount(Math.max(1, Math.min(64, amount)));
        return this;
    }

    /** Enchantment shine without a visible enchantment. */
    public Item glow() {
        return meta(m -> {
            Enchantment e = Enchantment.getByKey(NamespacedKey.minecraft("unbreaking"));
            if (e != null) {
                m.addEnchant(e, 1, true);
            }
            m.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        });
    }

    /** Resource pack model id (1.14+). */
    public Item model(int customModelData) {
        return meta(m -> {
            m.setCustomModelData(customModelData);
        });
    }

    /** Hides attributes, enchantments and similar tooltip lines. */
    public Item hideFlags() {
        return meta(m -> {
            m.addItemFlags(ItemFlag.values());
        });
    }

    /** Any other change: {@code .meta(m -> m.setUnbreakable(true))}. */
    public Item meta(MetaEdit edit) {
        ItemMeta m = stack.getItemMeta();
        if (m != null) {
            edit.edit(m);
            stack.setItemMeta(m);
        }
        return this;
    }

    /** A fresh copy, safe to put in an inventory. */
    public ItemStack build() {
        return stack.clone();
    }

    /** Lambda friendly ItemMeta editor. */
    public interface MetaEdit {
        void edit(ItemMeta meta);
    }
}
