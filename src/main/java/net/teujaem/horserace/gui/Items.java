package net.teujaem.horserace.gui;

import net.kyori.adventure.text.Component;
import net.teujaem.horserace.util.Text;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/** GUI 아이템 생성 도우미. */
final class Items {

    private Items() {
    }

    static ItemStack named(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.displayName(Text.of(name));
            if (lore.length > 0) {
                List<Component> lines = new ArrayList<>();
                for (String line : lore) {
                    lines.add(Text.of(line));
                }
                meta.lore(lines);
            }
            stack.setItemMeta(meta);
        }
        return stack;
    }

    static ItemStack pane() {
        return named(Material.BLACK_STAINED_GLASS_PANE, " ");
    }
}
