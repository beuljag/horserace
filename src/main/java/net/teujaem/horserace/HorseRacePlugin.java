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
    private double oddsPower = 2.0;
    private long tickInterval = 8;
    private double trackLength = 100;

    /** 오프라인 중 적중/환불된 금액. 접속 시 지급. pending.yml 에 저장. */
    private final Map<UUID, Integer> pendingPayouts = new HashMap<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        readConfig();
        loadPending();

        this.raceManager = new RaceManager(this);
        getServer().getPluginManager().registerEvents(new GuiListener(this), this);

        PluginCommand command = getCommand("horserace");
        if (command != null) {
            HorseRaceCommand executor = new HorseRaceCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        getLogger().info("경마 활성화 완료. 재화: " + Text.strip(currency.display())
                + ", 말: " + horses.size() + "마리, 배당 지수: " + oddsPower);
    }

    @Override
    public void onDisable() {
        if (raceManager != null) {
            raceManager.shutdownRefundAll();
        }
        savePending();
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
        this.oddsPower = Math.max(0.1, Math.min(10.0, getConfig().getDouble("race.odds-power", 2.0)));
        this.tickInterval = Math.max(1, getConfig().getInt("race.tick-interval-ticks", 8));
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

    public double getOddsPower() {
        return oddsPower;
    }

    /** 표시용 승률(0~1): 전체 말 중 이 말이 우승하는 비율. 전부 더하면 1. */
    public double winChance(Horse h) {
        int index = horses.indexOf(h);
        if (index < 0) {
            return 0;
        }
        return Race.winChance(horses, index, oddsPower);
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
