package net.teujaem.horserace.listener;

import net.teujaem.horserace.HorseRacePlugin;
import net.teujaem.horserace.gui.AmountGui;
import net.teujaem.horserace.gui.BetGui;
import net.teujaem.horserace.gui.RaceGui;
import net.teujaem.horserace.util.Text;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.InventoryHolder;

/** GUI 클릭 해석 + 접속 시 보류 지급. */
public final class GuiListener implements Listener {

    private final HorseRacePlugin plugin;

    public GuiListener(HorseRacePlugin plugin) {
        this.plugin = plugin;
    }

    private boolean ours(InventoryHolder holder) {
        return holder instanceof BetGui || holder instanceof AmountGui || holder instanceof RaceGui;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getInventory().getHolder();
        if (!ours(holder)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getClickedInventory() == null || event.getClickedInventory().getHolder() != holder) {
            return;
        }
        int slot = event.getSlot();

        if (holder instanceof BetGui bet) {
            int horse = bet.horseAt(slot);
            if (horse >= 0) {
                new AmountGui(plugin, player, horse).open();
            }
            return;
        }

        if (holder instanceof AmountGui amountGui) {
            if (slot == AmountGui.SLOT_BACK) {
                new BetGui(plugin, player).open();
                return;
            }
            int amount = amountGui.amountAt(slot);
            if (amount > 0) {
                plugin.getRaceManager().start(player, amountGui.horseIndex(), amount);
            }
            return;
        }

        RaceGui gui = (RaceGui) holder;
        if (plugin.getRaceManager().guiOf(player) != gui || !gui.race().finished()) {
            return; // 진행 중엔 누를 게 없다
        }
        switch (slot) {
            case RaceGui.SLOT_AGAIN -> plugin.getRaceManager().repeat(player);
            case RaceGui.SLOT_NEW -> {
                plugin.getRaceManager().quit(player);
                new BetGui(plugin, player).open();
            }
            case RaceGui.SLOT_QUIT -> {
                plugin.getRaceManager().quit(player);
                player.closeInventory();
            }
            default -> {
            }
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (ours(event.getInventory().getHolder())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof RaceGui
                && event.getPlayer() instanceof Player player) {
            plugin.getRaceManager().quit(player); // 끝난 경기만 정리됨
        }
    }

    /** 오프라인 중에 적중/환불된 금액을 접속 시 지급. */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Integer pending = plugin.getPendingPayouts().remove(player.getUniqueId());
        if (pending != null && pending > 0) {
            plugin.savePending();
            plugin.getCurrency().give(player, pending);
            plugin.message(player, "&a접속하지 않은 사이에 받은 금액: &f" + pending + " "
                    + Text.strip(plugin.getCurrency().display()));
        }
    }
}
