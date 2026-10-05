package com.hibiscusmc.hmccosmetics.command;

import com.hibiscusmc.hmccolor.HMCColorConfig;
import com.hibiscusmc.hmccolor.HMCColorContextKt;
import com.hibiscusmc.hmccosmetics.HMCCosmeticsPlugin;
import com.hibiscusmc.hmccosmetics.config.Settings;
import com.hibiscusmc.hmccosmetics.config.section.Wardrobe;
import com.hibiscusmc.hmccosmetics.config.section.WardrobeLocation;
import com.hibiscusmc.hmccosmetics.config.WardrobeSettings;
import com.hibiscusmc.hmccosmetics.cosmetic.Cosmetic;
import com.hibiscusmc.hmccosmetics.cosmetic.CosmeticSlot;
import com.hibiscusmc.hmccosmetics.cosmetic.Cosmetics;
import com.hibiscusmc.hmccosmetics.database.Database;
import com.hibiscusmc.hmccosmetics.gui.Menu;
import com.hibiscusmc.hmccosmetics.gui.Menus;
import com.hibiscusmc.hmccosmetics.gui.special.DyeMenuProvider;
import com.hibiscusmc.hmccosmetics.user.CosmeticUser;
import com.hibiscusmc.hmccosmetics.user.CosmeticUsers;
import com.hibiscusmc.hmccosmetics.util.MessagesUtil;
import com.hibiscusmc.hmccosmetics.util.HMCCServerUtils;
import me.lojosho.hibiscuscommons.HibiscusCommonsPlugin;
import me.lojosho.hibiscuscommons.hooks.Hooks;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;
import com.hibiscusmc.hmccosmetics.util.Scheduler;

public class CosmeticCommand implements CommandExecutor {

    public record SubCommandInfo(String name, String permission, String usage, String description) {}

    public static final List<SubCommandInfo> SUBCOMMANDS = List.of(
            new SubCommandInfo("apply", "hmccosmetics.cmd.apply", "/cosmetics apply <cosmetic> [player] [color]", "Equip a cosmetic"),
            new SubCommandInfo("unapply", "hmccosmetics.cmd.unapply", "/cosmetics unapply <slot|all> [player]", "Unequip a cosmetic slot"),
            new SubCommandInfo("menu", "hmccosmetics.cmd.menu", "/cosmetics menu [menu] [player]", "Open a cosmetic menu"),
            new SubCommandInfo("wardrobes", "hmccosmetics.cmd.wardrobe", "/cosmetics wardrobes <wardrobe> [player]", "Enter or leave a wardrobe"),
            new SubCommandInfo("dye", "hmccosmetics.cmd.dye", "/cosmetics dye <slot> [color]", "Dye an equipped cosmetic"),
            new SubCommandInfo("hide", "hmccosmetics.cmd.hide", "/cosmetics hide [player]", "Hide a player's cosmetics"),
            new SubCommandInfo("show", "hmccosmetics.cmd.show", "/cosmetics show [player]", "Show a player's cosmetics"),
            new SubCommandInfo("toggle", "hmccosmetics.cmd.toggle", "/cosmetics toggle [player]", "Toggle cosmetic visibility"),
            new SubCommandInfo("reload", "hmccosmetics.cmd.reload", "/cosmetics reload", "Reload config files"),
            new SubCommandInfo("dataclear", "hmccosmetics.cmd.dataclear", "/cosmetics dataclear <player>", "Clear a player's saved cosmetics"),
            new SubCommandInfo("setwardrobesetting", "hmccosmetics.cmd.setwardrobesetting", "/cosmetics setwardrobesetting <wardrobe> <setting> [value]", "Change a wardrobe setting"),
            new SubCommandInfo("disableall", "hmccosmetics.cmd.disableall", "/cosmetics disableall <true|false>", "Hide or show cosmetics for everyone"),
            new SubCommandInfo("hiddenreasons", "hmccosmetics.cmd.hiddenreasons", "/cosmetics hiddenreasons [player]", "List why a player's cosmetics are hidden"),
            new SubCommandInfo("clearhiddenreasons", "hmccosmetics.cmd.clearhiddenreasons", "/cosmetics clearhiddenreasons [player]", "Clear a player's hidden reasons"),
            new SubCommandInfo("debug", "hmccosmetics.cmd.debug", "/cosmetics debug", "Toggle debug mode"),
            new SubCommandInfo("dump", "hmccosmetics.cmd.dump", "/cosmetics dump", "Dump cosmetic debug info")
    );

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        boolean silent = false;
        boolean console = !(sender instanceof Player);

        if (hasPermission(sender, "hmccosmetics.cmd.silent")) {
            for (String singleArg : args) {
                if (singleArg.equalsIgnoreCase("-s")) {
                    silent = true;
                    args = Arrays.stream(args).filter(arg -> !arg.equalsIgnoreCase("-s")).toArray(String[]::new);
                    break;
                }
            }
        }

        if (args.length == 0) {
            if (console) {
                sendHelp(sender);
                return true;
            }
            if (!sender.hasPermission("hmccosmetics.cmd.default")) {
                MessagesUtil.sendMessage(sender, "no-permission");
                return true;
            }

            CosmeticUser user = CosmeticUsers.getUser(((Player) sender).getUniqueId());
            Menu menu = Menus.getDefaultMenu();

            if (user == null) {
                MessagesUtil.sendMessage(sender, "invalid-player");
                return true;
            }

            if (menu == null) {
                MessagesUtil.sendMessage(sender, "invalid-menu");
                return true;
            }

            menu.openMenu(user);
            return true;
        }

        Player player = sender instanceof Player ? (Player) sender : null;

        final boolean quiet = silent;
        final String[] commandArgs = args;
        switch (commandArgs[0].toLowerCase()) {
            case "help" -> sendHelp(sender);
            case "reload" -> {
                if (!hasPermission(sender, "hmccosmetics.cmd.reload")) {
                    if (!quiet) MessagesUtil.sendMessage(sender, "no-permission");
                    return true;
                }
                Scheduler.global(() -> {
                    HMCCosmeticsPlugin.setup();
                    if (!quiet) MessagesUtil.sendMessage(sender, "reloaded");
                });
                return true;
            }
            case "apply" -> {
                if (!sender.hasPermission("hmccosmetics.cmd.apply")) {
                    if (!quiet) MessagesUtil.sendMessage(sender, "no-permission");
                    return true;
                }
                if (commandArgs.length == 1) {
                    if (!quiet) MessagesUtil.sendMessage(sender, "not-enough-args");
                    return true;
                }

                Cosmetic cosmetic = Cosmetics.getCosmetic(commandArgs[1]);
                if (cosmetic == null) {
                    if (!quiet) MessagesUtil.sendMessage(sender, "invalid-cosmetic");
                    return true;
                }

                Player target = resolveTarget(sender, commandArgs, 2, "hmccosmetics.cmd.apply.other", quiet);
                if (target == null) return true;

                CosmeticUser user = getUser(sender, target, quiet);
                if (user == null) return true;
                boolean canColor = sender.hasPermission("hmccosmetics.cmd.apply.color");
                return runTarget(target, () -> {

                    Color color = null;
                    if (commandArgs.length >= 4 && canColor) {
                        // TODO: Add sub-color support somehow... (and make this neater)
                        String textColor = commandArgs[3];
                        if (!textColor.contains("#") && Hooks.isActiveHook("HMCColor")) {
                            HMCColorConfig.Colors colors = HMCColorContextKt.getHmcColor().getConfig().getColors().get(textColor);
                            if (colors != null) {
                                color = colors.getBaseColor().getColor();
                            }
                        } else {
                            color = HMCCServerUtils.hex2Rgb(textColor);
                        }
                    }

                    if (sender == target && !user.canEquipCosmetic(cosmetic)) {
                        if (!quiet) MessagesUtil.sendMessage(sender, "no-cosmetic-permission");
                        return true;
                    }

                    TagResolver placeholders = cosmeticPlaceholders(cosmetic, cosmetic.getSlot(), target);

                    if (!quiet) {
                        MessagesUtil.sendMessage(target, "equip-cosmetic", placeholders);
                        if (sender != target) MessagesUtil.sendMessage(sender, "equip-cosmetic-other", placeholders);
                    }

                    user.addCosmetic(cosmetic, color);
                    user.updateCosmetic(cosmetic.getSlot());
                    return true;
                });
            }
            case "unapply" -> {
                if (!sender.hasPermission("hmccosmetics.cmd.unapply")) {
                    if (!quiet) MessagesUtil.sendMessage(sender, "no-permission");
                    return true;
                }
                if (commandArgs.length == 1) {
                    if (!quiet) MessagesUtil.sendMessage(sender, "not-enough-args");
                    return true;
                }

                Player target = resolveTarget(sender, commandArgs, 2, "hmccosmetics.cmd.unapply.other", quiet);
                if (target == null) return true;

                CosmeticUser user = getUser(sender, target, quiet);
                if (user == null) return true;
                return runTarget(target, () -> {

                    Set<CosmeticSlot> cosmeticSlots;

                    if (commandArgs[1].equalsIgnoreCase("all")) {
                        cosmeticSlots = user.getSlotsWithCosmetics();
                    } else {
                        String rawSlot = commandArgs[1].toUpperCase();
                        if (!CosmeticSlot.contains(rawSlot)) {
                            if (!quiet) MessagesUtil.sendMessage(sender, "invalid-slot");
                            return true;
                        }
                        cosmeticSlots = Set.of(CosmeticSlot.valueOf(rawSlot));
                    }

                    for (CosmeticSlot cosmeticSlot : cosmeticSlots) {
                        final Cosmetic cosmetic = user.getCosmetic(cosmeticSlot);
                        if (cosmetic == null) {
                            if (!quiet) MessagesUtil.sendMessage(sender, "no-cosmetic-slot");
                            continue;
                        }

                        TagResolver placeholders = cosmeticPlaceholders(cosmetic, cosmeticSlot, target);

                        if (!quiet) {
                            MessagesUtil.sendMessage(target, "unequip-cosmetic", placeholders);
                            if (sender != target) MessagesUtil.sendMessage(sender, "unequip-cosmetic-other", placeholders);
                        }

                        user.removeCosmeticSlot(cosmeticSlot);
                        user.updateCosmetic(cosmeticSlot);
                    }
                    return true;
                });
            }
            case "wardrobes" -> {
                if (!sender.hasPermission("hmccosmetics.cmd.wardrobe")) {
                    if (!quiet) MessagesUtil.sendMessage(sender, "no-permission");
                    return true;
                }
                if (commandArgs.length == 1) {
                    if (!quiet) MessagesUtil.sendMessage(sender, "not-enough-args");
                    return true;
                }

                Player target = resolveTarget(sender, commandArgs, 2, "hmccosmetics.cmd.wardrobe.other", quiet);
                if (target == null) return true;

                if (!WardrobeSettings.getWardrobeNames().contains(commandArgs[1])) {
                    if (!quiet) MessagesUtil.sendMessage(sender, "no-wardrobes");
                    return true;
                }
                Wardrobe wardrobe = WardrobeSettings.getWardrobe(commandArgs[1]);

                CosmeticUser user = getUser(sender, target, quiet);
                if (user == null) return true;
                return runTarget(target, () -> {

                    boolean forced = sender != target;
                    if (user.isInWardrobe()) {
                        user.leaveWardrobe(false);
                    } else {
                        user.enterWardrobe(wardrobe, forced, forced);
                    }
                    return true;
                });
            }
            // cosmetic menu exampleMenu playerName
            case "menu" -> {
                if (!sender.hasPermission("hmccosmetics.cmd.menu")) {
                    if (!quiet) MessagesUtil.sendMessage(sender, "no-permission");
                    return true;
                }
                Menu menu;
                if (commandArgs.length == 1) {
                    menu = Menus.getDefaultMenu();
                } else {
                    menu = Menus.getMenu(commandArgs[1]);
                }

                Player target = resolveTarget(sender, commandArgs, 2, "hmccosmetics.cmd.menu.other", quiet);
                if (target == null) return true;

                CosmeticUser user = getUser(sender, target, quiet);
                if (user == null) return true;
                return runTarget(target, () -> {

                    if (menu == null) {
                        if (!quiet) MessagesUtil.sendMessage(sender, "invalid-menu");
                        return true;
                    }

                    menu.openMenu(user, sender != target);
                    return true;
                });
            }
            case "dataclear" -> {
                if (!hasPermission(sender, "hmccosmetics.cmd.dataclear")) {
                    if (!quiet) MessagesUtil.sendMessage(sender, "no-permission");
                    return true;
                }
                if (commandArgs.length == 1) {
                    if (!quiet) MessagesUtil.sendMessage(sender, "not-enough-args");
                    return true;
                }
                OfflinePlayer selectedPlayer = Bukkit.getOfflinePlayer(commandArgs[1]);
                Database.clearData(selectedPlayer.getUniqueId());
                if (!quiet) MessagesUtil.sendMessage(sender, "cleared-data",
                        TagResolver.resolver(Placeholder.parsed("player", String.valueOf(selectedPlayer.getName()))));
                return true;
            }
            case "dye" -> {
                if (!hasPermission(sender, "hmccosmetics.cmd.dye")) {
                    if (!quiet) MessagesUtil.sendMessage(sender, "no-permission");
                    return true;
                }
                if (player == null) {
                    if (!quiet) MessagesUtil.sendMessage(sender, "player-only");
                    return true;
                }
                CosmeticUser user = getUser(sender, player, quiet);
                if (user == null) return true;

                if (commandArgs.length == 1) {
                    if (!quiet) MessagesUtil.sendMessage(sender, "not-enough-args");
                    return true;
                }

                final String rawSlot = commandArgs[1];
                if (!CosmeticSlot.contains(rawSlot)) {
                    if (!quiet) MessagesUtil.sendMessage(sender, "invalid-slot");
                    return true;
                }
                final CosmeticSlot slot = CosmeticSlot.valueOf(rawSlot); // This is checked above. While IDEs may say the slot might be null, it will not be.
                final Cosmetic cosmetic = user.getCosmetic(slot);
                if (cosmetic == null) {
                    if (!quiet) MessagesUtil.sendMessage(sender, "invalid-slot");
                    return true;
                }

                if (commandArgs.length >= 3) {
                    if (commandArgs[2].isEmpty()) {
                        if (!quiet) MessagesUtil.sendMessage(sender, "invalid-color");
                        return true;
                    }
                    Color color = HMCCServerUtils.hex2Rgb(commandArgs[2]);
                    if (color == null) {
                        if (!quiet) MessagesUtil.sendMessage(sender, "invalid-color");
                        return true;
                    }
                    user.addCosmetic(cosmetic, color); // #FFFFFF
                } else {
                    if (DyeMenuProvider.canOpenDyeMenu()) {
                        DyeMenuProvider.openMenu(player, user, cosmetic);
                    } else {
                        if (!quiet) MessagesUtil.sendMessage(sender, "invalid-color");
                    }
                }
                return true;
            }
            case "setwardrobesetting" -> {
                if (!sender.hasPermission("hmccosmetics.cmd.setwardrobesetting")) {
                    if (!quiet) MessagesUtil.sendMessage(sender, "no-permission");
                    return true;
                }
                if (player == null) {
                    if (!quiet) MessagesUtil.sendMessage(sender, "player-only");
                    return true;
                }

                if (commandArgs.length < 3) {
                    if (!quiet) MessagesUtil.sendMessage(sender, "not-enough-args");
                    return true;
                }
                String wardrobeId = commandArgs[1];
                Wardrobe wardrobe = WardrobeSettings.getWardrobe(wardrobeId);
                if (wardrobe == null) {
                    wardrobe = new Wardrobe(wardrobeId, new WardrobeLocation(null, null, null), null, -1, null, WardrobeSettings.getWardrobeDefaultFile());
                    WardrobeSettings.addWardrobe(wardrobe);
                }

                switch (commandArgs[2].toLowerCase()) {
                    case "npclocation" -> {
                        WardrobeSettings.setNPCLocation(wardrobe, player.getLocation());
                        if (!quiet) MessagesUtil.sendMessage(player, "set-wardrobe-location");
                    }
                    case "viewerlocation" -> {
                        WardrobeSettings.setViewerLocation(wardrobe, player.getEyeLocation());
                        if (!quiet) MessagesUtil.sendMessage(player, "set-wardrobe-viewing");
                    }
                    case "leavelocation" -> {
                        WardrobeSettings.setLeaveLocation(wardrobe, player.getLocation());
                        if (!quiet) MessagesUtil.sendMessage(player, "set-wardrobe-leaving");
                    }
                    case "permission", "distance", "defaultmenu" -> {
                        if (commandArgs.length < 4) {
                            if (!quiet) MessagesUtil.sendMessage(sender, "not-enough-args");
                            return true;
                        }
                        switch (commandArgs[2].toLowerCase()) {
                            case "permission" -> {
                                WardrobeSettings.setWardrobePermission(wardrobe, commandArgs[3]);
                                if (!quiet) MessagesUtil.sendMessage(player, "set-wardrobe-permission");
                            }
                            case "distance" -> {
                                int distance;
                                try {
                                    distance = Integer.parseInt(commandArgs[3]);
                                } catch (NumberFormatException e) {
                                    if (!quiet) MessagesUtil.sendMessage(sender, "invalid-args");
                                    return true;
                                }
                                WardrobeSettings.setWardrobeDistance(wardrobe, distance);
                                if (!quiet) MessagesUtil.sendMessage(player, "set-wardrobe-distance");
                            }
                            case "defaultmenu" -> {
                                WardrobeSettings.setWardrobeDefaultMenu(wardrobe, commandArgs[3]);
                                if (!quiet) MessagesUtil.sendMessage(player, "set-wardrobe-menu");
                            }
                        }
                    }
                    default -> {
                        if (!quiet) MessagesUtil.sendMessage(sender, "invalid-args");
                    }
                }
                return true;
            }
            case "dump" -> {
                if (!hasPermission(sender, "hmccosmetics.cmd.dump")) {
                    if (!quiet) MessagesUtil.sendMessage(sender, "no-permission");
                    return true;
                }
                if (player == null) {
                    if (!quiet) MessagesUtil.sendMessage(sender, "player-only");
                    return true;
                }
                CosmeticUser user = getUser(sender, player, quiet);
                if (user == null) return true;

                player.sendMessage("Passengers -> " + player.getPassengers());
                if (user.getUserBackpackManager() != null) {
                    player.sendMessage("Backpack Location -> " + user.getUserBackpackManager().getEntityManager().getLocation());
                    player.sendMessage("Cosmetic Passengers -> " + user.getUserBackpackManager().getAreaEffectEntityId());
                }

                player.sendMessage("Cosmetics -> " + user.getCosmetics());
                player.sendMessage("EntityId -> " + player.getEntityId());
                return true;
            }
            case "hide" -> {
                if (!sender.hasPermission("hmccosmetics.cmd.hide")) {
                    if (!quiet) MessagesUtil.sendMessage(sender, "no-permission");
                    return true;
                }
                Player target = resolveTarget(sender, commandArgs, 1, "hmccosmetics.cmd.hide.other", quiet);
                if (target == null) return true;

                CosmeticUser user = getUser(sender, target, quiet);
                if (user == null) return true;
                return runTarget(target, () -> {

                    if (!quiet) MessagesUtil.sendMessage(sender, "hide-cosmetic");
                    user.hideCosmetics(CosmeticUser.HiddenReason.COMMAND);
                    return true;
                });
            }
            case "show" -> {
                if (!sender.hasPermission("hmccosmetics.cmd.show")) {
                    if (!quiet) MessagesUtil.sendMessage(sender, "no-permission");
                    return true;
                }
                Player target = resolveTarget(sender, commandArgs, 1, "hmccosmetics.cmd.show.other", quiet);
                if (target == null) return true;

                CosmeticUser user = getUser(sender, target, quiet);
                if (user == null) return true;
                return runTarget(target, () -> {

                    if (!quiet) MessagesUtil.sendMessage(sender, "show-cosmetic");
                    user.showCosmetics(CosmeticUser.HiddenReason.COMMAND);
                    return true;
                });
            }
            case "toggle" -> {
                if (!sender.hasPermission("hmccosmetics.cmd.toggle")) {
                    if (!quiet) MessagesUtil.sendMessage(sender, "no-permission");
                    return true;
                }
                Player target = resolveTarget(sender, commandArgs, 1, "hmccosmetics.cmd.toggle.other", quiet);
                if (target == null) return true;

                CosmeticUser user = getUser(sender, target, quiet);
                if (user == null) return true;
                return runTarget(target, () -> {

                    if (user.isHidden(CosmeticUser.HiddenReason.COMMAND)) {
                        if (!quiet) MessagesUtil.sendMessage(sender, "show-cosmetic");
                        user.showCosmetics(CosmeticUser.HiddenReason.COMMAND);
                    } else {
                        if (!quiet) MessagesUtil.sendMessage(sender, "hide-cosmetic");
                        user.hideCosmetics(CosmeticUser.HiddenReason.COMMAND);
                    }
                    return true;
                });
            }
            case "debug" -> {
                if (!sender.hasPermission("hmccosmetics.cmd.debug")) {
                    if (!quiet) MessagesUtil.sendMessage(sender, "no-permission");
                    return true;
                }

                if (Settings.isDebugMode()) {
                    Settings.setDebugMode(false);
                    if (!quiet) MessagesUtil.sendMessage(sender, "debug-disabled");
                } else {
                    Settings.setDebugMode(true);
                    if (!quiet) MessagesUtil.sendMessage(sender, "debug-enabled");
                }
                return true;
            }
            case "disableall" -> {
                if (!sender.hasPermission("hmccosmetics.cmd.disableall")) {
                    if (!quiet) MessagesUtil.sendMessage(sender, "no-permission");
                    return true;
                }
                if (commandArgs.length == 1) {
                    if (!quiet) MessagesUtil.sendMessage(sender, "not-enough-args");
                    return true;
                }
                if (commandArgs[1].equalsIgnoreCase("true")) {
                    Settings.setAllPlayersHidden(true);
                    for (CosmeticUser user : CosmeticUsers.values()) user.hideCosmetics(CosmeticUser.HiddenReason.DISABLED);
                    if (!quiet) MessagesUtil.sendMessage(sender, "disabled-all");
                } else if (commandArgs[1].equalsIgnoreCase("false")) {
                    Settings.setAllPlayersHidden(false);
                    for (CosmeticUser user : CosmeticUsers.values()) user.showCosmetics(CosmeticUser.HiddenReason.DISABLED);
                    if (!quiet) MessagesUtil.sendMessage(sender, "enabled-all");
                } else {
                    if (!quiet) MessagesUtil.sendMessage(sender, "invalid-args");
                }
                return true;
            }
            case "hiddenreasons" -> {
                if (!sender.hasPermission("hmccosmetics.cmd.hiddenreasons")) {
                    if (!quiet) MessagesUtil.sendMessage(sender, "no-permission");
                    return true;
                }
                Player target = resolveTarget(sender, commandArgs, 1, "hmccosmetics.cmd.hiddenreasons.other", quiet);
                if (target == null) return true;

                CosmeticUser user = getUser(sender, target, quiet);
                if (user == null) return true;
                return runTarget(target, () -> {

                    String reasons = user.getHiddenReasons().toString();
                    if (sender instanceof Player recipient) Scheduler.run(recipient, () -> recipient.sendMessage(reasons));
                    else sender.sendMessage(reasons);
                    return true;
                });
            }
            case "clearhiddenreasons" -> {
                if (!sender.hasPermission("hmccosmetics.cmd.clearhiddenreasons")) {
                    if (!quiet) MessagesUtil.sendMessage(sender, "no-permission");
                    return true;
                }
                Player target = resolveTarget(sender, commandArgs, 1, "hmccosmetics.cmd.clearhiddenreasons.other", quiet);
                if (target == null) return true;

                CosmeticUser user = getUser(sender, target, quiet);
                if (user == null) return true;
                return runTarget(target, () -> {

                    user.clearHiddenReasons();
                    if (!quiet) MessagesUtil.sendMessage(sender, "cleared-hidden-reasons",
                            TagResolver.resolver(Placeholder.parsed("player", target.getName())));
                    return true;
                });
            }
            default -> {
                if (!quiet) MessagesUtil.sendMessage(sender, "unknown-command");
                sendHelp(sender);
            }
        }
        return true;
    }

    private static boolean runTarget(Player target, BooleanSupplier action) {
        Scheduler.run(target, () -> { if (target.isOnline()) action.getAsBoolean(); });
        return true;
    }

    private static void sendHelp(@NotNull CommandSender sender) {
        MessagesUtil.sendMessage(sender, "help-header");
        for (SubCommandInfo info : SUBCOMMANDS) {
            if (!hasPermission(sender, info.permission())) continue;
            MessagesUtil.sendMessage(sender, "help-entry", TagResolver.resolver(
                    Placeholder.unparsed("usage", info.usage()),
                    Placeholder.unparsed("description", info.description())));
        }
    }

    /**
     * Resolves the player a subcommand acts on. When a player is named at the given index, targeting
     * anyone but the sender requires the other-permission. Without an arg, falls back to the sender.
     * Sends no-permission or invalid-player when unresolved.
     */
    @Nullable
    private static Player resolveTarget(@NotNull CommandSender sender, String @NotNull [] args, int index, String otherPermission, boolean silent) {
        if (args.length > index) {
            Player target = Bukkit.getPlayer(args[index]);
            if (target == null) {
                if (!silent) MessagesUtil.sendMessage(sender, "invalid-player");
                return null;
            }
            if (target != sender && !hasPermission(sender, otherPermission)) {
                if (!silent) MessagesUtil.sendMessage(sender, "no-permission");
                return null;
            }
            return target;
        }
        Player target = sender instanceof Player ? (Player) sender : null;
        if (target == null && !silent) MessagesUtil.sendMessage(sender, "invalid-player");
        return target;
    }

    @Nullable
    private static CosmeticUser getUser(@NotNull CommandSender sender, @NotNull Player target, boolean silent) {
        CosmeticUser user = CosmeticUsers.getUser(target);
        if (user == null && !silent) MessagesUtil.sendMessage(sender, "invalid-player");
        return user;
    }

    private static TagResolver cosmeticPlaceholders(@NotNull Cosmetic cosmetic, @NotNull CosmeticSlot slot, @NotNull Player target) {
        final ItemStack cosmeticItem = cosmetic.getItem();
        Component itemName = Component.text(cosmetic.getId());
        if (HibiscusCommonsPlugin.isOnPaper() && cosmeticItem != null) {
            itemName = cosmeticItem.effectiveName();
        }

        return TagResolver.resolver(
                Placeholder.parsed("cosmetic", cosmetic.getId()),
                Placeholder.parsed("player", target.getName()),
                Placeholder.parsed("cosmeticslot", slot.toString()),
                Placeholder.component("cosmetic_item_name", itemName));
    }

    static boolean hasPermission(@NotNull CommandSender sender, String permission) {
        return sender.isOp() || sender.hasPermission(permission);
    }
}
