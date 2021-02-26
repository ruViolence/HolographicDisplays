/*
 * Copyright (C) filoghost and contributors
 *
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package me.filoghost.holographicdisplays.nms.v1_16_R3;

import me.filoghost.holographicdisplays.api.line.HologramLine;
import me.filoghost.holographicdisplays.core.nms.entity.NMSEntityBase;
import me.filoghost.holographicdisplays.core.nms.entity.NMSSlime;
import me.filoghost.holographicdisplays.core.DebugLogger;
import me.filoghost.fcommons.reflection.ReflectField;
import net.minecraft.server.v1_16_R3.AxisAlignedBB;
import net.minecraft.server.v1_16_R3.DamageSource;
import net.minecraft.server.v1_16_R3.Entity;
import net.minecraft.server.v1_16_R3.EntityDamageSource;
import net.minecraft.server.v1_16_R3.EntityPlayer;
import net.minecraft.server.v1_16_R3.EntitySlime;
import net.minecraft.server.v1_16_R3.EntityTypes;
import net.minecraft.server.v1_16_R3.IChatBaseComponent;
import net.minecraft.server.v1_16_R3.NBTTagCompound;
import net.minecraft.server.v1_16_R3.SoundEffect;
import net.minecraft.server.v1_16_R3.World;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_16_R3.entity.CraftEntity;
import org.bukkit.event.player.PlayerInteractEntityEvent;

public class EntityNMSSlime extends EntitySlime implements NMSSlime {
    
    private static final ReflectField<Entity> VEHICLE_FIELD = ReflectField.lookup(Entity.class, Entity.class, "vehicle");

    private final HologramLine parentPiece;
    private CraftEntity customBukkitEntity;
    
    public EntityNMSSlime(World world, HologramLine parentPiece) {
        super(EntityTypes.SLIME, world);
        super.persistent = true;
        super.collides = false;
        a(0.0F, 0.0F);
        setSize(1, false);
        setInvisible(true);
        this.parentPiece = parentPiece;
        forceSetBoundingBox(new NullBoundingBox());
    }
    
    @Override
    public void tick() {
        // Disable normal ticking for this entity.
        
        // So it won't get removed.
        ticksLived = 0;
    }
    
    @Override
    public void inactiveTick() {
        // Disable normal ticking for this entity.
        
        // So it won't get removed.
        ticksLived = 0;
    }
    
    @Override
    public void a(AxisAlignedBB boundingBox) {
        // Do not change it!
    }
    
    public void forceSetBoundingBox(AxisAlignedBB boundingBox) {
        super.a(boundingBox);
    }
    
    @Override
    public void saveData(NBTTagCompound nbttagcompound) {
        // Do not save NBT.
    }
    
    @Override
    public boolean a_(NBTTagCompound nbttagcompound) {
        // Do not save NBT.
        return false;
    }

    @Override
    public boolean d(NBTTagCompound nbttagcompound) {
        // Do not save NBT.
        return false;
    }
    
    @Override
    public NBTTagCompound save(NBTTagCompound nbttagcompound) {
        // Do not save NBT.
        return nbttagcompound;
    }
    
    @Override
    public void load(NBTTagCompound nbttagcompound) {
        // Do not load NBT.
    }
    
    @Override
    public void loadData(NBTTagCompound nbttagcompound) {
        // Do not load NBT.
    }
    
    @Override
    public boolean damageEntity(DamageSource damageSource, float amount) {
        if (damageSource instanceof EntityDamageSource) {
            EntityDamageSource entityDamageSource = (EntityDamageSource) damageSource;
            if (entityDamageSource.getEntity() instanceof EntityPlayer) {
                Bukkit.getPluginManager().callEvent(new PlayerInteractEntityEvent(((EntityPlayer) entityDamageSource.getEntity()).getBukkitEntity(), getBukkitEntity())); // Bukkit takes care of the exceptions
            }
        }
        return false;
    }
    
    @Override
    public boolean isInvulnerable(DamageSource source) {
        /*
         * The field Entity.invulnerable is private.
         * It's only used while saving NBTTags, but since the entity would be killed
         * on chunk unload, we prefer to override isInvulnerable().
         */
        return true;
    }
    
    @Override
    public boolean isCollidable() {
        return false;
    }

    @Override
    public void setCustomName(IChatBaseComponent ichatbasecomponent) {
        // Locks the custom name.
    }
    
    @Override
    public void setCustomNameVisible(boolean visible) {
        // Locks the custom name.
    }
    
    @Override
    public void playSound(SoundEffect soundeffect, float f, float f1) {
        // Remove sounds.
    }
    
    @Override
    public void die() {
        // Prevent being killed.
    }
    
    @Override
    public CraftEntity getBukkitEntity() {
        if (customBukkitEntity == null) {
            customBukkitEntity = new CraftNMSSlime(super.world.getServer(), this);
        }
        return customBukkitEntity;
    }

    @Override
    public boolean isDeadNMS() {
        return super.dead;
    }
    
    @Override
    public void killEntityNMS() {
        super.dead = true;
    }
    
    @Override
    public void setLocationNMS(double x, double y, double z) {
        super.setPosition(x, y, z);
    }
    
    @Override
    public int getIdNMS() {
        return super.getId();
    }
    
    @Override
    public HologramLine getHologramLine() {
        return parentPiece;
    }

    @Override
    public org.bukkit.entity.Entity getBukkitEntityNMS() {
        return getBukkitEntity();
    }
    
    @Override
    public void setPassengerOfNMS(NMSEntityBase vehicleBase) {
        if (!(vehicleBase instanceof Entity)) {
            // It should never dismount
            return;
        }
        
        Entity entity = (Entity) vehicleBase;
        
        try {
            if (super.getVehicle() != null) {
                Entity oldVehicle = super.getVehicle();
                VEHICLE_FIELD.set(this, null);
                oldVehicle.passengers.remove(this);
            }

            VEHICLE_FIELD.set(this, entity);
            entity.passengers.clear();
            entity.passengers.add(this);

        } catch (Throwable t) {
            DebugLogger.cannotSetPassenger(t);
        }
    }
}