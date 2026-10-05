package com.hibiscusmc.hmccosmetics.user.manager;

import org.bukkit.Bukkit;
import com.hibiscusmc.hmccosmetics.HMCCosmeticsPlugin;
import com.hibiscusmc.hmccosmetics.config.Settings;
import com.hibiscusmc.hmccosmetics.cosmetic.types.CosmeticBalloonType;
import com.hibiscusmc.hmccosmetics.user.CosmeticUser;
import com.hibiscusmc.hmccosmetics.user.CosmeticUsers;
import com.hibiscusmc.hmccosmetics.util.HMCCServerUtils;
import com.hibiscusmc.hmccosmetics.util.MessagesUtil;
import com.hibiscusmc.hmccosmetics.util.Scheduler;
import org.bukkit.inventory.ItemStack;
import java.util.concurrent.atomic.AtomicBoolean;
import com.hibiscusmc.hmccosmetics.util.packets.HMCCPacketManager;
import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.entity.data.BukkitEntityData;
import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.model.ModeledEntity;
import lombok.Getter;
import me.lojosho.hibiscuscommons.hooks.Hooks;
import me.lojosho.hibiscuscommons.nms.NMSHandlers;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public class UserBalloonManager {

    private final CosmeticUser user;
    @Getter
    private BalloonType balloonType;
    private CosmeticBalloonType cosmeticBalloonType;
    @Getter
    private UserBalloonPufferfish pufferfish;
    private final ArmorStand modelEntity;
    private volatile Location location;
    private volatile Vector velocity = new Vector();
    private volatile boolean removed;
    private final AtomicBoolean teleporting = new AtomicBoolean();

    public UserBalloonManager(CosmeticUser user, @NotNull Location location) {
        this.user = user;
        this.location = location.clone();
        this.pufferfish = new UserBalloonPufferfish(user.getUniqueId(), NMSHandlers.getHandler().getUtilHandler().getNextEntityId(location.getWorld()), UUID.randomUUID());
        this.modelEntity = location.getWorld().spawn(location, ArmorStand.class, (e) -> {
            e.setInvisible(true);
            e.setGravity(false);
            e.setSilent(true);
            e.setInvulnerable(true);
            e.setSmall(true);
            e.setMarker(true);
            e.setPersistent(false);
            e.setAI(false);
            e.getPersistentDataContainer().set(HMCCServerUtils.getCosmemeticMobKey(), PersistentDataType.BOOLEAN, true);
        });
    }

    public void spawnModel(@NotNull CosmeticBalloonType cosmeticBalloonType, Color color) {
        Scheduler.run(modelEntity, () -> {
            if (!removed) spawnModelNow(cosmeticBalloonType, color);
        });
    }

    private void spawnModelNow(@NotNull CosmeticBalloonType cosmeticBalloonType, Color color) {
        // redo this
        if (cosmeticBalloonType.getModelName() != null && Hooks.isActiveHook("ModelEngine")) {
            balloonType = BalloonType.MODELENGINE;
        } else {
            if (cosmeticBalloonType.getItem() != null) {
                balloonType = BalloonType.ITEM;
            } else {
                balloonType = BalloonType.NONE;
            }
        }
        this.cosmeticBalloonType = cosmeticBalloonType;
        MessagesUtil.sendDebugMessages("balloontype is " + balloonType);

        if (balloonType == BalloonType.MODELENGINE) {
            String id = cosmeticBalloonType.getModelName();
            MessagesUtil.sendDebugMessages("Attempting Spawning for " + id);
            if (ModelEngineAPI.getBlueprint(id) == null) {
                MessagesUtil.sendDebugMessages("Invalid Model Engine Blueprint " + id, Level.SEVERE);
                return;
            }
            ModeledEntity modeledEntity = ModelEngineAPI.getOrCreateModeledEntity(modelEntity);
            ActiveModel model = ModelEngineAPI.createActiveModel(ModelEngineAPI.getBlueprint(id));
            model.setCanHurt(false);
            modeledEntity.addModel(model, false);

            if (color != null) {
                modeledEntity.getModels().forEach((d, singleModel) -> {
                    if (cosmeticBalloonType.isDyeablePart(d)) {
                        singleModel.setDefaultTint(color);
                        singleModel.getModelRenderer().sendToClient(ModelEngineAPI.getNMSHandler().createParsers());
                    }
                });
            }

            BukkitEntityData data = (BukkitEntityData) modeledEntity.getBase().getData();
            data.setBlockedCullIgnoreRadius((double) Settings.getViewDistance());
            data.getTracked().setPlayerPredicate(this::playerCheck);
            return;
        }
        if (balloonType == BalloonType.ITEM) {
            modelEntity.getEquipment().setHelmet(cosmeticBalloonType.getItem());
        }
    }

    public void remove() {
        // This code is like a brick road, always bumpy.
        // Basically, the balloon viewers ignore people in wardrobe, which well, if your the user in the wardrobe, ain't including you.
        // This manually passes in the viewers for it to destroy, which includes the person in the wardrobe
        if (user.getPlayer() != null && user.isInWardrobe()) pufferfish.destroyPufferfish(List.of(user.getPlayer()));
        else pufferfish.destroyPufferfish();

        removed = true;
        // Folia has already halted regions during server shutdown; this entity is nonpersistent.
        if (Bukkit.isStopping()) return;
        Scheduler.run(modelEntity, () -> {
            if (balloonType == BalloonType.MODELENGINE) {
                final ModeledEntity entity = ModelEngineAPI.getModeledEntity(modelEntity);
                if (entity != null) entity.destroy();
            }
            modelEntity.remove();
        });
        MessagesUtil.sendDebugMessages("Balloon Entity Removed");
    }

    public void addPlayerToModel(final CosmeticUser user, final CosmeticBalloonType cosmeticBalloonType) {
        addPlayerToModel(user, cosmeticBalloonType, null);
    }

    public void addPlayerToModel(final CosmeticUser user, final CosmeticBalloonType cosmeticBalloonType, Color color) {
        ItemStack item = balloonType == BalloonType.ITEM ? user.getUserCosmeticItem(cosmeticBalloonType) : null;
        Scheduler.run(modelEntity, () -> {
            if (removed) return;
            if (balloonType == BalloonType.MODELENGINE && ModelEngineAPI.getModeledEntity(modelEntity) == null) {
                spawnModelNow(cosmeticBalloonType, color);
            } else if (item != null) {
                modelEntity.getEquipment().setHelmet(item);
            }
        });
    }

    public void removePlayerFromModel(final Player viewer) {
        Scheduler.run(modelEntity, () -> {
            if (!removed && balloonType == BalloonType.ITEM) modelEntity.getEquipment().clear();
        });
    }

    public boolean isModelValid() { return !removed; }

    public Entity getModelEntity() {
        return this.modelEntity;
    }


    public int getPufferfishBalloonId() {
        return pufferfish.getPufferFishEntityId();
    }

    public UUID getPufferfishBalloonUniqueId() {
        return pufferfish.getUuid();
    }

    public UUID getModelUnqiueId() {
        return getModelEntity().getUniqueId();
    }

    public int getModelId() {
        return getModelEntity().getEntityId();
    }

    public Location getLocation() {
        return location.clone();
    }

    public void setLocation(Location location) {
        this.location = location.clone();
        moveModel();
    }

    private void moveModel() {
        Scheduler.run(modelEntity, () -> {
            if (removed || !teleporting.compareAndSet(false, true)) return;
            Location target = this.location.clone();
            modelEntity.teleportAsync(target).whenComplete((success, error) -> {
                teleporting.set(false);
                if (error != null) HMCCosmeticsPlugin.getInstance().getLogger().warning("Balloon teleport failed: " + error);
                if (!removed && !this.location.equals(target)) moveModel();
            });
        });
    }

    public Vector getVelocity() {
        return velocity.clone();
    }

    public void setVelocity(Vector vector) {
        this.velocity = vector.clone();
        Scheduler.run(modelEntity, () -> { if (!removed) modelEntity.setVelocity(vector.clone()); });
    }

    public void sendRemoveLeashPacket(List<Player> viewer) {
        HMCCPacketManager.sendLeashPacket(getPufferfishBalloonId(), -1, viewer);
    }

    public void sendRemoveLeashPacket() {
        HMCCPacketManager.sendLeashPacket(getPufferfishBalloonId(), -1, getLocation());
    }

    public void sendLeashPacket(int entityId) {
        if (cosmeticBalloonType.isShowLead()) {
            HMCCPacketManager.sendLeashPacket(getPufferfishBalloonId(), entityId, getLocation());
        }
    }

    public enum BalloonType {
        MODELENGINE,
        ITEM,
        NONE
    }

    private boolean playerCheck(final Player player) {
        MessagesUtil.sendDebugMessages("playerCheck");
        CosmeticUser viewer = CosmeticUsers.getUser(player.getUniqueId());

        if (user.getPlayer() == player) {
            return (!user.isHidden());
        } else {
            if (user.isInWardrobe()) return false;
            MessagesUtil.sendDebugMessages("playerCheck - Not Same Player");
            if (viewer != null && viewer.isInWardrobe()) {
                MessagesUtil.sendDebugMessages("playerCheck - Viewer in Wardrobe");
                return false;
            }
        }
        return (!user.isHidden());
    }
}
