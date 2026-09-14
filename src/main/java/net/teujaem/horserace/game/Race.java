package net.teujaem.horserace.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * 플레이어 한 명의 경기 한 판. 순수 로직만 담고 Bukkit 에 의존하지 않는다.
 *
 * <p><b>진행 방식</b>: 우승마를 미리 정하지 않는다. 매 틱마다 말 하나하나가
 * "이번 틱에 움직일지" 를 굴린다. 이동 확률 = (1 / 배당) ^ movePower 이므로
 * 배당이 낮은 말은 거의 매 틱 움직이고, 배당이 높은 말은 자주 멈춘다.
 * 움직이면 트랙의 moveStep 비율(±30% 흔들림)만큼 전진하고, 먼저 결승선을 넘는 말이 우승이다.
 * 같은 틱에 여럿이 넘으면 더 멀리 간 쪽이 이긴다.
 *
 * <p>승률은 이 규칙에서 저절로 나오는 값이라 공식이 없다. {@link #estimateWinChances} 로
 * 여러 판을 돌려 추정한다 (표시용).
 */
public final class Race {

    private static final Random RANDOM = new Random();

    private final List<Runner> runners;
    private final Bet bet;
    private final double trackLength;
    private final double movePower;
    private final double moveStep;      // 한 번 이동 거리 (트랙 길이 기준 비율, 예 0.08)
    private int winnerIndex = -1;
    private boolean finished = false;

    public Race(List<Horse> field, Bet bet, double trackLength, double movePower, double moveStep) {
        this.bet = bet;
        this.trackLength = trackLength;
        this.movePower = movePower;
        this.moveStep = moveStep;
        this.runners = new ArrayList<>();
        for (int i = 0; i < field.size(); i++) {
            runners.add(new Runner(i, field.get(i)));
        }
    }

    /** 이 말이 한 틱에 움직일 확률 = (1 / 배당) ^ movePower. */
    public static double moveChance(Horse h, double movePower) {
        return Math.max(0.0, Math.min(1.0, Math.pow(1.0 / h.odds(), movePower)));
    }

    /**
     * 표시용 승률 추정. samples 판을 실제 규칙으로 돌려 말별 우승 비율을 돌려준다.
     * 순수 계산이라 빠르다 (5마리 × 수천 판이면 수십 ms).
     */
    public static double[] estimateWinChances(List<Horse> field, double trackLength,
                                              double movePower, double moveStep, int samples) {
        int[] wins = new int[field.size()];
        Bet dummy = new Bet(0, 1, 1.0);
        for (int s = 0; s < samples; s++) {
            Race race = new Race(field, dummy, trackLength, movePower, moveStep);
            while (!race.tick()) {
                // 끝날 때까지
            }
            wins[race.winnerIndex]++;
        }
        double[] chances = new double[field.size()];
        for (int i = 0; i < field.size(); i++) {
            chances[i] = samples <= 0 ? 0 : (double) wins[i] / samples;
        }
        return chances;
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

    /** 우승마. 아직 안 끝났으면 null. */
    public Runner winner() {
        return winnerIndex < 0 ? null : runners.get(winnerIndex);
    }

    public Runner betRunner() {
        return runners.get(bet.runnerIndex());
    }

    public boolean won() {
        return finished && winnerIndex == bet.runnerIndex();
    }

    public boolean finished() {
        return finished;
    }

    public double trackLength() {
        return trackLength;
    }

    /**
     * 한 틱 진행. 말마다 이동 여부를 굴리고, 결승선을 넘은 말이 나오면 순위를 확정하고 끝낸다.
     *
     * @return 경기가 끝났으면 true
     */
    public boolean tick() {
        if (finished) {
            return true;
        }
        List<Runner> crossed = new ArrayList<>();
        for (Runner r : runners) {
            boolean move = RANDOM.nextDouble() < moveChance(r.horse(), movePower);
            r.setMovedLastTick(move);
            if (move) {
                double jitter = 0.7 + RANDOM.nextDouble() * 0.6;   // 0.7 ~ 1.3
                r.advance(trackLength * moveStep * jitter);
                if (r.progress() >= trackLength) {
                    crossed.add(r);
                }
            }
        }
        if (crossed.isEmpty()) {
            return false;
        }
        // 같은 틱에 여럿이 넘으면 더 멀리 간 쪽이 앞 순위. 나머지는 현재 위치 순.
        List<Runner> order = new ArrayList<>(runners);
        order.sort((a, b) -> Double.compare(b.progress(), a.progress()));
        for (int i = 0; i < order.size(); i++) {
            order.get(i).setFinishOrder(i);
        }
        Runner winner = order.get(0);
        winner.setProgress(trackLength);
        winnerIndex = winner.index();
        finished = true;
        return true;
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
