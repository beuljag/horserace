package net.teujaem.horserace.util;

import org.bukkit.entity.Player;

/**
 * 베팅에 쓰는 재화 추상화. 아이템 기반({@link ItemCurrency})과
 * Vault 이코노미 기반({@link VaultCurrency}) 두 구현을 스위칭해서 쓴다.
 */
public interface Currency {

    /** GUI/메시지에 표시할 단위 이름(색코드 포함). 예: "&e금괴", "&a원" */
    String display();

    /** 플레이어가 가진 재화 총량. */
    int balance(Player player);

    /** amount 만큼 회수. 부족하면 false 를 돌려주고 아무것도 건드리지 않는다. */
    boolean take(Player player, int amount);

    /** amount 만큼 지급. */
    void give(Player player, int amount);
}
