package me.chaos;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.LargeFireball;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/** Глобальные модификаторы: граница мира, вечная ночь, воздушные припасы, метеориты. */
public final class WorldEffects {

    private record BorderSnap(double x, double z, double size, double damage, double buffer, int warning) {}

    private final ChaosPlugin plugin;
    private final ChaosGame game;
    private final Random rnd = new Random();
    private final File backupFile;

    private final Map<UUID, BorderSnap> snaps = new HashMap<>();
    private final Set<UUID> nightWorlds = new HashSet<>();
    private final List<Location> drops = new ArrayList<>();

    private int airdropIn;
    private int meteorIn;
    private int lastAnnounced;

    public WorldEffects(ChaosPlugin plugin, ChaosGame game) {
        this.plugin = plugin;
        this.game = game;
        this.backupFile = new File(plugin.getDataFolder(), "border-backup.yml");
    }

    // ------------------------------------------------------------ жизненный цикл

    public void onGameStart() {
        airdropIn = plugin.getConfig().getInt("airdrop-seconds", 150);
        meteorIn = plugin.getConfig().getInt("meteor-seconds", 40);
        lastAnnounced = 0;
        drops.clear();
        if (game.mod(Modifier.BORDER)) applyBorder();
    }

    public void onGameStop() {
        restoreBorder();
        morning();
        drops.clear();
    }

    public void onToggle(Modifier m, boolean on) {
        if (!game.isRunning()) return;
        switch (m) {
            case BORDER -> {
                if (on) applyBorder();
                else restoreBorder();
            }
            case NIGHT -> {
                if (!on) morning();
            }
            case AIRDROP -> airdropIn = plugin.getConfig().getInt("airdrop-seconds", 150);
            case METEOR -> meteorIn = plugin.getConfig().getInt("meteor-seconds", 40);
            default -> { }
        }
    }

    /** Раз в секунду, пока игра идёт и не на паузе. */
    public void tick() {
        if (game.mod(Modifier.NIGHT)) {
            for (World w : overworlds()) {
                w.setTime(18000);
                if (!w.hasStorm()) w.setStorm(true);
                if (!w.isThundering()) w.setThundering(true);
                nightWorlds.add(w.getUID());
            }
        }

        if (game.mod(Modifier.ESCALATION)) {
            int m = game.elapsedMinutes();
            if (m > 0 && m % 5 == 0 && m != lastAnnounced) {
                lastAnnounced = m;
                Fx.broadcast("<dark_gray>[<gold>Хаос<dark_gray>] <dark_red>Угроза растёт! <gray>Новые мобы сильнее на <white>"
                        + Math.min(400, m * 8) + "%");
                Fx.soundAll("entity.wither.spawn", 0.4f, 0.7f);
            }
        }

        if (game.mod(Modifier.AIRDROP) && --airdropIn <= 0) {
            airdropIn = plugin.getConfig().getInt("airdrop-seconds", 150);
            spawnAirdrop();
        }

        if (game.mod(Modifier.METEOR) && --meteorIn <= 0) {
            meteorIn = plugin.getConfig().getInt("meteor-seconds", 40);
            spawnMeteor();
        }

        beams();
    }

    // ------------------------------------------------------------ вспомогательное

    private List<World> overworlds() {
        Set<World> set = new HashSet<>();
        for (Player p : game.players()) {
            if (p.getWorld().getEnvironment() == World.Environment.NORMAL) set.add(p.getWorld());
        }
        return new ArrayList<>(set);
    }

    private List<Player> overworldPlayers() {
        List<Player> list = new ArrayList<>();
        for (Player p : game.players()) {
            if (p.getWorld().getEnvironment() == World.Environment.NORMAL) list.add(p);
        }
        return list;
    }

    // ------------------------------------------------------------ сжимающийся мир

    private void applyBorder() {
        if (!snaps.isEmpty()) return;
        double start = plugin.getConfig().getDouble("border.start-size", 300);
        double min = plugin.getConfig().getDouble("border.min-size", 30);
        long seconds = plugin.getConfig().getLong("border.shrink-seconds", 1800);

        Map<World, List<Player>> byWorld = new HashMap<>();
        for (Player p : overworldPlayers()) byWorld.computeIfAbsent(p.getWorld(), w -> new ArrayList<>()).add(p);

        for (Map.Entry<World, List<Player>> e : byWorld.entrySet()) {
            World w = e.getKey();
            List<Player> ps = e.getValue();
            double cx = 0;
            double cz = 0;
            for (Player p : ps) {
                cx += p.getLocation().getX();
                cz += p.getLocation().getZ();
            }
            cx /= ps.size();
            cz /= ps.size();
            double far = 0;
            for (Player p : ps) {
                far = Math.max(far, Math.max(Math.abs(p.getLocation().getX() - cx), Math.abs(p.getLocation().getZ() - cz)));
            }

            WorldBorder b = w.getWorldBorder();
            snaps.put(w.getUID(), new BorderSnap(b.getCenter().getX(), b.getCenter().getZ(), b.getSize(),
                    b.getDamageAmount(), b.getDamageBuffer(), b.getWarningDistance()));

            double size = Math.max(start, far * 2 + 60);
            b.setCenter(cx, cz);
            b.setSize(size);
            b.setDamageAmount(2.0);
            b.setDamageBuffer(1.0);
            b.setWarningDistance(8);
            if (size > min) b.setSize(min, seconds);
        }
        saveBackup();
        if (!snaps.isEmpty()) {
            Fx.broadcast("<dark_gray>[<gold>Хаос<dark_gray>] <dark_red>Граница мира начала сужаться! <gray>До <white>"
                    + (int) min + "<gray> блоков за <white>" + (seconds / 60) + "<gray> мин. Держитесь ближе друг к другу.");
            Fx.soundAll("block.beacon.deactivate", 1f, 0.6f);
        }
    }

    private void restoreBorder() {
        if (snaps.isEmpty()) return;
        for (Map.Entry<UUID, BorderSnap> e : snaps.entrySet()) {
            World w = Bukkit.getWorld(e.getKey());
            if (w != null) applySnap(w, e.getValue());
        }
        snaps.clear();
        if (backupFile.exists()) backupFile.delete();
    }

    private void applySnap(World w, BorderSnap s) {
        WorldBorder b = w.getWorldBorder();
        b.setCenter(s.x(), s.z());
        b.setSize(s.size());
        b.setDamageAmount(s.damage());
        b.setDamageBuffer(s.buffer());
        b.setWarningDistance(s.warning());
    }

    private void saveBackup() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<UUID, BorderSnap> e : snaps.entrySet()) {
            String p = "worlds." + e.getKey() + ".";
            BorderSnap s = e.getValue();
            y.set(p + "x", s.x());
            y.set(p + "z", s.z());
            y.set(p + "size", s.size());
            y.set(p + "damage", s.damage());
            y.set(p + "buffer", s.buffer());
            y.set(p + "warning", s.warning());
        }
        try {
            plugin.getDataFolder().mkdirs();
            y.save(backupFile);
        } catch (IOException ex) {
            plugin.getLogger().warning("Не удалось сохранить border-backup.yml: " + ex.getMessage());
        }
    }

    /** Если сервер упал посреди игры — возвращает границу мира как была. */
    public void restoreBackup() {
        if (!backupFile.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(backupFile);
        ConfigurationSection sec = y.getConfigurationSection("worlds");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                try {
                    World w = Bukkit.getWorld(UUID.fromString(key));
                    if (w == null) continue;
                    ConfigurationSection c = sec.getConfigurationSection(key);
                    if (c == null) continue;
                    applySnap(w, new BorderSnap(c.getDouble("x"), c.getDouble("z"), c.getDouble("size", 5.9999968E7),
                            c.getDouble("damage", 0.2), c.getDouble("buffer", 5.0), c.getInt("warning", 5)));
                    plugin.getLogger().info("Граница мира в " + w.getName() + " восстановлена после прерванной игры.");
                } catch (IllegalArgumentException ignored) {
                    // битый ключ
                }
            }
        }
        backupFile.delete();
    }

    // ------------------------------------------------------------ вечная ночь

    private void morning() {
        for (UUID id : nightWorlds) {
            World w = Bukkit.getWorld(id);
            if (w == null) continue;
            w.setTime(1000);
            w.setStorm(false);
            w.setThundering(false);
        }
        nightWorlds.clear();
    }

    // ------------------------------------------------------------ воздушные припасы

    private List<ItemStack> lootPool() {
        return List.of(
                new ItemStack(Material.DIAMOND, 4), new ItemStack(Material.DIAMOND_SWORD),
                new ItemStack(Material.DIAMOND_CHESTPLATE), new ItemStack(Material.DIAMOND_HELMET),
                new ItemStack(Material.NETHERITE_INGOT, 1), new ItemStack(Material.GOLDEN_APPLE, 4),
                new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, 1), new ItemStack(Material.TOTEM_OF_UNDYING),
                new ItemStack(Material.ENDER_PEARL, 4), new ItemStack(Material.TNT, 8),
                new ItemStack(Material.BOW), new ItemStack(Material.ARROW, 32),
                new ItemStack(Material.CROSSBOW), new ItemStack(Material.SHIELD),
                new ItemStack(Material.EXPERIENCE_BOTTLE, 16), new ItemStack(Material.COOKED_BEEF, 16),
                new ItemStack(Material.OBSIDIAN, 8), new ItemStack(Material.IRON_INGOT, 12),
                new ItemStack(Material.FIREWORK_ROCKET, 16));
    }

    private void spawnAirdrop() {
        List<Player> ps = overworldPlayers();
        if (ps.isEmpty()) return;
        Player target = ps.get(rnd.nextInt(ps.size()));
        World w = target.getWorld();

        for (int attempt = 0; attempt < 8; attempt++) {
            double ang = rnd.nextDouble() * Math.PI * 2;
            double rad = 40 + rnd.nextDouble() * 60;
            int x = target.getLocation().getBlockX() + (int) Math.round(Math.cos(ang) * rad);
            int z = target.getLocation().getBlockZ() + (int) Math.round(Math.sin(ang) * rad);
            if (!w.getWorldBorder().isInside(new Location(w, x, 64, z))) continue;

            int y = w.getHighestBlockYAt(x, z);
            Block ground = w.getBlockAt(x, y, z);
            if (ground.isLiquid()) continue;
            Block spot = w.getBlockAt(x, y + 1, z);
            if (!spot.getType().isAir()) continue;

            spot.setType(Material.CHEST);
            BlockState st = spot.getState();
            if (!(st instanceof Chest chest)) {
                spot.setType(Material.AIR);
                continue;
            }
            chest.customName(Fx.mm("<aqua>Воздушные припасы"));
            chest.update();

            Inventory inv = chest.getBlockInventory();
            List<ItemStack> pool = lootPool();
            List<Integer> slots = new ArrayList<>();
            for (int i = 0; i < 27; i++) slots.add(i);
            Collections.shuffle(slots, rnd);
            for (int i = 0; i < 7; i++) inv.setItem(slots.get(i), pool.get(rnd.nextInt(pool.size())).clone());

            Location loc = spot.getLocation();
            drops.add(loc);
            while (drops.size() > 5) drops.remove(0);

            w.strikeLightningEffect(loc.clone().add(0.5, 0, 0.5));
            Fx.broadcast("<dark_gray>[<gold>Хаос<dark_gray>] <aqua>Воздушные припасы упали! <gray>Координаты: <white>"
                    + x + " " + (y + 1) + " " + z + " <gray>(рядом с <yellow>" + target.getName() + "<gray>)");
            Fx.soundAll("entity.firework_rocket.launch", 0.8f, 0.8f);
            Fx.soundAll("block.note_block.bell", 1f, 1.4f);
            return;
        }
    }

    /** Световой столб над ещё не опустошёнными сундуками. */
    private void beams() {
        Iterator<Location> it = drops.iterator();
        while (it.hasNext()) {
            Location l = it.next();
            World w = l.getWorld();
            if (w == null || !w.isChunkLoaded(l.getBlockX() >> 4, l.getBlockZ() >> 4)) continue;
            BlockState st = l.getBlock().getState();
            if (!(st instanceof Chest c) || c.getBlockInventory().isEmpty()) {
                it.remove();
                continue;
            }
            for (int dy = 1; dy <= 40; dy += 2) {
                w.spawnParticle(Particle.END_ROD, l.getX() + 0.5, l.getY() + dy, l.getZ() + 0.5, 1, 0, 0, 0, 0, null, true);
            }
        }
    }

    // ------------------------------------------------------------ метеориты

    private void spawnMeteor() {
        List<Player> ps = overworldPlayers();
        if (ps.isEmpty()) return;
        Player p = ps.get(rnd.nextInt(ps.size()));
        World w = p.getWorld();

        Location target = p.getLocation().add(rnd.nextInt(9) - 4, 0, rnd.nextInt(9) - 4);
        Location from = target.clone().add(rnd.nextInt(25) - 12, 45, rnd.nextInt(25) - 12);
        from.setY(Math.min(w.getMaxHeight() - 3, from.getY()));
        Vector dir = target.toVector().subtract(from.toVector()).normalize();

        w.spawn(from, LargeFireball.class, f -> {
            f.setDirection(dir);
            f.setYield(2.0f);
            f.setIsIncendiary(false);
            Util.noGrief(plugin, f);
        });

        for (Player o : Bukkit.getOnlinePlayers()) {
            Fx.sound(o, "entity.ghast.shoot", 0.6f, 0.6f);
        }
        p.sendActionBar(Fx.mm("<gold><bold>Метеорит! <gray>Смотри на небо"));
    }
}
