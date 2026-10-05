package com.hibiscusmc.hmccosmetics.gui.action.actions;

import com.hibiscusmc.hmccosmetics.cosmetic.CosmeticHolder;
import com.hibiscusmc.hmccosmetics.gui.action.Action;
import com.hibiscusmc.hmccosmetics.user.CosmeticUser;
import com.hibiscusmc.hmccosmetics.util.MessagesUtil;
import com.hibiscusmc.hmccosmetics.util.Scheduler;
import me.lojosho.hibiscuscommons.hooks.Hooks;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class ActionPlayerCommand extends Action {

    public ActionPlayerCommand() {
        super("player-command");
    }

    @Override
    public void run(Player viewer, CosmeticHolder cosmeticHolder, String raw) {
        // todo: what if we wanted the cosmetic holder to execute the command instead
        String command = MessagesUtil.processStringNoKeyString(viewer, Hooks.processPlaceholders(viewer, raw));
        // Commands may open another inventory; dispatch after the inventory click finishes.
        Scheduler.later(viewer, () -> {
            if (viewer.isOnline()) viewer.performCommand(command);
        }, 1);
    }

    @Override
    public void run(@NotNull CosmeticUser user, String raw) {
        run(user.getPlayer(), user, raw);
    }
}