package me.chaos;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ChaosCommand implements CommandExecutor, TabCompleter {

    private static final List<String> ROOT = List.of(
            "menu", "start", "stop", "pause", "skip", "time", "interval", "event", "events", "modifier", "reload", "help");
    private static final List<String> MOD_SUB = List.of("list", "on", "off", "all", "none", "random");

    private final ChaosPlugin plugin;
    private final ChaosGame game;

    public ChaosCommand(ChaosPlugin plugin, ChaosGame game) {
        this.plugin = plugin;
        this.game = game;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (a.length == 0) {
            help(s);
            return true;
        }
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "menu" -> {
                if (s instanceof Player p) new ChaosMenu(game, ChaosMenu.Page.MAIN).open(p);
                else s.sendMessage(Fx.mm("<red>Меню можно открыть только в игре."));
            }
            case "start" -> {
                int sec = a.length > 1 ? num(a[1]) : 0;
                if (a.length > 1 && sec < 5) {
                    s.sendMessage(Fx.mm("<red>Интервал — число секунд, минимум 5."));
                    return true;
                }
                if (!game.start(sec)) s.sendMessage(Fx.mm("<red>Игра уже идёт."));
            }
            case "stop" -> {
                if (!game.isRunning()) s.sendMessage(Fx.mm("<red>Игра не запущена."));
                else game.stop();
            }
            case "pause" -> {
                if (!game.togglePause()) s.sendMessage(Fx.mm("<red>Игра не запущена."));
            }
            case "skip" -> {
                if (!game.skip()) s.sendMessage(Fx.mm("<red>Игра не запущена."));
            }
            case "time" -> {
                int sec = a.length > 1 ? num(a[1]) : -1;
                if (!game.isRunning() || sec < 1) s.sendMessage(Fx.mm("<red>/chaos time [секунды] — только во время игры."));
                else game.setRemaining(sec);
            }
            case "interval" -> {
                int sec = a.length > 1 ? num(a[1]) : -1;
                if (sec < 5) s.sendMessage(Fx.mm("<red>/chaos interval [секунды, минимум 5]"));
                else {
                    game.setInterval(sec);
                    s.sendMessage(Fx.mm("<green>Интервал: <white>" + sec + " сек <gray>(применится со следующего цикла)"));
                }
            }
            case "event" -> {
                ChaosEvent ev = a.length > 1 ? game.registry().byId(a[1]) : null;
                if (ev == null) s.sendMessage(Fx.mm("<red>/chaos event [id] — список: /chaos events"));
                else game.forceEvent(ev);
            }
            case "events" -> {
                s.sendMessage(Fx.mm("<gold>События (" + game.registry().all().size() + "):"));
                for (ChaosEvent e : game.registry().all()) {
                    s.sendMessage(Fx.mm("<dark_gray>[<yellow>" + e.id() + "<dark_gray>] " + e.name() + " <dark_gray>— <gray>" + e.description()));
                }
            }
            case "modifier", "mod" -> modifierCmd(s, a);
            case "reload" -> {
                plugin.reloadConfig();
                game.resetInterval();
                s.sendMessage(Fx.mm("<green>Конфиг перезагружен."));
            }
            default -> help(s);
        }
        return true;
    }

    private void modifierCmd(CommandSender s, String[] a) {
        String sub = a.length > 1 ? a[1].toLowerCase(Locale.ROOT) : "list";
        switch (sub) {
            case "list" -> {
                s.sendMessage(Fx.mm("<gold>Модификаторы <gray>(включено " + game.modifiers().active().size()
                        + " из " + Modifier.values().length + "):"));
                for (Modifier m : Modifier.values()) {
                    s.sendMessage(Fx.mm((game.modifiers().has(m) ? "<green>✔ " : "<gray>✖ ")
                            + m.title() + " <dark_gray>[" + m.id() + "] <gray>" + m.description()));
                }
            }
            case "on", "add", "off", "remove" -> {
                Modifier m = a.length > 2 ? Modifier.byId(a[2]) : null;
                if (m == null) {
                    s.sendMessage(Fx.mm("<red>Неизвестный модификатор. Список: /chaos modifier list"));
                    return;
                }
                boolean on = sub.equals("on") || sub.equals("add");
                if (!game.setModifier(m, on, true)) {
                    s.sendMessage(Fx.mm("<gray>Он уже " + (on ? "включён." : "выключен.")));
                }
            }
            case "all" -> game.setAllModifiers(true);
            case "none", "clear" -> game.setAllModifiers(false);
            case "random" -> game.randomModifiers(a.length > 2 ? Math.max(1, num(a[2])) : 3);
            default -> s.sendMessage(Fx.mm("<red>/chaos modifier [list|on|off|all|none|random] [id/число]"));
        }
    }

    private void help(CommandSender s) {
        s.sendMessage(Fx.mm("<gold><bold>Хаос-таймер</bold> <gray>— команды:"));
        String[] lines = {
                "/chaos menu <gray>— меню управления (сундук)",
                "/chaos start [секунды] <gray>— начать игру",
                "/chaos stop <gray>— остановить",
                "/chaos pause <gray>— пауза / продолжить",
                "/chaos skip <gray>— сразу запустить событие",
                "/chaos time [сек] <gray>— поставить оставшееся время",
                "/chaos interval [сек] <gray>— интервал между событиями",
                "/chaos event [id] <gray>— запустить конкретное событие",
                "/chaos events <gray>— список событий",
                "/chaos modifier list <gray>— все модификаторы (по умолчанию включены все)",
                "/chaos modifier on|off [id] <gray>— включить / выключить один",
                "/chaos modifier all|none|random [число] <gray>— все / никакие / случайные",
                "/chaos reload <gray>— перезагрузить конфиг"
        };
        for (String l : lines) s.sendMessage(Fx.mm("<yellow>" + l));
    }

    private static int num(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String alias, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            out.addAll(ROOT);
        } else if (a.length == 2) {
            switch (a[0].toLowerCase(Locale.ROOT)) {
                case "event" -> game.registry().all().forEach(e -> out.add(e.id()));
                case "modifier", "mod" -> out.addAll(MOD_SUB);
                default -> { }
            }
        } else if (a.length == 3 && a[0].toLowerCase(Locale.ROOT).startsWith("mod")) {
            String sub = a[1].toLowerCase(Locale.ROOT);
            if (sub.equals("on") || sub.equals("off") || sub.equals("add") || sub.equals("remove")) {
                for (Modifier m : Modifier.values()) out.add(m.id());
            }
        }
        String prefix = a[a.length - 1].toLowerCase(Locale.ROOT);
        out.removeIf(x -> !x.toLowerCase(Locale.ROOT).startsWith(prefix));
        return out;
    }
}
