package net.teujaem.horserace.game;

import org.bukkit.Material;

/**
 * config 에 등록된 말 한 마리. 배당(odds)은 관리자가 정하는 고정값이고,
 * 승률은 배당에 반비례한다 (배당이 높을수록 잘 안 이긴다).
 */
public record Horse(String name, String color, Material material, double odds) {

    /** 색코드 포함 표시 이름. 예: "&e번개" */
    public String coloredName() {
        return color + name;
    }

    /** 배당 표시용. 예: "1.5배" */
    public String oddsText() {
        return String.format("%.1f배", odds);
    }

    public Horse withOdds(double newOdds) {
        return new Horse(name, color, material, newOdds);
    }
}
