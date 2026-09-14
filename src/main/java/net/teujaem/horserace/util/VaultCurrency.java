package net.teujaem.horserace.util;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.entity.Player;

/**
 * Vault 이코노미 기반 재화. 서버에 Vault 와 이코노미 플러그인이 있을 때 쓴다.
 * 판돈은 정수로 다루므로 잔액도 내림해서 정수로 취급한다.
 */
public final class VaultCurrency implements Currency {

    private final Economy economy;
    private final String display;

    public VaultCurrency(Economy economy, String display) {
        this.economy = economy;
        this.display = display;
    }

    @Override
    public String display() {
        return display;
    }

    @Override
    public int balance(Player player) {
        return (int) Math.floor(economy.getBalance(player));
    }

    @Override
    public boolean take(Player player, int amount) {
        if (amount <= 0) {
            return true;
        }
        if (economy.getBalance(player) < amount) {
            return false;
        }
        return economy.withdrawPlayer(player, amount).transactionSuccess();
    }

    @Override
    public void give(Player player, int amount) {
        if (amount <= 0) {
            return;
        }
        economy.depositPlayer(player, amount);
    }
}
