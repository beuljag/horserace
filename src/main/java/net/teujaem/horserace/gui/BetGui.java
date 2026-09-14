package net.teujaem.horserace.gui;

import net.teujaem.horserace.HorseRacePlugin;
import net.teujaem.horserace.game.Horse;
import net.teujaem.horserace.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** 말 목록 + 배당 화면. 말을 클릭하면 {@link AmountGui} 로 넘어간다. */
public final class BetGui implements InventoryHolder {

    public static final int SLOT_INFO = 22;

    /** 최대 5마리: 슬롯 11~15 (가운데 정렬) */
    private static final int[][] SLOT_LAYOUTS = {
            {},
            {13},
            {12, 14},
            {11, 13, 15},
            {10, 12, 14, 16},
            {11, 12, 13, 14, 15},
    };

    private final HorseRacePlugin plugin;
    private final Player player;
    private final Inventory inventory;
    private final Map<Integer, Integer> slotToHorse = new HashMap<>();

    public BetGui(HorseRacePlugin plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.inventory = Bukkit.createInventory(this, 27, Text.of("&0경마 &7- 말 선택"));
        build();
    }

    private void build() {
        ItemStack pane = Items.pane();
        for (int i = 0; i < 27; i++) {
            inventory.setItem(i, pane);
        }
        String cur = Text.strip(plugin.getCurrency().display());
        List<Horse> horses = plugin.getHorses();
        int[] slots = SLOT_LAYOUTS[Math.min(horses.size(), 5)];
        for (int i = 0; i < slots.length; i++) {
            Horse h = horses.get(i);
            inventory.setItem(slots[i], Items.named(h.material(),
                    "&f" + (i + 1) + "번 " + h.coloredName(),
                    "&7배당: &e" + h.oddsText(),
                    "&7승률: &f약 " + plugin.winChancePercent(h) + "% &8(전체 말 중)",
                    "",
                    "&e클릭해서 이 말에 베팅"));
            slotToHorse.put(slots[i], i);
        }
        inventory.setItem(SLOT_INFO, Items.named(Material.PAPER, "&b내 잔액",
                "&f" + plugin.getCurrency().balance(player) + " " + cur));
    }

    /** 슬롯에 해당하는 말 번호. 없으면 -1. */
    public int horseAt(int slot) {
        return slotToHorse.getOrDefault(slot, -1);
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void open() {
        player.openInventory(inventory);
    }
}
