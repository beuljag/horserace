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

/** 특정 말에 걸 금액을 고르는 화면. 금액을 누르면 바로 경기가 시작된다. */
public final class AmountGui implements InventoryHolder {

    public static final int SLOT_BACK = 18;
    public static final int SLOT_HORSE = 4;

    private static final int[] AMOUNT_SLOTS = {10, 11, 12, 13, 14, 15, 16};

    private final HorseRacePlugin plugin;
    private final Player player;
    private final int horseIndex;
    private final Inventory inventory;
    private final Map<Integer, Integer> slotToAmount = new HashMap<>();

    public AmountGui(HorseRacePlugin plugin, Player player, int horseIndex) {
        this.plugin = plugin;
        this.player = player;
        this.horseIndex = horseIndex;
        Horse h = plugin.getHorses().get(horseIndex);
        this.inventory = Bukkit.createInventory(this, 27,
                Text.of("&0경마 &7- " + Text.strip(h.coloredName()) + " 에 베팅"));
        build(h);
    }

    private void build(Horse h) {
        ItemStack pane = Items.pane();
        for (int i = 0; i < 27; i++) {
            inventory.setItem(i, pane);
        }
        String cur = Text.strip(plugin.getCurrency().display());
        int balance = plugin.getCurrency().balance(player);

        inventory.setItem(SLOT_HORSE, Items.named(h.material(),
                "&f" + (horseIndex + 1) + "번 " + h.coloredName(),
                "&7배당: &e" + h.oddsText(),
                "&7잔액: &f" + balance + " " + cur));

        List<Integer> amounts = plugin.getBetAmounts();
        for (int i = 0; i < amounts.size() && i < AMOUNT_SLOTS.length; i++) {
            int amount = amounts.get(i);
            boolean afford = balance >= amount;
            int payout = (int) Math.floor(amount * h.odds());
            inventory.setItem(AMOUNT_SLOTS[i], Items.named(afford ? Material.GOLD_INGOT : Material.BARRIER,
                    (afford ? "&e" : "&c") + amount + " " + cur,
                    afford ? "&7우승 시 &a" + payout + " " + cur + " &7수령" : "&c재화가 부족합니다",
                    afford ? "&e클릭하면 바로 출발!" : ""));
            slotToAmount.put(AMOUNT_SLOTS[i], amount);
        }

        inventory.setItem(SLOT_BACK, Items.named(Material.ARROW, "&7← 말 목록으로"));
    }

    public int amountAt(int slot) {
        return slotToAmount.getOrDefault(slot, -1);
    }

    public int horseIndex() {
        return horseIndex;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void open() {
        player.openInventory(inventory);
    }
}
