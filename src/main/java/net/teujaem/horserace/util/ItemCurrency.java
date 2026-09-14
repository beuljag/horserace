package net.teujaem.horserace.util;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * 아이템 기반 재화. 이코노미 플러그인 없이 인벤토리의 특정 아이템 개수를
 * 그대로 화폐처럼 쓴다. (기본: 금괴)
 */
public final class ItemCurrency implements Currency {

    private final Material material;
    private final String display;

    public ItemCurrency(Material material, String display) {
        this.material = material;
        this.display = display;
    }

    public Material material() {
        return material;
    }

    @Override
    public String display() {
        return display;
    }

    @Override
    public int balance(Player player) {
        int total = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.getType() == material) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    @Override
    public boolean take(Player player, int amount) {
        if (amount <= 0) {
            return true;
        }
        if (balance(player) < amount) {
            return false;
        }
        player.getInventory().removeItem(new ItemStack(material, amount));
        return true;
    }

    @Override
    public void give(Player player, int amount) {
        if (amount <= 0) {
            return;
        }
        // 스택 한도(64) 를 넘겨도 addItem 이 알아서 쪼갠다. 넘치면 발밑에 드롭.
        var overflow = player.getInventory().addItem(new ItemStack(material, amount));
        overflow.values().forEach(left ->
                player.getWorld().dropItemNaturally(player.getLocation(), left));
    }
}
