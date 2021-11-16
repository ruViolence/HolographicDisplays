/*
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *  
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  GNU General Public License for more details.
 *  
 *  You should have received a copy of the GNU General Public License
 *  along with this program. If not, see <https://www.gnu.org/licenses/>.
 */
package com.gmail.filoghost.holographicdisplays.nms.v1_17_R1;

import com.gmail.filoghost.holographicdisplays.api.line.HologramLine;
import com.gmail.filoghost.holographicdisplays.api.line.ItemLine;
import com.gmail.filoghost.holographicdisplays.nms.interfaces.ChatComponentAdapter;
import com.gmail.filoghost.holographicdisplays.nms.interfaces.CustomNameHelper;
import com.gmail.filoghost.holographicdisplays.nms.interfaces.ItemPickupManager;
import com.gmail.filoghost.holographicdisplays.nms.interfaces.NMSManager;
import com.gmail.filoghost.holographicdisplays.nms.interfaces.entity.NMSArmorStand;
import com.gmail.filoghost.holographicdisplays.nms.interfaces.entity.NMSEntityBase;
import com.gmail.filoghost.holographicdisplays.nms.interfaces.entity.NMSItem;
import com.gmail.filoghost.holographicdisplays.util.ConsoleLogger;
import com.gmail.filoghost.holographicdisplays.util.Validator;
import net.minecraft.network.chat.ChatComponentText;
import net.minecraft.network.chat.IChatBaseComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.WorldServer;
import net.minecraft.util.MathHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Entity.RemovalReason;
import org.bukkit.Bukkit;
import org.bukkit.craftbukkit.v1_17_R1.CraftWorld;
import org.bukkit.craftbukkit.v1_17_R1.entity.CraftEntity;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class NmsManagerImpl implements NMSManager {
	
	@Override
	public void setup() {}
	
	@Override
	public NMSItem spawnNMSItem(org.bukkit.World bukkitWorld, double x, double y, double z, ItemLine parentPiece, ItemStack stack, ItemPickupManager itemPickupManager) {
		WorldServer nmsWorld = ((CraftWorld) bukkitWorld).getHandle();
		EntityNMSItem customItem = new EntityNMSItem(nmsWorld, parentPiece, itemPickupManager);
		customItem.setLocationNMS(x, y, z);
		customItem.setItemStackNMS(stack);
		if (!addEntityToWorld(nmsWorld, customItem)) {
			ConsoleLogger.handleSpawnFail(parentPiece);
		}
		return customItem;
	}
	
	@Override
	public EntityNMSSlime spawnNMSSlime(org.bukkit.World bukkitWorld, double x, double y, double z, HologramLine parentPiece) {
		WorldServer nmsWorld = ((CraftWorld) bukkitWorld).getHandle();
		EntityNMSSlime touchSlime = new EntityNMSSlime(nmsWorld, parentPiece);
		touchSlime.setLocationNMS(x, y, z);
		if (!addEntityToWorld(nmsWorld, touchSlime)) {
			ConsoleLogger.handleSpawnFail(parentPiece);
		}
		return touchSlime;
	}
	
	@Override
	public NMSArmorStand spawnNMSArmorStand(org.bukkit.World world, double x, double y, double z, HologramLine parentPiece, boolean broadcastLocationPacket) {
		WorldServer nmsWorld = ((CraftWorld) world).getHandle();
		EntityNMSArmorStand invisibleArmorStand = new EntityNMSArmorStand(nmsWorld, parentPiece);
		invisibleArmorStand.setLocationNMS(x, y, z, broadcastLocationPacket);
		if (!addEntityToWorld(nmsWorld, invisibleArmorStand)) {
			ConsoleLogger.handleSpawnFail(parentPiece);
		}
		return invisibleArmorStand;
	}
	
	private boolean addEntityToWorld(WorldServer nmsWorld, Entity nmsEntity) {
		Validator.isTrue(Bukkit.isPrimaryThread(), "Async entity add");
		
        final int chunkX = MathHelper.floor(nmsEntity.locX() / 16.0);
        final int chunkZ = MathHelper.floor(nmsEntity.locZ() / 16.0);
        
        if (!nmsWorld.isChunkLoaded(chunkX, chunkZ)) {
        	// This should never happen
			nmsEntity.getBukkitEntity().remove();
            nmsEntity.setRemoved(RemovalReason.b /* DISCARDED */);
            return false;
        }
        
        try {
        	nmsWorld.G /* entityManager */ .a /* addNewEntity */ (nmsEntity);
        	return true;
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		}
    }
	
	@Override
	public boolean isNMSEntityBase(org.bukkit.entity.Entity bukkitEntity) {
		return ((CraftEntity) bukkitEntity).getHandle() instanceof NMSEntityBase;
	}

	@Override
	public NMSEntityBase getNMSEntityBase(org.bukkit.entity.Entity bukkitEntity) {
		Entity nmsEntity = ((CraftEntity) bukkitEntity).getHandle();
		
		if (nmsEntity instanceof NMSEntityBase) {
			return ((NMSEntityBase) nmsEntity);
		} else {
			return null;
		}
	}
	
	@Override
	public NMSEntityBase getNMSEntityBaseFromID(org.bukkit.World bukkitWorld, int entityID) {
		WorldServer nmsWorld = ((CraftWorld) bukkitWorld).getHandle();
		Entity nmsEntity = nmsWorld.getEntity(entityID);
		
		if (nmsEntity instanceof NMSEntityBase) {
			return ((NMSEntityBase) nmsEntity);
		} else {
			return null;
		}
	}
	
	@Override
	public Object replaceCustomNameText(Object customNameObject, String target, String replacement) {
		return CustomNameHelper.replaceCustomNameChatComponent(NMSChatComponentAdapter.INSTANCE, customNameObject, target, replacement);
	}
	
	@Override
	public int getCurrentTick() {
		return MinecraftServer.currentTick;
	}

	private static enum NMSChatComponentAdapter implements ChatComponentAdapter<IChatBaseComponent> {

		INSTANCE {
			
			public ChatComponentText cast(Object chatComponentObject) {
				return (ChatComponentText) chatComponentObject;
			}
			
			@Override
			public String getText(IChatBaseComponent chatComponent) {
				return chatComponent.getText();
			}
	
			@Override
			public List<IChatBaseComponent> getSiblings(IChatBaseComponent chatComponent) {
				return chatComponent.getSiblings();
			}
	
			@Override
			public void addSibling(IChatBaseComponent chatComponent, IChatBaseComponent newSibling) {
				newSibling.getChatModifier().setChatModifier(chatComponent.getChatModifier());
				chatComponent.getSiblings().add(newSibling);
			}
	
			@Override
			public ChatComponentText cloneComponent(IChatBaseComponent chatComponent, String newText) {
				ChatComponentText clonedChatComponent = new ChatComponentText(newText);
				clonedChatComponent.setChatModifier(chatComponent.getChatModifier().a());
				return clonedChatComponent;
			}
			
		}
		
	}
	
}
