package net.teujaem.horserace.gui;

import net.teujaem.horserace.HorseRacePlugin;
import net.teujaem.horserace.game.Race;
import net.teujaem.horserace.game.Runner;
import net.teujaem.horserace.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * 1인용 경기장 화면. 6행 x 9열 상자에서 위 5행이 말 레인(0열 = 말 정보, 1~8열 = 트랙, 8열 = 결승선),
 * 맨 아래 행은 내 베팅 정보와 종료 후 버튼(같은 베팅 한 번 더 / 다른 말 고르기 / 나가기).
 */
public final class RaceGui implements InventoryHolder {

    private static final int COLS = 9;
    private static final int TRACK_START = 1;
    private static final int FINISH_COL = 8;
    private static final int BOTTOM = 45;

    public static final int SLOT_MY_BET = BOTTOM + 1;
    public static final int SLOT_AGAIN = BOTTOM + 3;
    public static final int SLOT_NEW = BOTTOM + 4;
    public static final int SLOT_QUIT = BOTTOM + 5;
    public static final int SLOT_RESULT = BOTTOM + 7;

    private final HorseRacePlugin plugin;
    private final Race race;
    private final Player player;
    private final Inventory inventory;

    public RaceGui(HorseRacePlugin plugin, Player player, Race race) {
        this.plugin = plugin;
        this.player = player;
        this.race = race;
        this.inventory = Bukkit.createInventory(this, 54, Text.of("&0경마 &7- 경기장"));
        render();
    }

    /** 현재 진행 상황을 아이템으로 다시 그린다. 매 틱 호출된다. */
    public void render() {
        ItemStack track = Items.named(Material.BROWN_STAINED_GLASS_PANE, "&8─");
        ItemStack finish = Items.named(Material.WHITE_STAINED_GLASS_PANE, "&f결승선");
        ItemStack empty = Items.pane();
        String cur = Text.strip(plugin.getCurrency().display());
        int myIndex = race.bet().runnerIndex();

        List<Runner> standings = race.standings();
        for (int row = 0; row < 5; row++) {
            Runner r = race.runner(row);
            if (r == null) {
                for (int c = 0; c < COLS; c++) {
                    inventory.setItem(row * COLS + c, empty);
                }
                continue;
            }
            for (int c = TRACK_START; c < FINISH_COL; c++) {
                inventory.setItem(row * COLS + c, track);
            }
            inventory.setItem(row * COLS + FINISH_COL, finish);

            boolean mine = r.index() == myIndex;
            int rank = standings.indexOf(r) + 1;
            List<String> lore = new ArrayList<>();
            lore.add("&7배당: &e" + r.horse().oddsText());
            lore.add("&7현재: &f" + rank + "위");
            if (mine) {
                lore.add("&a▶ 내가 베팅한 말");
            }
            inventory.setItem(row * COLS, Items.named(r.horse().material(),
                    (mine ? "&a★ " : "&f") + (r.index() + 1) + "번 " + r.horse().coloredName(),
                    lore.toArray(String[]::new)));

            int col;
            if (r.finished() && r.finishOrder() == 0) {
                col = FINISH_COL;
            } else {
                double ratio = Math.min(1.0, r.progress() / race.trackLength());
                col = TRACK_START + (int) Math.floor(ratio * (FINISH_COL - TRACK_START));
                col = Math.min(col, FINISH_COL - 1);
            }
            String name = r.horse().coloredName();
            if (race.finished()) {
                name += " &6" + (r.finishOrder() + 1) + "위" + (r.finishOrder() == 0 ? " ★" : "");
            }
            Material icon = race.finished() && r.finishOrder() == 0 ? Material.GOLD_BLOCK : Material.SADDLE;
            inventory.setItem(row * COLS + col, Items.named(icon, name,
                    "&7" + (int) Math.min(100, r.progress() * 100 / race.trackLength()) + "% 진행"));
        }

        // 하단 정보 행
        for (int c = 0; c < COLS; c++) {
            inventory.setItem(BOTTOM + c, empty);
        }
        Runner mine = race.betRunner();
        inventory.setItem(SLOT_MY_BET, Items.named(Material.BOOK, "&b내 베팅",
                "&f" + (mine.index() + 1) + "번 " + mine.horse().coloredName()
                        + " &7x " + race.bet().amount() + " " + cur,
                "&7배당 " + race.bet().oddsText() + " → 적중 시 &a" + race.bet().payout() + " " + cur));

        if (race.finished()) {
            renderResult(cur);
        } else {
            inventory.setItem(SLOT_RESULT, Items.named(Material.CLOCK, "&e경기 진행 중..."));
        }
    }

    private void renderResult(String cur) {
        Runner winner = race.winner();
        if (race.won()) {
            inventory.setItem(SLOT_RESULT, Items.named(Material.GOLD_BLOCK, "&a★ 적중!",
                    "&6우승: " + winner.horse().coloredName(),
                    "&a+" + race.bet().payout() + " " + cur + " &7수령"));
        } else {
            inventory.setItem(SLOT_RESULT, Items.named(Material.REDSTONE_BLOCK, "&c낙첨",
                    "&6우승: " + winner.horse().coloredName(),
                    "&c-" + race.bet().amount() + " " + cur));
        }
        int balance = plugin.getCurrency().balance(player);
        boolean afford = balance >= race.bet().amount();
        inventory.setItem(SLOT_AGAIN, Items.named(afford ? Material.LIME_WOOL : Material.BARRIER,
                "&a같은 베팅 한 번 더",
                "&f" + (race.betRunner().index() + 1) + "번 " + race.betRunner().horse().coloredName()
                        + " &7x " + race.bet().amount() + " " + cur,
                afford ? "&7클릭하면 바로 출발" : "&c재화가 부족합니다"));
        inventory.setItem(SLOT_NEW, Items.named(Material.SADDLE, "&e다른 말 고르기",
                "&7말 선택 화면으로"));
        inventory.setItem(SLOT_QUIT, Items.named(Material.RED_WOOL, "&c나가기",
                "&7잔액: &f" + balance + " " + cur));
    }

    public Race race() {
        return race;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public void open() {
        player.openInventory(inventory);
    }
}
