package net.teujaem.horserace.game;

/** 한 경기에 출전한 말. 현재 위치와 결승 순위를 들고 있다. */
public final class Runner {

    private final int index;          // 출전 번호 (0부터) = config 순서
    private final Horse horse;
    private double progress;          // 0 ~ trackLength
    private int finishOrder = -1;     // 결승 통과 순위 (0 = 1등). 미통과면 -1
    private boolean movedLastTick;    // 직전 틱에 움직였는지 (연출용)

    public Runner(int index, Horse horse) {
        this.index = index;
        this.horse = horse;
    }

    public int index() {
        return index;
    }

    public Horse horse() {
        return horse;
    }

    public double progress() {
        return progress;
    }

    public void setProgress(double progress) {
        this.progress = progress;
    }

    public void advance(double amount) {
        this.progress += amount;
    }

    public int finishOrder() {
        return finishOrder;
    }

    public boolean finished() {
        return finishOrder >= 0;
    }

    public void setFinishOrder(int order) {
        this.finishOrder = order;
    }

    public boolean movedLastTick() {
        return movedLastTick;
    }

    public void setMovedLastTick(boolean moved) {
        this.movedLastTick = moved;
    }
}
