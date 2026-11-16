package me.chaos;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.attribute.AttributeModifier.Operation;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.block.Block;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

public final class ChaosListener implements Listener {

    private static final Set<String> LUCKY_DENY_EXACT = Set.of(
            "AIR", "CAVE_AIR", "VOID_AIR", "BARRIER", "LIGHT", "DEBUG_STICK", "KNOWLEDGE_BOOK",
            "BEDROCK", "SPAWNER", "TRIAL_SPAWNER", "VAULT", "JIGSAW", "END_PORTAL_FRAME",
            "REINFORCED_DEEPSLATE", "PETRIFIED_OAK_SLAB", "FARMLAND");

    private final ChaosPlugin plugin;
    private final ChaosGame game;
    private final Random rnd = new Random();
    private final List<Material> luckyPool = new ArrayList<>();
    private boolean linking;

    public ChaosListener(ChaosPlugin plugin, ChaosGame game) {
        this.plugin = plugin;
        this.game = game;
        for (Material m : Material.values()) {
            String n = m.name();
            if (n.startsWith("LEGACY_") || !m.isItem() || m.isAir()) continue;
            if (LUCKY_DENY_EXACT.contains(n)) continue;
            if (n.contains("COMMAND_BLOCK") || n.startsWith("STRUCTURE_") || n.startsWith("TEST_")) continue;
            luckyPool.add(m);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onFall(EntityDamageEvent e) {
        if (e.getCause() == EntityDamageEvent.DamageCause.FALL
                && e.getEntity() instanceof Player
                && game.mod(Modifier.NO_FALL)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (!game.mod(Modifier.LUCKY)) return;
        Player p = e.getPlayer();
        if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) return;

        e.setDropItems(false);
        Block b = e.getBlock();
        Location loc = b.getLocation().add(0.5, 0.5, 0.5);

        if (rnd.nextInt(100) < 5) {
            p.sendMessage(Fx.mm("<red>Лакиблок оказался ловушкой!"));
            b.getWorld().spawn(loc, TNTPrimed.class, t -> {
                t.setFuseTicks(40);
                Util.noGrief(plugin, t);
            });
            return;
        }
        Material m = luckyPool.get(rnd.nextInt(luckyPool.size()));
        int amount = Math.max(1, Math.min(m.getMaxStackSize(), 1 + rnd.nextInt(3)));
        b.getWorld().dropItemNaturally(loc, new ItemStack(m, amount));
        b.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc, 8, 0.3, 0.3, 0.3, 0);
        Fx.sound(p, "entity.experience_orb.pickup", 0.5f, 0.8f + rnd.nextFloat() * 0.8f);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!game.mod(Modifier.VAMPIRE) || !(e.getDamager() instanceof Player p)) return;
        double heal = e.getFinalDamage() * 0.3;
        if (heal <= 0) return;
        AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
        double cap = max != null ? max.getValue() : 20.0;
        p.setHealth(Math.min(cap, p.getHealth() + heal));
        p.getWorld().spawnParticle(Particle.HEART, p.getLocation().add(0, 1.8, 0), 2, 0.3, 0.2, 0.3, 0);
        Fx.sound(p, "entity.phantom.bite", 0.4f, 1.5f);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDeath(EntityDeathEvent e) {
        if (!game.mod(Modifier.BOOM) || !(e.getEntity() instanceof Mob)) return;
        Location l = e.getEntity().getLocation();
        l.getWorld().createExplosion(l, 1.6f, false, false, e.getEntity());
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        if (e.getEntity().getPersistentDataContainer().has(plugin.noGriefKey(), PersistentDataType.BYTE)) {
            e.blockList().clear();
        }
    }

    /** Эскалация: чем дольше идёт игра, тем сильнее новые враждебные мобы. */
    @EventHandler(ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent e) {
        if (!game.mod(Modifier.ESCALATION) || !(e.getEntity() instanceof Monster m)) return;
        int minutes = game.elapsedMinutes();
        if (minutes <= 0) return;
        buff(m, Attribute.MAX_HEALTH, "esc_hp", Math.min(4.0, minutes * 0.08), Operation.ADD_SCALAR);
        buff(m, Attribute.ATTACK_DAMAGE, "esc_dmg", Math.min(2.0, minutes * 0.04), Operation.MULTIPLY_SCALAR_1);
        buff(m, Attribute.MOVEMENT_SPEED, "esc_spd", Math.min(0.5, minutes * 0.01), Operation.MULTIPLY_SCALAR_1);
        AttributeInstance hp = m.getAttribute(Attribute.MAX_HEALTH);
        if (hp != null) m.setHealth(hp.getValue());
    }

    private void buff(LivingEntity mob, Attribute attribute, String keyName, double amount, Operation op) {
        AttributeInstance inst = mob.getAttribute(attribute);
        if (inst == null) return;
        NamespacedKey key = new NamespacedKey(plugin, keyName);
        Util.removeMod(inst, key);
        inst.addTransientModifier(new AttributeModifier(key, amount, op, EquipmentSlotGroup.ANY));
    }

    /** Связанные судьбы: часть урона, полученного одним игроком, чувствуют все остальные. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onLinkedDamage(EntityDamageEvent e) {
        if (linking || !game.mod(Modifier.LINKED) || !(e.getEntity() instanceof Player victim)) return;
        double share = e.getFinalDamage() * 0.3;
        if (share < 0.5) return;
        linking = true;
        try {
            for (Player other : game.players()) {
                if (other.equals(victim)) continue;
                double dmg = Math.min(share, other.getHealth() - 1.0); // не добиваем: оставляем хотя бы полсердца
                if (dmg <= 0) continue;
                other.damage(dmg);
                Fx.sound(other, "entity.warden.heartbeat", 0.5f, 1.5f);
                other.sendActionBar(Fx.mm("<red>Ты чувствуешь боль <yellow>" + victim.getName()));
            }
        } finally {
            linking = false;
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        game.onJoin(e.getPlayer());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (game.isRunning()) game.modifiers().refresh(p);
        }, 2L);
    }
}
