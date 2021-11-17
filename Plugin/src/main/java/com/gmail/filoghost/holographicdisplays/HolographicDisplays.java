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
package com.gmail.filoghost.holographicdisplays;

import java.io.File;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.plugin.java.JavaPlugin;

import com.gmail.filoghost.holographicdisplays.api.internal.BackendAPI;
import com.gmail.filoghost.holographicdisplays.bridge.bungeecord.BungeeServerTracker;
import com.gmail.filoghost.holographicdisplays.bridge.protocollib.ProtocolLibHook;
import com.gmail.filoghost.holographicdisplays.commands.main.HologramsCommandHandler;
import com.gmail.filoghost.holographicdisplays.disk.Configuration;
import com.gmail.filoghost.holographicdisplays.disk.HologramDatabase;
import com.gmail.filoghost.holographicdisplays.disk.UnicodeSymbols;
import com.gmail.filoghost.holographicdisplays.listener.MainListener;
import com.gmail.filoghost.holographicdisplays.nms.interfaces.NMSManager;
import com.gmail.filoghost.holographicdisplays.object.DefaultBackendAPI;
import com.gmail.filoghost.holographicdisplays.object.NamedHologram;
import com.gmail.filoghost.holographicdisplays.object.NamedHologramManager;
import com.gmail.filoghost.holographicdisplays.object.PluginHologram;
import com.gmail.filoghost.holographicdisplays.object.PluginHologramManager;
import com.gmail.filoghost.holographicdisplays.placeholder.AnimationsRegister;
import com.gmail.filoghost.holographicdisplays.placeholder.PlaceholdersManager;
import com.gmail.filoghost.holographicdisplays.task.BungeeCleanupTask;
import com.gmail.filoghost.holographicdisplays.task.StartupLoadHologramsTask;
import com.gmail.filoghost.holographicdisplays.task.WorldPlayerCounterTask;
import com.gmail.filoghost.holographicdisplays.util.ConsoleLogger;
import com.gmail.filoghost.holographicdisplays.util.NMSVersion;
import com.gmail.filoghost.holographicdisplays.util.VersionUtils;

public class HolographicDisplays extends JavaPlugin {
	
	// The main instance of the plugin.
	private static HolographicDisplays instance;
	
	// The manager for net.minecraft.server access.
	private static NMSManager nmsManager;
	
	// The listener for all the Bukkit and NMS events.
	private static MainListener mainListener;
	
	// The command handler, just in case a plugin wants to register more commands.
	private static HologramsCommandHandler commandHandler;
	
	// Not null if ProtocolLib is installed and successfully loaded.
	private static ProtocolLibHook protocolLibHook;
	
	// Not null if PlaceholderAPI is installed and successfully loaded.
	private static boolean hasPlaceholderAPI;
	
	@Override
	public void onEnable() {
		
		// Warn about plugin reloaders and the /reload command.
		if (instance != null || System.getProperty("HolographicDisplaysLoaded") != null) {
			Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "[HolographicDisplays] Please do not use /reload or plugin reloaders. Use the command \"/holograms reload\" instead. You will receive no support for doing this operation.");
		}
		
		System.setProperty("HolographicDisplaysLoaded", "true");
		instance = this;
		ConsoleLogger.setLogger(instance.getLogger());
		
		// Load placeholders.yml.
		UnicodeSymbols.load(this);

		// Load the configuration.
		Configuration.load(this);
		
		// The bungee chat API is required.
		try {
			Class.forName("net.md_5.bungee.api.chat.ComponentBuilder");
		} catch (ClassNotFoundException e) {
			criticalShutdown(
				"Holographic Displays requires the new chat API.",
				"You are probably running CraftBukkit instead of Spigot.");
			return;
		}
		
		if (!NMSVersion.isValid()) {
			criticalShutdown(
				"Holographic Displays does not support this server version.",
				"Supported Spigot versions: from 1.8.3 to 1.16.4.");
			return;
		}
		
		try {
			nmsManager = (NMSManager) Class.forName("com.gmail.filoghost.holographicdisplays.nms." + NMSVersion.getCurrent() + ".NmsManagerImpl").getConstructor().newInstance();
		} catch (Throwable t) {
			t.printStackTrace();
			criticalShutdown(
				"Holographic Displays was unable to initialize the NMS manager.");
			return;
		}

		try {
			nmsManager.setup();
		} catch (Exception e) {
			e.printStackTrace();
			criticalShutdown(
				"Holographic Displays was unable to register custom entities.");
			return;
		}
		
		// ProtocolLib check.
		hookProtocolLib();
		
		// PlaceholderAPI check.
		try {
			Class.forName("me.clip.placeholderapi.PlaceholderAPI");
			hasPlaceholderAPI = true;
		} catch (ClassNotFoundException ignored) {}
		
		// Load animation files and the placeholder manager.
		PlaceholdersManager.load(this);
		try {
			AnimationsRegister.loadAnimations(this);
		} catch (Exception ex) {
			ConsoleLogger.log(Level.WARNING, "Failed to load animation files!", ex);
		}
		
		// Initalize other static classes.
		HologramDatabase.loadYamlFile(this);
		BungeeServerTracker.startTask(Configuration.bungeeRefreshSeconds);
		
		// Start repeating tasks.
		Bukkit.getScheduler().scheduleSyncRepeatingTask(this, new BungeeCleanupTask(), 5 * 60 * 20, 5 * 60 * 20);
		Bukkit.getScheduler().scheduleSyncRepeatingTask(this, new WorldPlayerCounterTask(), 0L, 3 * 20);
		
		if (getCommand("holograms") == null) {
			criticalShutdown(
				"Holographic Displays was unable to register the command \"holograms\".",
				"This can be caused by edits to plugin.yml or other plugins.");
			return;
		}
		
		getCommand("holograms").setExecutor(commandHandler = new HologramsCommandHandler());
		Bukkit.getPluginManager().registerEvents(mainListener = new MainListener(nmsManager), this);

		// Holograms are loaded later, when the worlds are ready.
		Bukkit.getScheduler().runTask(this, new StartupLoadHologramsTask());
		
		// Enable the API.
		BackendAPI.setImplementation(new DefaultBackendAPI());
	}
	

	@Override
	public void onDisable() {
		for (NamedHologram hologram : NamedHologramManager.getHolograms()) {
			hologram.despawnEntities();
		}
		for (PluginHologram hologram : PluginHologramManager.getHolograms()) {
			hologram.despawnEntities();
		}
	}
	
	public static NMSManager getNMSManager() {
		return nmsManager;
	}
	
	public static MainListener getMainListener() {
		return mainListener;
	}

	public static HologramsCommandHandler getCommandHandler() {
		return commandHandler;
	}
	
	private static void criticalShutdown(String... errorMessage) {
		String separator = "****************************************************************************";
		StringBuffer output = new StringBuffer("\n ");
		output.append("\n" + separator);
		for (String line : errorMessage) {
			output.append("\n    " + line);
		}
		output.append("\n ");
		output.append("\n    This plugin has been disabled.");
		output.append("\n" + separator);
		output.append("\n ");
		
		System.out.println(output);
		
		try {
			Thread.sleep(5000);
		} catch (InterruptedException ex) { }
		instance.setEnabled(false);
	}

	public static HolographicDisplays getInstance() {
		return instance;
	}


	public void hookProtocolLib() {
		if (!Bukkit.getPluginManager().isPluginEnabled("ProtocolLib")) {
			return;
		}

		try {
			String protocolVersion = Bukkit.getPluginManager().getPlugin("ProtocolLib").getDescription().getVersion();
			Matcher versionNumbersMatcher = Pattern.compile("([0-9\\.])+").matcher(protocolVersion);
			
			if (!versionNumbersMatcher.find()) {
				throw new IllegalArgumentException("could not find version numbers pattern");
			}
			
			String versionNumbers = versionNumbersMatcher.group();
			
			if (!VersionUtils.isVersionGreaterEqual(versionNumbers, "4.4")) {
				Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "[Holographic Displays] Detected old unsupported version of ProtocolLib, support disabled. You must use ProtocolLib 4.4.0 or higher.");
				return;
			}

			if (NMSVersion.isGreaterEqualThan(NMSVersion.v1_17_R1) && !VersionUtils.isVersionGreaterEqual(versionNumbers, "4.7")) {
				Bukkit.getConsoleSender().sendMessage(ChatColor.RED + "[Holographic Displays] The minimum ProtocolLib version for this Minecraft version is 4.7.0, support disabled.");
				return;
			}

		} catch (Exception e) {
			ConsoleLogger.log(Level.WARNING, "Could not detect ProtocolLib version (" + e.getClass().getName() + ": " + e.getMessage() + "), enabling support anyway and hoping for the best. If you get errors, please contact the author.");
		}
		
		try {
			ProtocolLibHook protocolLibHook = new com.gmail.filoghost.holographicdisplays.bridge.protocollib.current.ProtocolLibHookImpl();
			
			if (protocolLibHook.hook(this, nmsManager)) {
				HolographicDisplays.protocolLibHook = protocolLibHook;
				ConsoleLogger.log(Level.INFO, "Enabled player relative placeholders with ProtocolLib.");
			}
		} catch (Exception e) {
			ConsoleLogger.log(Level.WARNING, "Failed to load ProtocolLib support. Is it updated?", e);
		}
	}
	
	
	public static boolean hasProtocolLibHook() {
		return protocolLibHook != null;
	}
	
	
	public static ProtocolLibHook getProtocolLibHook() {
		return protocolLibHook;
	}
	
	
	public static boolean hasPlaceholderAPI() {
		return hasPlaceholderAPI;
	}
	
	
	public static boolean isConfigFile(File file) {
		return file.getName().toLowerCase().endsWith(".yml") && instance.getResource(file.getName()) != null;
	}
	
}
