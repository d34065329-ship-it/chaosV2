package me.chaos;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

/** Защита меню: забрать, перетащить или переложить предметы нельзя ни одним способом. */
public final class MenuListener implements Listener {

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof ChaosMenu menu)) return;
        e.setCancelled(true); // отменяем ВСЕ клики: shift-клик, цифры, F, Q, двойной клик и т.д.
        if (!(e.getWhoClicked() instanceof Player p)) return;
        int raw = e.getRawSlot();
        if (raw < 0 || raw >= e.getInventory().getSize()) return; // клик не по меню
        menu.click(p, raw, e.getClick());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCreative(InventoryCreativeEvent e) {
        if (e.getInventory().getHolder() instanceof ChaosMenu) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof ChaosMenu) e.setCancelled(true);
    }
}
