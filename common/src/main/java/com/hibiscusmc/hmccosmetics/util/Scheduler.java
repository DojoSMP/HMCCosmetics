package com.hibiscusmc.hmccosmetics.util;

import com.github.Anon8281.universalScheduler.UniversalScheduler;
import com.github.Anon8281.universalScheduler.scheduling.schedulers.TaskScheduler;
import com.github.Anon8281.universalScheduler.scheduling.tasks.MyScheduledTask;
import com.hibiscusmc.hmccosmetics.HMCCosmeticsPlugin;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import me.lojosho.hibiscuscommons.HibiscusCommonsPlugin;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;


public final class Scheduler {
    private Scheduler() {}

    private static TaskScheduler scheduler() {
        // Commons remains enabled while its dependent plugin restores players during disable.
        return HMCCosmeticsPlugin.getInstance().isEnabled()
                ? HMCCosmeticsPlugin.getInstance().getTaskScheduler()
                : UniversalScheduler.getScheduler(HibiscusCommonsPlugin.getInstance());
    }

    public static boolean owns(Entity entity) {
        return entity != null && scheduler().isEntityThread(entity);
    }

    public static void run(Entity entity, Runnable action) {
        if (entity == null) return;
        if (owns(entity)) action.run();
        else scheduler().execute(entity, action);
    }

    public static void later(Entity entity, Runnable action, long ticks) {
        if (entity != null) scheduler().runTaskLater(entity, action, Math.max(1, ticks));
    }

    public static Runnable repeat(Entity entity, Consumer<Runnable> action, long delay, long period) {
        if (entity == null) return () -> {};
        AtomicReference<MyScheduledTask> task = new AtomicReference<>();
        AtomicBoolean cancelled = new AtomicBoolean();
        Runnable cancel = () -> {
            cancelled.set(true);
            MyScheduledTask scheduled = task.get();
            if (scheduled != null) {
                try {
                    scheduled.cancel();
                } catch (NullPointerException retired) {
                    // UniversalScheduler 0.1.7 wraps a null native task when the entity is already retired.
                    if (!UniversalScheduler.isExpandedSchedulingAvailable) throw retired;
                }
            }
        };
        task.set(scheduler().runTaskTimer(entity, () -> {
            if (!cancelled.get()) action.accept(cancel);
        }, Math.max(1, delay), Math.max(1, period)));
        // Another region may run the first tick before runTaskTimer returns.
        if (cancelled.get()) cancel.run();
        return cancel;
    }

    public static void global(Runnable action) {
        scheduler().execute(action);
    }

    public static void teleport(Player player, Location destination, Consumer<Boolean> completion) {
        player.teleportAsync(destination.clone(), PlayerTeleportEvent.TeleportCause.PLUGIN)
                .whenComplete((success, error) -> run(player, () -> {
                    if (error != null) HMCCosmeticsPlugin.getInstance().getLogger().warning("Wardrobe teleport failed: " + error);
                    completion.accept(error == null && Boolean.TRUE.equals(success));
                }));
    }
}
