package me.chaos;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Меню «Chaos Menu» (сундук 6×9). Всё в нём — кнопки: ничего нельзя забрать в инвентарь
 * (клики, перетаскивание и shift-клик отменяются в {@link MenuListener}).
 */
public final class ChaosMenu implements InventoryHolder {

    public enum Page { MAIN, EVENTS }

    // --- главная страница ---
    private static final int STATUS = 4;
    private static final int START = 10;
    private static final int STOP = 11;
    private static final int PAUSE = 12;
    private static final int SKIP = 13;
    private static final int INTERVAL = 14;
    private static final int EVENTS = 15;
    private static final int RELOAD = 16;
    private static final int ALL_ON = 20;
    private static final int RANDOM = 22;
    private static final int ALL_OFF = 24;
    private static final int CLOSE = 31;
    private static final int MOD_FIRST = 36; // модификаторы — внизу, слоты 36..53

    // --- страница событий ---
    private static final int BACK = 49;

    private static final Map<String, Material> EVENT_ICONS = Map.ofEntries(
            Map.entry("swap", Material.ENDER_PEARL), Map.entry("invswap", Material.HOPPER),
            Map.entry("levitation", Material.SHULKER_SHELL), Map.entry("launch", Material.PISTON),
            Map.entry("zombies", Material.ZOMBIE_HEAD), Map.entry("creepers", Material.CREEPER_HEAD),
            Map.entry("tnt", Material.TNT), Map.entry("animals", Material.BEEF),
            Map.entry("blind", Material.INK_SAC), Map.entry("loot", Material.GOLDEN_APPLE),
            Map.entry("hunger", Material.ROTTEN_FLESH), Map.entry("feast", Material.COOKED_PORKCHOP),
            Map.entry("lightning", Material.LIGHTNING_ROD), Map.entry("jumper", Material.RABBIT_FOOT),
            Map.entry("size", Material.PUFFERFISH), Map.entry("time", Material.CLOCK),
            Map.entry("freeze", Material.BLUE_ICE), Map.entry("target", Material.TARGET),
            Map.entry("mlg", Material.WATER_BUCKET), Map.entry("cobweb", Material.COBWEB),
            Map.entry("potions", Material.BREWING_STAND), Map.entry("healthswap", Material.APPLE),
            Map.entry("xp", Material.EXPERIENCE_BOTTLE), Map.entry("moon", Material.END_ROD),
            Map.entry("ghost", Material.GLASS_BOTTLE), Map.entry("fireworks", Material.FIREWORK_STAR));

    private final ChaosGame game;
    private final Page page;
    private final Inventory inv;

    public ChaosMenu(ChaosGame game, Page page) {
        this.game = game;
        this.page = page;
        Component title = page == Page.MAIN
                ? Component.text("Chaos Menu", NamedTextColor.DARK_GRAY)
                : Component.text("Chaos Menu » События", NamedTextColor.DARK_GRAY);
        this.inv = Bukkit.createInventory(this, 54, title);
    }

    @Override
    public Inventory getInventory() { return inv; }

    public Page page() { return page; }

    public void open(Player p) {
        render();
        p.openInventory(inv);
        Fx.sound(p, "block.chest.open", 0.6f, 1.2f);
    }

    // ------------------------------------------------------------ отрисовка

    private static ItemStack item(Material m, String name, boolean glow, String... lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Fx.mm(name).decoration(TextDecoration.ITALIC, false));
        if (lore.length > 0) {
            List<Component> lines = new ArrayList<>();
            for (String s : lore) lines.add(Fx.mm(s).decoration(TextDecoration.ITALIC, false));
            meta.lore(lines);
        }
        if (glow) meta.setEnchantmentGlintOverride(true);
        it.setItemMeta(meta);
        return it;
    }

    private static ItemStack pane() {
        ItemStack it = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Component.text(" "));
        it.setItemMeta(meta);
        return it;
    }

    public void render() {
        inv.clear();
        ItemStack pane = pane();
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, pane);
        if (page == Page.MAIN) renderMain();
        else renderEvents();
    }

    /** Обновляет только карточку статуса (чтобы таймер тикал, не мигая остальным меню). */
    public void refreshStatus() {
        if (page == Page.MAIN) inv.setItem(STATUS, statusItem());
    }

    private ItemStack statusItem() {
        int on = game.modifiers().active().size();
        int all = Modifier.values().length;
        List<String> lore = new ArrayList<>();
        if (!game.isRunning()) lore.add("<gray>Состояние: <red>игра остановлена");
        else if (game.isPaused()) lore.add("<gray>Состояние: <yellow>пауза");
        else lore.add("<gray>Состояние: <green>игра идёт");
        if (game.isRunning()) lore.add("<gray>До события: <white>" + ChaosGame.fmt(game.remaining()));
        lore.add("<gray>Интервал: <white>" + game.intervalSeconds() + " сек");
        lore.add("<gray>Модификаторов включено: <white>" + on + "/" + all);
        return item(Material.NETHER_STAR, "<gold><bold>Хаос-таймер", game.isRunning(), lore.toArray(new String[0]));
    }

    private void renderMain() {
        inv.setItem(STATUS, statusItem());

        inv.setItem(START, item(Material.EMERALD_BLOCK, "<green><bold>Старт", false,
                "<gray>Начать игру", game.isRunning() ? "<red>Игра уже идёт" : "<yellow>Нажми, чтобы начать"));
        inv.setItem(STOP, item(Material.REDSTONE_BLOCK, "<red><bold>Стоп", false, "<gray>Остановить игру"));
        inv.setItem(PAUSE, item(Material.CLOCK, "<yellow><bold>Пауза / продолжить", false, "<gray>Остановить таймер"));
        inv.setItem(SKIP, item(Material.FIREWORK_ROCKET, "<light_purple><bold>Событие сейчас", false,
                "<gray>Не ждать таймер — запустить", "<gray>случайное событие прямо сейчас"));
        inv.setItem(INTERVAL, item(Material.COMPARATOR, "<aqua><bold>Интервал: " + game.intervalSeconds() + " сек", false,
                "<gray>ЛКМ: <white>+30 сек", "<gray>ПКМ: <white>−30 сек", "<gray>Shift: <white>шаг 10 сек",
                "<dark_gray>Применится со следующего цикла"));
        inv.setItem(EVENTS, item(Material.BOOK, "<gold><bold>Запустить событие", false,
                "<gray>Открыть список всех событий", "<gray>и запустить любое из них"));
        inv.setItem(RELOAD, item(Material.WRITABLE_BOOK, "<gray><bold>Перезагрузить конфиг", false,
                "<gray>Заново прочитать config.yml"));

        inv.setItem(ALL_ON, item(Material.LIME_DYE, "<green><bold>Включить все модификаторы", false));
        inv.setItem(RANDOM, item(Material.AMETHYST_SHARD, "<light_purple><bold>Случайные модификаторы", false,
                "<gray>ЛКМ: оставить <white>3 <gray>случайных", "<gray>ПКМ: оставить <white>5 <gray>случайных",
                "<dark_gray>Остальные будут выключены"));
        inv.setItem(ALL_OFF, item(Material.RED_DYE, "<red><bold>Выключить все модификаторы", false));

        inv.setItem(CLOSE, item(Material.BARRIER, "<red><bold>Закрыть", false));

        Modifier[] mods = Modifier.values();
        for (int i = 0; i < mods.length && MOD_FIRST + i < inv.getSize(); i++) {
            Modifier m = mods[i];
            boolean on = game.modifiers().has(m);
            inv.setItem(MOD_FIRST + i, item(m.icon(),
                    (on ? "<green>[ВКЛ] " : "<red>[ВЫКЛ] ") + m.title(), on,
                    "<gray>" + m.description(),
                    "",
                    on ? "<green>Сейчас: включён" : "<red>Сейчас: выключен",
                    "<yellow>Нажми, чтобы " + (on ? "выключить" : "включить")));
        }
    }

    private static List<Integer> eventSlots() {
        List<Integer> slots = new ArrayList<>();
        for (int r = 1; r <= 4; r++) for (int c = 1; c <= 7; c++) slots.add(r * 9 + c);
        return slots;
    }

    private void renderEvents() {
        inv.setItem(STATUS, item(Material.NETHER_STAR, "<gold><bold>Запуск события", false,
                "<gray>Нажми на событие, чтобы", "<gray>запустить его прямо сейчас"));
        List<Integer> slots = eventSlots();
        List<ChaosEvent> events = game.registry().all();
        for (int i = 0; i < events.size() && i < slots.size(); i++) {
            ChaosEvent ev = events.get(i);
            inv.setItem(slots.get(i), item(EVENT_ICONS.getOrDefault(ev.id(), Material.PAPER), ev.name(), false,
                    "<gray>" + ev.description(), "", "<yellow>Нажми, чтобы запустить"));
        }
        inv.setItem(BACK, item(Material.ARROW, "<yellow><bold>Назад", false));
    }

    // ------------------------------------------------------------ клики

    private void msg(Player p, String text) {
        p.sendMessage(Fx.mm(text));
    }

    private void later(Runnable r) {
        Bukkit.getScheduler().runTask(game.plugin(), r);
    }

    public void click(Player p, int slot, ClickType click) {
        if (!p.hasPermission("chaos.admin")) {
            p.closeInventory();
            return;
        }
        Fx.sound(p, "ui.button.click", 0.6f, 1.2f);
        if (page == Page.EVENTS) clickEvents(p, slot);
        else clickMain(p, slot, click);
    }

    private void clickEvents(Player p, int slot) {
        if (slot == BACK) {
            later(() -> new ChaosMenu(game, Page.MAIN).open(p));
            return;
        }
        int idx = eventSlots().indexOf(slot);
        List<ChaosEvent> events = game.registry().all();
        if (idx < 0 || idx >= events.size()) return;
        ChaosEvent ev = events.get(idx);
        later(() -> {
            p.closeInventory();
            game.forceEvent(ev);
        });
    }

    private void clickMain(Player p, int slot, ClickType click) {
        switch (slot) {
            case START -> {
                if (!game.start(0)) msg(p, "<red>Игра уже идёт.");
            }
            case STOP -> {
                if (game.isRunning()) game.stop();
                else msg(p, "<red>Игра не запущена.");
            }
            case PAUSE -> {
                if (!game.togglePause()) msg(p, "<red>Игра не запущена.");
            }
            case SKIP -> {
                if (game.isRunning()) {
                    game.skip();
                    later(p::closeInventory);
                } else {
                    msg(p, "<red>Игра не запущена. Событие можно запустить через «Запустить событие».");
                }
            }
            case INTERVAL -> {
                int step = click.isShiftClick() ? 10 : 30;
                if (click.isRightClick()) step = -step;
                game.changeInterval(step);
            }
            case EVENTS -> {
                later(() -> new ChaosMenu(game, Page.EVENTS).open(p));
                return;
            }
            case RELOAD -> {
                game.plugin().reloadConfig();
                game.resetInterval();
                msg(p, "<green>Конфиг перезагружен.");
            }
            case ALL_ON -> game.setAllModifiers(true);
            case ALL_OFF -> game.setAllModifiers(false);
            case RANDOM -> game.randomModifiers(click.isRightClick() ? 5 : 3);
            case CLOSE -> {
                later(p::closeInventory);
                return;
            }
            default -> {
                Modifier[] mods = Modifier.values();
                int idx = slot - MOD_FIRST;
                if (idx >= 0 && idx < mods.length) {
                    Modifier m = mods[idx];
                    boolean on = !game.modifiers().has(m);
                    game.setModifier(m, on, true);
                    Fx.sound(p, "block.note_block.pling", 0.8f, on ? 1.6f : 0.7f);
                } else {
                    return;
                }
            }
        }
        render();
    }
}
