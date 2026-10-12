package com.hibiscusmc.hmccosmetics.packets;

import java.util.HashMap;
import com.hibiscusmc.hmccosmetics.config.Settings;
import com.hibiscusmc.hmccosmetics.cosmetic.Cosmetic;
import com.hibiscusmc.hmccosmetics.cosmetic.CosmeticSlot;
import com.hibiscusmc.hmccosmetics.cosmetic.types.CosmeticBackpackType;
import com.hibiscusmc.hmccosmetics.gui.Menu;
import com.hibiscusmc.hmccosmetics.user.CosmeticUser;
import com.hibiscusmc.hmccosmetics.user.CosmeticUsers;
import com.hibiscusmc.hmccosmetics.user.manager.UserWardrobeManager;
import com.hibiscusmc.hmccosmetics.util.HMCCInventoryUtils;
import com.hibiscusmc.hmccosmetics.util.Scheduler;
import com.hibiscusmc.hmccosmetics.util.packets.HMCCPacketManager;
import me.lojosho.hibiscuscommons.packets.PacketAction;
import me.lojosho.hibiscuscommons.packets.PacketInterface;
import me.lojosho.hibiscuscommons.packets.data.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Netty callbacks only consume published copies; live player work runs on the entity scheduler. */
public class CosmeticPacketInterface implements PacketInterface {
    @Override
    public @NotNull PacketAction writeContainerContent(@NotNull Player player, @NotNull ContainerContentWrapper wrapper) {
        CosmeticUser user = CosmeticUsers.getUser(player);
        if (wrapper.getWindowId() != 0 || user == null || user.isInWardrobe()) return PacketAction.NOTHING;
        List<ItemStack> items = new ArrayList<>(wrapper.getSlotData());
        boolean changed = false;
        for (int slot = 0; slot < items.size(); slot++) {
            EquipmentSlot equipmentSlot = HMCCInventoryUtils.getPacketArmorSlot(slot);
            if (equipmentSlot == null) continue;
            ItemStack item = user.getPacketItem(equipmentSlot);
            if (item == null) continue;
            if (Settings.getSlotOption(equipmentSlot).isRequireEmpty() && !items.get(slot).getType().isAir()) continue;
            items.set(slot, item);
            changed = true;
        }
        if (!changed) return PacketAction.NOTHING;
        wrapper.setSlotData(items);
        return PacketAction.CHANGED;
    }

    @Override
    public @NotNull PacketAction writeSlotContent(@NotNull Player player, @NotNull SlotContentWrapper wrapper) {
        CosmeticUser user = CosmeticUsers.getUser(player);
        if (wrapper.getWindowId() != 0 || user == null || user.isInWardrobe()) return PacketAction.NOTHING;
        EquipmentSlot slot = HMCCInventoryUtils.getPacketArmorSlot(wrapper.getSlot());
        if (slot == null) return PacketAction.NOTHING;
        ItemStack item = user.getPacketItem(slot);
        if (item == null) return PacketAction.NOTHING;
        if (Settings.getSlotOption(slot).isRequireEmpty() && !wrapper.getItemStack().getType().isAir()) return PacketAction.NOTHING;
        wrapper.setItemStack(item);
        return PacketAction.CHANGED;
    }

    @Override
    public @NotNull PacketAction writeEquipmentContent(@NotNull Player player, @NotNull EntityEquipmentWrapper wrapper) {
        CosmeticUser user = CosmeticUsers.getUser(wrapper.getEntityId());
        if (user == null || user.isInWardrobe()) return PacketAction.NOTHING;
        var armor = new HashMap<>(wrapper.getArmor());
        boolean changed = false;
        for (EquipmentSlot slot : armor.keySet()) {
            // The owner's selected hotbar item must remain client-authoritative.
            if (slot == EquipmentSlot.HAND && user.getUniqueId().equals(player.getUniqueId())) continue;
            ItemStack item = user.getPacketItem(slot);
            if (item == null) continue;
            if (Settings.getSlotOption(slot).isRequireEmpty() && !armor.get(slot).getType().isAir()) continue;
            armor.put(slot, item);
            changed = true;
        }
        if (!changed) return PacketAction.NOTHING;
        wrapper.setArmor(armor);
        return PacketAction.CHANGED;
    }

    @Override
    public @NotNull PacketAction writePassengerContent(@NotNull Player player, @NotNull PassengerWrapper wrapper) {
        if (!Settings.isBackpackInterceptPassengerPacket()) return PacketAction.NOTHING;
        CosmeticUser viewer = CosmeticUsers.getUser(player);
        if (viewer == null || viewer.isInWardrobe()) return PacketAction.NOTHING;

        int ownerId = wrapper.getOwner();
        Optional<CosmeticUser> optionalCosmeticUser = CosmeticUsers.values().stream()
                .filter(user -> user.getPlayer() != null)
                .filter(user -> ownerId == user.getPlayer().getEntityId())
                .findFirst();
        if (optionalCosmeticUser.isEmpty()) return PacketAction.NOTHING;

        CosmeticUser owner = optionalCosmeticUser.get();
        if (owner.isInWardrobe()) return PacketAction.NOTHING;

        Cosmetic backpackCosmetic = owner.getCosmetic(CosmeticSlot.BACKPACK);
        if (backpackCosmetic == null) return PacketAction.NOTHING;
        if (!(backpackCosmetic instanceof CosmeticBackpackType cosmeticBackpackType)) return PacketAction.NOTHING;

        if (owner.getUniqueId().equals(viewer.getUniqueId())) {
            if (cosmeticBackpackType.isFirstPersonCompadible()) return PacketAction.NOTHING;
        }

        int mount = owner.getPacketBackpackMount();
        if (mount == -1 || (owner.getUniqueId().equals(viewer.getUniqueId()) && owner.isPacketFirstPersonBackpack())) return PacketAction.NOTHING;

        List<Integer> passengers = new ArrayList<>(wrapper.getPassengers());
        if (passengers.contains(mount)) return PacketAction.NOTHING;
        passengers.add(mount);
        wrapper.setPassengers(passengers);
        return PacketAction.CHANGED;
    }

    @Override
    public PacketAction readPlayerScale(@NotNull Player player, @NotNull PlayerScaleWrapper wrapper) {
        CosmeticUser user = CosmeticUsers.getUser(wrapper.getEntityId());
        if (user != null && !user.isInWardrobe()) {
            for (int id : user.getPacketBackpackIds()) HMCCPacketManager.sendEntityScalePacket(id, wrapper.getScale(), List.of(player));
        }
        return PacketAction.NOTHING;
    }

    @Override
    public @NotNull PacketAction readInventoryClick(@NotNull Player player, @NotNull InventoryClickWrapper wrapper) {
        if (wrapper.getClickType() != 0 || wrapper.getSlotNumber() == -999) return PacketAction.NOTHING;
        CosmeticSlot slot = HMCCInventoryUtils.NMSCosmeticSlot(wrapper.getSlotNumber());
        if (slot != null) Scheduler.later(player, () -> {
            CosmeticUser user = CosmeticUsers.getUser(player);
            if (user != null && !user.isInWardrobe()) user.updateCosmetic(slot);
        }, 1);
        return PacketAction.NOTHING;
    }

    @Override
    public @NotNull PacketAction readPlayerAction(@NotNull Player player, @NotNull PlayerActionWrapper wrapper) {
        CosmeticUser user = CosmeticUsers.getUser(player);
        return Settings.isPreventOffhandSwapping() && wrapper.getActionType().equalsIgnoreCase("SWAP_ITEM_WITH_OFFHAND")
                && user != null && user.hasCosmeticInSlot(CosmeticSlot.OFFHAND) ? PacketAction.CANCELLED : PacketAction.NOTHING;
    }

    private void reopenWardrobeMenu(Player player) {
        Scheduler.run(player, () -> {
            CosmeticUser user = CosmeticUsers.getUser(player);
            if (user == null || !user.isInWardrobe()) return;
            var wardrobe = user.getWardrobeManager();
            if (wardrobe.getWardrobeStatus() != UserWardrobeManager.WardrobeStatus.RUNNING) return;
            Menu menu = wardrobe.getLastOpenMenu();
            if (menu != null) menu.openMenu(user);
        });
    }

    @Override
    public @NotNull PacketAction readPlayerArm(@NotNull Player player, @NotNull PlayerSwingWrapper wrapper) {
        CosmeticUser user = CosmeticUsers.getUser(player);
        if (user == null || !user.isInWardrobe()) return PacketAction.NOTHING;
        reopenWardrobeMenu(player);
        return PacketAction.CANCELLED;
    }

    @Override
    public @NotNull PacketAction readEntityHandle(@NotNull Player player, @NotNull PlayerInteractWrapper wrapper) {
        CosmeticUser user = CosmeticUsers.getUser(player);
        return user != null && user.isInWardrobe() ? PacketAction.CANCELLED : PacketAction.NOTHING;
    }

    @Override
    public @NotNull PacketAction readPlayerInput(@NotNull Player player, @NotNull PlayerInputWrapper wrapper) {
        if (wrapper.jump()) reopenWardrobeMenu(player);
        return PacketAction.NOTHING;
    }
}
