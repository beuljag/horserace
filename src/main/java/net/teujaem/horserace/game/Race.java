package net.teujaem.horserace.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 플레이어 한 명의 경기 한 판. 순수 로직만 담고 Bukkit 에 의존하지 않는다.
 *
 * <p><b>승패 결정</b>: 말마다 가중치 = (1 / 배당)^oddsPower 를 갖고, 매 경기 그 비율대로 우승마가 뽑힌다.
 * 베팅과 무관하게 항상 같은 확률이며, 배당이 높을수록 거듭제곱으로 가파르게 떨어진다.
 * 표시되는 승률이 곧 실제 판정 확률이다. 어떤 말에 걸었는지는 결과에 전혀 영향을 주지 않는다.
 *
 * <p><b>연출</b>: 우승마는 미리 정해져 있고, {@link #tick()} 은 랜덤 변동이 있는 레이스를
 * 그리되 다른 말이 우승마보다 먼저 결승선을 넘지 못하게 선 앞에서 붙잡는다.
 */
public final class Race {

    private static final Random RANDOM = new Random();

    private final List<Runner> runners;
    private final Bet bet;
    private final int winnerIndex;
    private final double trackLength;
    private boolean finished = false;

    public Race(List<Horse> field, Bet bet, double trackLength, double oddsPower) {
        this.bet = bet;
        this.trackLength = trackLength;
        this.winnerIndex = decideWinner(field, oddsPower);

        this.runners = new ArrayList<>();
        for (int i = 0; i < field.size(); i++) {
            double speed = 0.9 + RANDOM.nextDouble() * 0.2;   // 0.9 ~ 1.1
            if (i == winnerIndex) {
                speed *= 1.1;                                  // 우승마는 살짝 빠르게 (연출)
            }
            runners.add(new Runner(i, field.get(i), speed));
        }
    }

    /** 말 하나의 우승 가중치 = (1 / 배당) ^ oddsPower. 배당이 높을수록 가파르게 작아진다. */
    private static double weight(Horse h, double oddsPower) {
        return Math.pow(1.0 / h.odds(), oddsPower);
    }

    /**
     * 표시용 승률 (0~1) = 이 말의 가중치 / 전체 가중치 합.
     * 베팅과 무관하게 매 경기 이 확률대로 우승마가 정해진다. 전부 더하면 1.
     */
    public static double winChance(List<Horse> field, int index, double oddsPower) {
        double total = 0;
        for (Horse h : field) {
            total += weight(h, oddsPower);
        }
        return total <= 0 ? 0 : weight(field.get(index), oddsPower) / total;
    }

    private static int decideWinner(List<Horse> field, double oddsPower) {
        // 모든 말이 자기 가중치대로 경쟁. 베팅 여부는 무관.
        double[] w = new double[field.size()];
        double total = 0;
        for (int i = 0; i < field.size(); i++) {
            w[i] = weight(field.get(i), oddsPower);
            total += w[i];
        }
        double roll = RANDOM.nextDouble() * total;
        for (int i = 0; i < field.size(); i++) {
            roll -= w[i];
            if (roll <= 0) {
                return i;
            }
        }
        // 부동소수 오차 대비
        return field.size() - 1;
    }

    public List<Runner> runners() {
        return Collections.unmodifiableList(runners);
    }

    public Runner runner(int index) {
        return index >= 0 && index < runners.size() ? runners.get(index) : null;
    }

    public Bet bet() {
        return bet;
    }

    public Runner winner() {
        return runners.get(winnerIndex);
    }

    public Runner betRunner() {
        return runners.get(bet.runnerIndex());
    }

    public boolean won() {
        return winnerIndex == bet.runnerIndex();
    }

    public boolean finished() {
        return finished;
    }

    public double trackLength() {
        return trackLength;
    }

    /**
     * 한 틱 진행. 우승마가 결승선을 넘는 순간 나머지 순위를 위치 기준으로 확정하고 종료한다.
     *
     * @return 경기가 끝났으면 true
     */
    public boolean tick() {
        if (finished) {
            return true;
        }
        Runner winner = winner();
        for (Runner r : runners) {
            // 한 틱에 트랙의 약 3.5% 전진 (틱 간격 8 기준 경기 약 12~16초)
            double base = trackLength * 0.035 * r.speed();
            double luck = 0.6 + RANDOM.nextDouble() * 0.8;               // 0.6 ~ 1.4
            double burst = RANDOM.nextInt(10) == 0 ? trackLength * 0.03 : 0; // 가끔 스퍼트
            r.advance(base * luck + burst);
            if (r != winner && r.progress() >= trackLength) {
                // 우승마보다 먼저 못 넘는다. 결승선 바로 앞에서 붙잡기 (접전 연출)
                r.setProgress(trackLength - 0.01);
            }
        }
        if (winner.progress() >= trackLength) {
            winner.setProgress(trackLength);
            winner.setFinishOrder(0);
            List<Runner> rest = new ArrayList<>(runners);
            rest.remove(winner);
            rest.sort((a, b) -> Double.compare(b.progress(), a.progress()));
            for (int i = 0; i < rest.size(); i++) {
                rest.get(i).setFinishOrder(i + 1);
            }
            finished = true;
        }
        return finished;
    }

    /** 현재 순위 (진행 중이면 위치 기준, 끝났으면 통과 순서 기준). */
    public List<Runner> standings() {
        List<Runner> sorted = new ArrayList<>(runners);
        sorted.sort((a, b) -> {
            if (a.finished() && b.finished()) {
                return Integer.compare(a.finishOrder(), b.finishOrder());
            }
            if (a.finished() != b.finished()) {
                return a.finished() ? -1 : 1;
            }
            return Double.compare(b.progress(), a.progress());
        });
        return sorted;
    }
}
