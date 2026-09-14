package net.teujaem.horserace.game;

/** 플레이어가 말 한 마리에 건 베팅. */
public record Bet(int runnerIndex, int amount, double odds) {

    /** 우승 시 돌려받는 총액(원금 포함). */
    public int payout() {
        return (int) Math.floor(amount * odds);
    }

    public String oddsText() {
        return String.format("%.1f배", odds);
    }
}
