package net.teujaem.horserace.service;

import net.teujaem.horserace.HorseRacePlugin;
import net.teujaem.horserace.game.Bet;
import net.teujaem.horserace.game.Horse;
import net.teujaem.horserace.game.Race;
import net.teujaem.horserace.game.Runner;
import net.teujaem.horserace.gui.RaceGui;
import net.teujaem.horserace.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 플레이어별 1인 경기 관리. 클릭하면 바로 출발하고, 끝나면 결과 화면에서 바로 다시 할 수 있다.
 * 경기 중에 GUI 를 닫아도 경기는 끝까지 돌고 결과는 채팅으로 알려준다.
 */
public final class RaceManager {

    private record Session(Race race, RaceGui gui, BukkitTask task) {
    }

    private final HorseRacePlugin plugin;
    private final Map<UUID, Session> sessions = new HashMap<>();

    public RaceManager(HorseRacePlugin plugin) {
        this.plugin = plugin;
    }

    /** 플레이어의 현재 경기(진행 중이거나 결과 화면). 없으면 null. */
    public Race raceOf(Player player) {
        Session s = sessions.get(player.getUniqueId());
        return s == null ? null : s.race();
    }

    public RaceGui guiOf(Player player) {
        Session s = sessions.get(player.getUniqueId());
        return s == null ? null : s.gui();
    }

    /**
     * 베팅하고 바로 출발. 실패 사유는 플레이어에게 안내하고 false.
     */
    public boolean start(Player player, int horseIndex, int amount) {
        List<Horse> horses = plugin.getHorses();
        if (horseIndex < 0 || horseIndex >= horses.size()) {
            plugin.message(player, "&c그런 번호의 말이 없습니다. &7(1~" + horses.size() + ")");
            return false;
        }
        if (amount <= 0) {
            plugin.message(player, "&c금액이 올바르지 않습니다.");
            return false;
        }
        Session existing = sessions.get(player.getUniqueId());
        if (existing != null && !existing.race().finished()) {
            plugin.message(player, "&c이미 경기가 진행 중입니다.");
            existing.gui().open();
            return false;
        }
        String cur = Text.strip(plugin.getCurrency().display());
        if (!plugin.getCurrency().take(player, amount)) {
            plugin.message(player, "&c재화가 부족합니다. &7(잔액 " + plugin.getCurrency().balance(player) + " " + cur + ")");
            return false;
        }
        if (existing != null) {
            existing.task().cancel();
        }

        Horse horse = horses.get(horseIndex);
        Bet bet = new Bet(horseIndex, amount, horse.odds());
        Race race = new Race(horses, bet, plugin.getTrackLength(), plugin.getMovePower(), plugin.getMoveStep());
        RaceGui gui = new RaceGui(plugin, player, race);
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> tick(player),
                plugin.getTickInterval(), plugin.getTickInterval());
        sessions.put(player.getUniqueId(), new Session(race, gui, task));

        plugin.message(player, "&e" + (horseIndex + 1) + "번 " + horse.coloredName() + " &7x " + amount + " " + cur
                + " &8(배당 " + horse.oddsText() + ", 적중 시 " + bet.payout() + ") &f출발!");
        player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.8f, 1f);
        gui.open();
        return true;
    }

    /** 결과 화면에서 "같은 베팅 한 번 더". */
    public void repeat(Player player) {
        Race race = raceOf(player);
        if (race == null || !race.finished()) {
            return;
        }
        start(player, race.bet().runnerIndex(), race.bet().amount());
    }

    private void tick(Player player) {
        Session s = sessions.get(player.getUniqueId());
        if (s == null) {
            return;
        }
        boolean done = s.race().tick();
        if (player.isOnline() && player.getOpenInventory().getTopInventory().getHolder() == s.gui()) {
            s.gui().render();
        }
        if (done) {
            s.task().cancel();
            finish(player, s);
        }
    }

    private void finish(Player player, Session s) {
        Race race = s.race();
        Runner winner = race.winner();
        String cur = Text.strip(plugin.getCurrency().display());
        int payout = race.won() ? race.bet().payout() : 0;
        plugin.recordResult(winner.horse());

        if (race.won()) {
            if (player.isOnline()) {
                plugin.getCurrency().give(player, payout);
                plugin.message(player, "&a적중! &6" + winner.horse().coloredName() + " &f우승! &a+" + payout + " " + cur);
                player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
            } else {
                plugin.getPendingPayouts().merge(player.getUniqueId(), payout, Integer::sum);
                plugin.savePending();
            }
        } else if (player.isOnline()) {
            plugin.message(player, "&7낙첨... 우승은 " + winner.horse().coloredName() + " &7(" + winner.horse().oddsText() + ")");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.7f);
        }
        if (player.isOnline() && player.getOpenInventory().getTopInventory().getHolder() == s.gui()) {
            s.gui().render(); // 결과 + 버튼
        }
        // 세션은 남겨 둔다: 결과 화면의 "한 번 더" 에 쓰이고, 다음 start 에서 교체된다.
    }

    /** 결과 화면을 닫거나 나가기. 진행 중 경기는 건드리지 않는다 (끝까지 돌고 정산). */
    public void quit(Player player) {
        Session s = sessions.get(player.getUniqueId());
        if (s != null && s.race().finished()) {
            sessions.remove(player.getUniqueId());
        }
    }

    /** 플러그인 비활성화: 진행 중 경기는 원금 환불. */
    public void shutdownRefundAll() {
        for (Map.Entry<UUID, Session> e : sessions.entrySet()) {
            Session s = e.getValue();
            s.task().cancel();
            if (!s.race().finished()) {
                Player p = Bukkit.getPlayer(e.getKey());
                int amount = s.race().bet().amount();
                if (p != null && p.isOnline()) {
                    plugin.getCurrency().give(p, amount);
                    plugin.message(p, "&7서버 종료로 경기가 취소되어 " + amount + " "
                            + Text.strip(plugin.getCurrency().display()) + " 을(를) 환불했습니다.");
                } else {
                    plugin.getPendingPayouts().merge(e.getKey(), amount, Integer::sum);
                }
            }
        }
        sessions.clear();
        plugin.savePending();
    }
}
