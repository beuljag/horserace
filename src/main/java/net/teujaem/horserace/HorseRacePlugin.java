package net.teujaem.horserace;

import net.kyori.adventure.text.Component;
import net.milkbowl.vault.economy.Economy;
import net.teujaem.horserace.command.HorseRaceCommand;
import net.teujaem.horserace.game.Horse;
import net.teujaem.horserace.game.Race;
import net.teujaem.horserace.listener.GuiListener;
import net.teujaem.horserace.service.RaceManager;
import net.teujaem.horserace.util.Currency;
import net.teujaem.horserace.util.ItemCurrency;
import net.teujaem.horserace.util.Text;
import net.teujaem.horserace.util.VaultCurrency;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class HorseRacePlugin extends JavaPlugin {

    /** GUI 레인 수 한계. */
    public static final int MAX_HORSES = 5;

    private RaceManager raceManager;
    private Currency currency;

    // ---- 설정값 캐시 ----
    private String prefix = "&6[경마] &f";
    private List<Integer> betAmounts = new ArrayList<>();
    private List<Horse> horses = new ArrayList<>();
    private double movePower = 0.5;
    private double moveStep = 0.16;
    private int estimateSamples = 5000;
    /** 말 순서와 같은 인덱스의 추정 승률(0~1). 설정/배당이 바뀔 때 다시 계산. */
    private double[] winChances = new double[0];

    // ---- 실제 경기 통계 (stats.yml) ----
    /** 실제 통계를 승률로 쓰기 시작하는 최소 경기 수. 그 전엔 시뮬레이션 예상값. */
    private int statsMinRaces = 30;
    private int statsTotalRaces = 0;
    /** 말 이름 → 실제 우승 횟수 */
    private final Map<String, Integer> statsWins = new HashMap<>();
    /** 통계가 유효한 규칙(배당·이동 확률·이동 거리·트랙)의 서명. 바뀌면 통계 초기화. */
    private String statsSignature = "";
    private long tickInterval = 32;
    private double trackLength = 100;

    /** 오프라인 중 적중/환불된 금액. 접속 시 지급. pending.yml 에 저장. */
    private final Map<UUID, Integer> pendingPayouts = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        readConfig();
        loadPending();
        loadStats();

        this.raceManager = new RaceManager(this);
        getServer().getPluginManager().registerEvents(new GuiListener(this), this);

        PluginCommand command = getCommand("horserace");
        if (command != null) {
            HorseRaceCommand executor = new HorseRaceCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        getLogger().info("경마 활성화 완료. 재화: " + Text.strip(currency.display())
                + ", 말: " + horses.size() + "마리, 이동 확률 지수: " + movePower + ", 이동 거리: " + moveStep);
    }

    @Override
    public void onDisable() {
        if (raceManager != null) {
            raceManager.shutdownRefundAll();
        }
        savePending();
        saveStats();
    }

    public void readConfig() {
        reloadConfig();
        this.prefix = getConfig().getString("prefix", "&6[경마] &f");

        // 재화
        String display = getConfig().getString("currency.display", "&e금괴");
        String type = getConfig().getString("currency.type", "ITEM").toUpperCase();
        if (type.equals("VAULT")) {
            Economy economy = setupVault();
            if (economy != null) {
                this.currency = new VaultCurrency(economy, display);
                getLogger().info("Vault 이코노미 연동됨.");
            } else {
                getLogger().warning("currency.type=VAULT 인데 Vault/이코노미 플러그인을 못 찾았습니다. 아이템 화폐로 폴백합니다.");
                this.currency = buildItemCurrency(display);
            }
        } else {
            this.currency = buildItemCurrency(display);
        }

        // 판돈
        this.betAmounts = new ArrayList<>();
        for (int amount : getConfig().getIntegerList("bet-amounts")) {
            if (amount > 0) {
                betAmounts.add(amount);
            }
        }
        if (betAmounts.isEmpty()) {
            betAmounts.addAll(List.of(1, 5, 10, 25, 50, 100));
        }

        // 경기 규칙
        this.movePower = Math.max(0.1, Math.min(10.0, getConfig().getDouble("race.move-chance-power", 0.5)));
        this.moveStep = Math.max(0.01, Math.min(1.0, getConfig().getDouble("race.move-step", 0.16)));
        this.estimateSamples = Math.max(500, Math.min(100000, getConfig().getInt("race.estimate-samples", 5000)));
        this.statsMinRaces = Math.max(1, getConfig().getInt("race.stats-min-races", 30));
        this.tickInterval = Math.max(1, getConfig().getInt("race.tick-interval-ticks", 32));
        this.trackLength = Math.max(10, getConfig().getDouble("race.track-length", 100));

        // 말 목록
        List<Horse> loaded = new ArrayList<>();
        for (Map<?, ?> entry : getConfig().getMapList("horses")) {
            if (loaded.size() >= MAX_HORSES) {
                getLogger().warning("horses 는 최대 " + MAX_HORSES + "마리입니다. 나머지는 무시합니다.");
                break;
            }
            String name = entry.get("name") == null ? "말" : String.valueOf(entry.get("name"));
            String color = entry.get("color") == null ? "&f" : String.valueOf(entry.get("color"));
            String matName = entry.get("material") == null ? "SADDLE" : String.valueOf(entry.get("material"));
            Material material = Material.matchMaterial(matName);
            if (material == null || !material.isItem()) {
                getLogger().warning("horses." + name + " 의 material '" + matName + "' 을(를) 못 찾았습니다. SADDLE 로 대체합니다.");
                material = Material.SADDLE;
            }
            double odds = 2.0;
            if (entry.get("odds") instanceof Number n) {
                odds = Math.max(1.0, n.doubleValue());
            }
            loaded.add(new Horse(name, color, material, odds));
        }
        if (loaded.size() < 2) {
            getLogger().warning("horses 목록이 2마리 미만입니다. 기본 말을 사용합니다.");
            loaded = new ArrayList<>(List.of(
                    new Horse("번개", "&e", Material.YELLOW_WOOL, 1.1),
                    new Horse("흑풍", "&8", Material.BLACK_WOOL, 1.3),
                    new Horse("백설", "&f", Material.WHITE_WOOL, 1.5),
                    new Horse("홍염", "&c", Material.RED_WOOL, 1.7),
                    new Horse("청해", "&b", Material.LIGHT_BLUE_WOOL, 2.0)));
        }
        this.horses = loaded;
        recomputeWinChances();
    }

    /** 현재 말 목록과 규칙으로 승률을 시뮬레이션해 캐시한다. 규칙이 바뀌었으면 실제 통계도 초기화. */
    private void recomputeWinChances() {
        this.winChances = Race.estimateWinChances(horses, trackLength, movePower, moveStep, estimateSamples);
        String sig = ruleSignature();
        if (statsSignature.isEmpty()) {
            // 최초 기동: 아직 stats.yml 을 읽기 전이라 서명만 기억하고 loadStats() 에 맡긴다.
            statsSignature = sig;
            return;
        }
        if (!sig.equals(statsSignature)) {
            if (statsTotalRaces > 0) {
                getLogger().info("배당/규칙이 바뀌어 실제 승률 통계를 초기화합니다. (이전 " + statsTotalRaces + "경기)");
            }
            statsWins.clear();
            statsTotalRaces = 0;
            statsSignature = sig;
            saveStats();
        }
    }

    private String ruleSignature() {
        StringBuilder sb = new StringBuilder();
        for (Horse h : horses) {
            sb.append(h.name()).append('=').append(h.odds()).append(';');
        }
        sb.append("p=").append(movePower).append(";s=").append(moveStep).append(";t=").append(trackLength);
        return sb.toString();
    }

    // ---- 실제 통계 ----

    private File statsFile() {
        return new File(getDataFolder(), "stats.yml");
    }

    private void loadStats() {
        statsWins.clear();
        statsTotalRaces = 0;
        File file = statsFile();
        if (file.exists()) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            String sig = yaml.getString("signature", "");
            if (sig.equals(ruleSignature())) {
                statsTotalRaces = yaml.getInt("total", 0);
                var section = yaml.getConfigurationSection("wins");
                if (section != null) {
                    for (String key : section.getKeys(false)) {
                        statsWins.put(key, section.getInt(key));
                    }
                }
            } else {
                getLogger().info("stats.yml 이 현재 배당/규칙과 달라 실제 승률 통계를 새로 시작합니다.");
            }
        }
        statsSignature = ruleSignature();
        saveStats();
    }

    public void saveStats() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("signature", statsSignature);
        yaml.set("total", statsTotalRaces);
        for (Horse h : horses) {
            yaml.set("wins." + h.name(), statsWins.getOrDefault(h.name(), 0));
        }
        try {
            yaml.save(statsFile());
        } catch (IOException e) {
            getLogger().warning("stats.yml 저장 실패: " + e.getMessage());
        }
    }

    /** 경기가 끝날 때 호출. 우승마를 집계하고 저장한다. */
    public void recordResult(Horse winner) {
        statsTotalRaces++;
        statsWins.merge(winner.name(), 1, Integer::sum);
        saveStats();
    }

    public void resetStats() {
        statsWins.clear();
        statsTotalRaces = 0;
        saveStats();
    }

    public int statsTotalRaces() {
        return statsTotalRaces;
    }

    public int statsWins(Horse h) {
        return statsWins.getOrDefault(h.name(), 0);
    }

    /** 실제 통계를 승률로 쓰는 중이면 true (최소 경기 수 충족). */
    public boolean usingRealStats() {
        return statsTotalRaces >= statsMinRaces;
    }

    public int statsMinRaces() {
        return statsMinRaces;
    }

    private ItemCurrency buildItemCurrency(String display) {
        String matName = getConfig().getString("currency.material", "GOLD_INGOT");
        Material material = Material.matchMaterial(matName);
        if (material == null || !material.isItem()) {
            getLogger().warning("currency.material '" + matName + "' 을(를) 못 찾았습니다. GOLD_INGOT 로 대체합니다.");
            material = Material.GOLD_INGOT;
        }
        return new ItemCurrency(material, display);
    }

    private Economy setupVault() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return null;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        return rsp == null ? null : rsp.getProvider();
    }

    // ---- 관리자 편집 (config 에 저장) ----

    public void setBetAmounts(List<Integer> amounts) {
        List<Integer> cleaned = new ArrayList<>();
        for (int a : amounts) {
            if (a > 0 && !cleaned.contains(a)) {
                cleaned.add(a);
            }
        }
        cleaned.sort(Integer::compareTo);
        this.betAmounts = cleaned;
        getConfig().set("bet-amounts", cleaned);
        saveConfig();
    }

    public void setHorseOdds(int index, double odds) {
        List<Horse> copy = new ArrayList<>(horses);
        copy.set(index, copy.get(index).withOdds(Math.round(odds * 10.0) / 10.0));
        saveHorses(copy);
    }

    public void addHorse(Horse horse) {
        List<Horse> copy = new ArrayList<>(horses);
        copy.add(horse);
        saveHorses(copy);
    }

    public Horse removeHorse(int index) {
        List<Horse> copy = new ArrayList<>(horses);
        Horse removed = copy.remove(index);
        saveHorses(copy);
        return removed;
    }

    private void saveHorses(List<Horse> list) {
        this.horses = list;
        List<Map<String, Object>> serialized = new ArrayList<>();
        for (Horse h : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", h.name());
            m.put("color", h.color());
            m.put("material", h.material().name());
            m.put("odds", h.odds());
            serialized.add(m);
        }
        getConfig().set("horses", serialized);
        saveConfig();
        recomputeWinChances();
    }

    // ---- 보류 지급 (pending.yml) ----

    private File pendingFile() {
        return new File(getDataFolder(), "pending.yml");
    }

    private void loadPending() {
        pendingPayouts.clear();
        File file = pendingFile();
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String key : yaml.getKeys(false)) {
            try {
                pendingPayouts.put(UUID.fromString(key), yaml.getInt(key));
            } catch (IllegalArgumentException ignored) {
                // 잘못된 키는 무시
            }
        }
    }

    public void savePending() {
        YamlConfiguration yaml = new YamlConfiguration();
        pendingPayouts.forEach((id, amount) -> yaml.set(id.toString(), amount));
        try {
            yaml.save(pendingFile());
        } catch (IOException e) {
            getLogger().warning("pending.yml 저장 실패: " + e.getMessage());
        }
    }

    public Map<UUID, Integer> getPendingPayouts() {
        return pendingPayouts;
    }

    // ---- 메시지 ----

    public void message(CommandSender target, String legacyMessage) {
        target.sendMessage(Text.of(prefix + legacyMessage));
    }

    public Component prefixed(String legacyMessage) {
        return Text.of(prefix + legacyMessage);
    }

    // ---- 접근자 ----

    public RaceManager getRaceManager() {
        return raceManager;
    }

    public Currency getCurrency() {
        return currency;
    }

    public List<Integer> getBetAmounts() {
        return betAmounts;
    }

    public List<Horse> getHorses() {
        return horses;
    }

    public double getMovePower() {
        return movePower;
    }

    public double getMoveStep() {
        return moveStep;
    }

    /**
     * 표시용 승률(0~1). 실제 경기가 stats-min-races 이상 쌓였으면 실제 우승 비율,
     * 아니면 시뮬레이션 예상값. 전부 더하면 1.
     */
    public double winChance(Horse h) {
        if (usingRealStats()) {
            return (double) statsWins(h) / statsTotalRaces;
        }
        return estimatedWinChance(h);
    }

    /** 시뮬레이션 예상 승률(0~1). */
    public double estimatedWinChance(Horse h) {
        int index = horses.indexOf(h);
        if (index < 0 || index >= winChances.length) {
            return 0;
        }
        return winChances[index];
    }

    /** 이 말이 한 틱에 움직일 확률(0~1). */
    public double moveChance(Horse h) {
        return Race.moveChance(h, movePower);
    }

    /** 표시용 승률(%). */
    public int winChancePercent(Horse h) {
        return (int) Math.round(winChance(h) * 100);
    }

    /** 이 말의 승률 기준 공정 배당(기대값 1.0 이 되는 배당). 관리자 튜닝 참고용. */
    public double fairOdds(Horse h) {
        double p = winChance(h);
        return p <= 0 ? 0 : 1.0 / p;
    }

    public long getTickInterval() {
        return tickInterval;
    }

    public double getTrackLength() {
        return trackLength;
    }
}
