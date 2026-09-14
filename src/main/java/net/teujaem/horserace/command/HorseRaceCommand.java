package net.teujaem.horserace.command;

import net.teujaem.horserace.HorseRacePlugin;
import net.teujaem.horserace.game.Horse;
import net.teujaem.horserace.game.Race;
import net.teujaem.horserace.gui.BetGui;
import net.teujaem.horserace.gui.RaceGui;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

/** /hr 명령. 플레이어용(베팅/말 목록)과 관리자용(배당·말·판돈 편집, 리로드). */
public final class HorseRaceCommand implements CommandExecutor, TabCompleter {

    private final HorseRacePlugin plugin;

    public HorseRaceCommand(HorseRacePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, String[] args) {
        if (args.length == 0) {
            Player player = requirePlayer(sender);
            if (player == null) {
                return true;
            }
            Race race = plugin.getRaceManager().raceOf(player);
            if (race != null && !race.finished()) {
                RaceGui gui = plugin.getRaceManager().guiOf(player);
                if (gui != null) {
                    gui.open();
                }
                return true;
            }
            new BetGui(plugin, player).open();
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "help", "도움말" -> help(sender);

            case "bet", "베팅" -> {
                Player player = requirePlayer(sender);
                if (player == null) {
                    return true;
                }
                if (args.length < 3) {
                    plugin.message(player, "&c사용법: /hr bet <말번호> <금액>");
                    return true;
                }
                plugin.getRaceManager().start(player, parseInt(args[1]) - 1, parseInt(args[2]));
            }

            case "horses", "list", "말" -> listHorses(sender);

            case "stats", "통계" -> {
                if (args.length >= 2 && args[1].equalsIgnoreCase("reset")) {
                    if (!admin(sender)) {
                        return true;
                    }
                    plugin.resetStats();
                    plugin.message(sender, "&a실제 승률 통계를 초기화했습니다. 다시 예상값을 표시합니다.");
                    return true;
                }
                stats(sender);
            }

            // ---- 관리자 ----
            case "odds", "배당" -> {
                if (!admin(sender)) {
                    return true;
                }
                if (args.length < 3) {
                    plugin.message(sender, "&c사용법: /hr odds <말번호> <배당>  &7예: /hr odds 3 1.5");
                    listHorses(sender);
                    return true;
                }
                int index = parseInt(args[1]) - 1;
                double odds = parseDouble(args[2]);
                if (index < 0 || index >= plugin.getHorses().size()) {
                    plugin.message(sender, "&c그런 번호의 말이 없습니다.");
                    return true;
                }
                if (odds < 1.0 || odds > 1000) {
                    plugin.message(sender, "&c배당은 1.0 이상이어야 합니다.");
                    return true;
                }
                plugin.setHorseOdds(index, odds);
                Horse h = plugin.getHorses().get(index);
                plugin.message(sender, "&a" + (index + 1) + "번 " + h.coloredName() + " &a배당 → &e" + h.oddsText()
                        + " &7(승률 약 " + plugin.winChancePercent(h) + "%, 공정 배당 "
                        + String.format("%.1f", plugin.fairOdds(h)) + "배)");
                plugin.message(sender, "&7배당을 바꾸면 모든 말의 승률이 다시 계산됩니다.");
            }

            case "horse" -> {
                if (!admin(sender)) {
                    return true;
                }
                horseEdit(sender, args);
            }

            case "amounts", "판돈" -> {
                if (!admin(sender)) {
                    return true;
                }
                amounts(sender, args);
            }

            case "reload", "리로드" -> {
                if (!admin(sender)) {
                    return true;
                }
                plugin.readConfig();
                plugin.message(sender, "&a설정을 다시 불러왔습니다. (말 " + plugin.getHorses().size() + "마리)");
            }

            default -> help(sender);
        }
        return true;
    }

    private void listHorses(CommandSender sender) {
        List<Horse> horses = plugin.getHorses();
        String basis = plugin.usingRealStats()
                ? "실제 " + plugin.statsTotalRaces() + "경기 기준"
                : "예상값, 실제 " + plugin.statsTotalRaces() + "/" + plugin.statsMinRaces() + "경기";
        plugin.message(sender, "&6말 목록 &7(승률: " + basis + ")");
        for (int i = 0; i < horses.size(); i++) {
            Horse h = horses.get(i);
            String line = "&f" + (i + 1) + "번 " + h.coloredName() + " &7배당 &e" + h.oddsText()
                    + " &7/ 승률 약 " + plugin.winChancePercent(h) + "% / 이동 " + (int) Math.round(plugin.moveChance(h) * 100) + "%";
            if (sender.hasPermission("horserace.admin")) {
                line += " &8(공정 배당 " + String.format("%.1f", plugin.fairOdds(h)) + "배)";
            }
            plugin.message(sender, line);
        }
    }

    private void stats(CommandSender sender) {
        int total = plugin.statsTotalRaces();
        plugin.message(sender, "&6실제 경기 통계 &7(총 " + total + "경기"
                + (plugin.usingRealStats() ? ", 승률에 반영 중" : ", " + plugin.statsMinRaces() + "경기부터 승률에 반영") + ")");
        List<Horse> horses = plugin.getHorses();
        for (int i = 0; i < horses.size(); i++) {
            Horse h = horses.get(i);
            int wins = plugin.statsWins(h);
            String real = total > 0 ? String.format("%.1f", wins * 100.0 / total) + "%" : "-";
            plugin.message(sender, "&f" + (i + 1) + "번 " + h.coloredName() + " &7우승 " + wins + "회 &f" + real
                    + " &8(예상 " + Math.round(plugin.estimatedWinChance(h) * 100) + "%)");
        }
        if (sender.hasPermission("horserace.admin")) {
            plugin.message(sender, "&7/hr stats reset 으로 초기화. 배당이나 규칙을 바꾸면 자동 초기화됩니다.");
        }
    }

    /** /hr horse add <이름> <배당> [아이템] [색코드] | remove <번호> */
    private void horseEdit(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.message(sender, "&7/hr horse add <이름> <배당> [아이템] [색코드]");
            plugin.message(sender, "&7/hr horse remove <번호>");
            return;
        }
        switch (args[1].toLowerCase()) {
            case "add" -> {
                if (args.length < 4) {
                    plugin.message(sender, "&c사용법: /hr horse add <이름> <배당> [아이템] [색코드]");
                    return;
                }
                if (plugin.getHorses().size() >= 5) {
                    plugin.message(sender, "&c말은 최대 5마리까지입니다. 먼저 하나 지우세요.");
                    return;
                }
                double odds = parseDouble(args[3]);
                if (odds < 1.0) {
                    plugin.message(sender, "&c배당은 1.0 이상이어야 합니다.");
                    return;
                }
                Material material = Material.SADDLE;
                if (args.length >= 5) {
                    Material m = Material.matchMaterial(args[4]);
                    if (m == null || !m.isItem()) {
                        plugin.message(sender, "&c아이템 '" + args[4] + "' 을(를) 못 찾았습니다.");
                        return;
                    }
                    material = m;
                }
                String color = args.length >= 6 ? args[5] : "&f";
                plugin.addHorse(new Horse(args[2], color, material, odds));
                plugin.message(sender, "&a말 추가: " + color + args[2] + " &7배당 " + String.format("%.1f", odds) + "배");
            }
            case "remove" -> {
                int index = args.length >= 3 ? parseInt(args[2]) - 1 : -1;
                if (index < 0 || index >= plugin.getHorses().size()) {
                    plugin.message(sender, "&c그런 번호의 말이 없습니다.");
                    return;
                }
                if (plugin.getHorses().size() <= 2) {
                    plugin.message(sender, "&c말은 최소 2마리가 필요합니다.");
                    return;
                }
                Horse removed = plugin.removeHorse(index);
                plugin.message(sender, "&a말 삭제: " + removed.coloredName());
            }
            default -> plugin.message(sender, "&7/hr horse add|remove ...");
        }
    }

    private void amounts(CommandSender sender, String[] args) {
        if (args.length < 2) {
            plugin.message(sender, "&7판돈: &f" + plugin.getBetAmounts());
            plugin.message(sender, "&7/hr amounts add|remove <금액> | set <금액...>");
            return;
        }
        List<Integer> list = new ArrayList<>(plugin.getBetAmounts());
        switch (args[1].toLowerCase()) {
            case "add" -> {
                int v = args.length > 2 ? parseInt(args[2]) : -1;
                if (v <= 0) {
                    plugin.message(sender, "&c금액을 입력하세요.");
                    return;
                }
                list.add(v);
            }
            case "remove" -> list.remove(Integer.valueOf(args.length > 2 ? parseInt(args[2]) : -1));
            case "set" -> {
                list.clear();
                for (int i = 2; i < args.length; i++) {
                    int v = parseInt(args[i]);
                    if (v > 0) {
                        list.add(v);
                    }
                }
                if (list.isEmpty()) {
                    plugin.message(sender, "&c금액을 하나 이상 입력하세요.");
                    return;
                }
            }
            default -> {
                plugin.message(sender, "&7/hr amounts add|remove <금액> | set <금액...>");
                return;
            }
        }
        plugin.setBetAmounts(list);
        plugin.message(sender, "&a판돈 목록: &f" + plugin.getBetAmounts());
    }

    private void help(CommandSender sender) {
        plugin.message(sender, "&6/hr &7- 말 선택 화면 (경기 중이면 경기장)");
        plugin.message(sender, "&6/hr bet <말번호> <금액> &7- 명령으로 바로 베팅+출발");
        plugin.message(sender, "&6/hr horses &7- 말 목록과 배당");
        plugin.message(sender, "&6/hr stats &7- 실제 경기 통계");
        if (sender.hasPermission("horserace.admin")) {
            plugin.message(sender, "&c/hr odds <번호> <배당> &7- 말 배당 변경");
            plugin.message(sender, "&c/hr horse add|remove &7- 말 추가/삭제");
            plugin.message(sender, "&c/hr amounts &7- 판돈 목록 편집");
            plugin.message(sender, "&c/hr stats reset &7- 실제 승률 통계 초기화");
            plugin.message(sender, "&c/hr reload &7- 설정 리로드");
        }
    }

    private Player requirePlayer(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("플레이어만 사용할 수 있습니다.");
            return null;
        }
        if (!player.hasPermission("horserace.play")) {
            plugin.message(player, "&c권한이 없습니다.");
            return null;
        }
        return player;
    }

    private boolean admin(CommandSender sender) {
        if (sender.hasPermission("horserace.admin")) {
            return true;
        }
        plugin.message(sender, "&c권한이 없습니다.");
        return false;
    }

    private int parseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private double parseDouble(String s) {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, String[] args) {
        List<String> out = new ArrayList<>();
        boolean admin = sender.hasPermission("horserace.admin");
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("bet", "horses", "stats", "help"));
            if (admin) {
                subs.addAll(List.of("odds", "horse", "amounts", "reload"));
            }
            for (String s : subs) {
                if (s.startsWith(args[0].toLowerCase())) {
                    out.add(s);
                }
            }
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("bet") || args[0].equalsIgnoreCase("odds"))) {
            for (int i = 1; i <= plugin.getHorses().size(); i++) {
                out.add(String.valueOf(i));
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("bet")) {
            for (int a : plugin.getBetAmounts()) {
                out.add(String.valueOf(a));
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("odds")) {
            out.addAll(List.of("1.1", "1.3", "1.5", "1.7", "2.0"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("horse")) {
            out.addAll(List.of("add", "remove"));
        } else if (args.length == 2 && args[0].equalsIgnoreCase("stats") && admin) {
            out.add("reset");
        } else if (args.length == 2 && args[0].equalsIgnoreCase("amounts")) {
            out.addAll(List.of("add", "remove", "set"));
        }
        return out;
    }
}
