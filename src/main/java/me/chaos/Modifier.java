package me.chaos;

import org.bukkit.Material;

/** Модификаторы — правила, действующие всю игру. По умолчанию включены все. */
public enum Modifier {

    FAST("fast", "<red>★ Спринт", "Таймер вдвое короче — события идут вдвое чаще", Material.REPEATER),
    DOUBLE("double", "<light_purple>★ Двойной хаос", "Каждый раз срабатывают сразу два события подряд", Material.ENDER_EYE),
    LOW_GRAVITY("moon", "<aqua>★ Лунная гравитация", "Низкая гравитация — прыгаешь как на Луне", Material.FEATHER),
    NO_FALL("nofall", "<green>★ Мягкая посадка", "Урон от падения отключён", Material.SLIME_BALL),
    LUCKY("lucky", "<gold>★ Лакиблоки", "Из любого сломанного блока падает случайный предмет (иногда — ловушка!)", Material.GOLD_BLOCK),
    VAMPIRE("vampire", "<dark_red>★ Вампиры", "Ударил игрока или моба — вылечился на 30% нанесённого урона", Material.REDSTONE),
    GLASS("glass", "<white>★ Стеклянные пушки", "Всего 5 сердец, но урон ×2", Material.GLASS),
    TURBO("turbo", "<yellow>★ Турбо", "Скорость передвижения +40%", Material.SUGAR),
    GLOW("glow", "<yellow>★ Подсветка", "Все игроки светятся сквозь стены", Material.GLOWSTONE_DUST),
    BOOM("boom", "<red>★ Взрывные мобы", "Убитые мобы взрываются (блоки не ломаются)", Material.TNT),

    // --- глобальные модификаторы ---
    BORDER("border", "<dark_red>★ Сжимающийся мир", "Граница мира сужается всю игру, как в королевской битве", Material.IRON_BARS),
    NIGHT("night", "<dark_purple>★ Вечная ночь", "Всегда ночь и гроза — солнце не взойдёт", Material.BLACK_DYE),
    ESCALATION("escalate", "<dark_red>★ Эскалация", "С каждой минутой новые мобы сильнее, быстрее и злее", Material.ZOMBIE_HEAD),
    LINKED("link", "<light_purple>★ Связанные судьбы", "Один получил урон — остальные чувствуют 30% его боли", Material.LEAD),
    AIRDROP("airdrop", "<aqua>★ Воздушные припасы", "Каждые пару минут падает сундук с ценным лутом — координаты видят все", Material.CHEST),
    METEOR("meteor", "<gold>★ Метеоритный дождь", "С неба падают огненные шары (блоки не ломают, но бьют больно)", Material.FIRE_CHARGE);

    private final String id;
    private final String title;
    private final String description;
    private final Material icon;

    Modifier(String id, String title, String description, Material icon) {
        this.id = id;
        this.title = title;
        this.description = description;
        this.icon = icon;
    }

    public String id() { return id; }
    public String title() { return title; }
    public String description() { return description; }
    public Material icon() { return icon; }

    public static Modifier byId(String id) {
        for (Modifier m : values()) {
            if (m.id.equalsIgnoreCase(id)) return m;
        }
        return null;
    }
}
