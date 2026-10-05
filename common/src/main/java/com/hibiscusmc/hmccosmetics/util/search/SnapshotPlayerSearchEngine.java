package com.hibiscusmc.hmccosmetics.util.search;

import com.hibiscusmc.hmccosmetics.HMCCosmeticsPlugin;
import com.hibiscusmc.hmccosmetics.util.Scheduler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.*;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Positions are published by each player's owning region; queries never read foreign entities. */
public final class SnapshotPlayerSearchEngine extends PlayerSearchEngine {
    private final ConcurrentHashMap<UUID, Position> positions = new ConcurrentHashMap<>();

    public SnapshotPlayerSearchEngine(HMCCosmeticsPlugin plugin) {
        super(plugin);
        for (Player player : Bukkit.getOnlinePlayers()) Scheduler.run(player, () -> update(player, player.getLocation()));
    }

    private void update(Player player, Location location) {
        positions.put(player.getUniqueId(), new Position(player, location.getWorld().getUID(),
                location.getX(), location.getY(), location.getZ()));
    }

    @Override
    public List<Player> getPlayersInRange(Location location, double range) {
        UUID world = location.getWorld().getUID();
        return positions.values().stream()
                .filter(p -> p.world.equals(world) && Math.abs(p.x - location.getX()) <= range
                        && Math.abs(p.y - location.getY()) <= range && Math.abs(p.z - location.getZ()) <= range)
                .map(Position::player).toList();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) { update(event.getPlayer(), event.getTo()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) { update(event.getPlayer(), event.getTo()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) { update(event.getPlayer(), event.getPlayer().getLocation()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) { update(event.getPlayer(), event.getPlayer().getLocation()); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) { positions.remove(event.getPlayer().getUniqueId()); }

    private record Position(Player player, UUID world, double x, double y, double z) {}
}
