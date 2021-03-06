/*
 * This file is part of SpoutcraftPlugin.
 *
 * Copyright (c) 2011 SpoutcraftDev <http://spoutcraft.org//>
 * SpoutcraftPlugin is licensed under the GNU Lesser General Public License.
 *
 * SpoutcraftPlugin is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SpoutcraftPlugin is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.getspout.spout.config;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.bukkit.Location;
import org.bukkit.configuration.MemorySection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.getspout.spout.Spout;
import org.getspout.spoutapi.player.SpoutPlayer;

public class ConfigReader {
	private static boolean buildCheck = true;
	private static int authTicks = 200;
	private static String kickMessage = "This server requires Spoutcraft! http://get.spout.org";

	private static boolean chunkDataCache = true;
	private static boolean allowClientCache = true;
	private static boolean teleportSmoothing = true;
	private static boolean runDeadlockMonitor = false;

	private static HashMap<String, List<Waypoint> > waypoints = new HashMap<String, List<Waypoint>>();

	public void read() {
		Spout.getInstance().reloadConfig();
		FileConfiguration configuration = Spout.getInstance().getConfig();
		configuration.options().copyDefaults(true);

		kickMessage = configuration.getString("ForceClientKickMessage");
		authTicks = configuration.getInt("ClientCheckTicks", 200);
		buildCheck = configuration.getBoolean("ServerVersionCheck", true);
		/*allowSkyCheat = configuration.getBoolean("AllowSkyCheat", false);
		allowClearWaterCheat = configuration.getBoolean("AllowClearWaterCheat", false);
		allowStarsCheat = configuration.getBoolean("AllowStarsCheat", false);
		allowWeatherCheat = configuration.getBoolean("AllowWeatherCheat", false);
		allowTimeCheat = configuration.getBoolean("AllowTimeCheat", false);
		allowCoordsCheat = configuration.getBoolean("AllowCoordsCheat", false);
		allowEntityLabelCheat = configuration.getBoolean("AllowEntityLabelCheat", false);
		allowVoidFogCheat = configuration.getBoolean("AllowVoidFogCheat", false);*/
		chunkDataCache = configuration.getBoolean("ChunkDataCache", true);
		allowClientCache = configuration.getBoolean("AllowClientCache", true);
		teleportSmoothing = configuration.getBoolean("TeleportSmoothing", true);
		runDeadlockMonitor = configuration.getBoolean("DeadlockMonitor", false);

		loadWaypoints(configuration);

		Spout.getInstance().saveConfig();
	}

	private void loadWaypoints(FileConfiguration config) {
		try {
			Object o = config.get("waypoints");
			if (o != null) {
				MemorySection mem = (MemorySection)o;
				Map<String, Object> worlds = getMemorySectionMap(mem);
				Iterator<Entry<String, Object>> i = worlds.entrySet().iterator();
				while (i.hasNext()) {
					Entry<String, Object> e = i.next();
					final String world = e.getKey().toLowerCase();
					if (e.getValue() instanceof MemorySection) {
						Map<String, Object> waypoints = getMemorySectionMap((MemorySection) e.getValue());
						Iterator<Entry<String, Object>> j = waypoints.entrySet().iterator();
						while (j.hasNext()) {
							Entry<String, Object> waypoint = j.next();
							MemorySection values = (MemorySection) waypoint.getValue();
							double x = values.getDouble("x");
							double y = values.getDouble("y");
							double z = values.getDouble("z");

							List<Waypoint> existing = ConfigReader.waypoints.get(world);
							if (existing == null) {
								existing = new LinkedList<Waypoint>();
								ConfigReader.waypoints.put(world, existing);
							}
							existing.add(new Waypoint(x, y, z, waypoint.getKey()));
						}
					}
				}
			}
		} catch (Exception e) {
			System.out.println("[SpoutPlugin] Error while loading waypoints: ");
			e.printStackTrace();
		}
	}

	public void addWaypoint(String name, Location location) {
		Spout.getInstance().reloadConfig();
		FileConfiguration configuration = Spout.getInstance().getConfig();
		configuration.set("waypoints." + location.getWorld().getName().toLowerCase() + "." + name + ".x", location.getX());
		configuration.set("waypoints." + location.getWorld().getName().toLowerCase() + "." + name + ".y", location.getY());
		configuration.set("waypoints." + location.getWorld().getName().toLowerCase() + "." + name + ".z", location.getZ());
		Spout.getInstance().saveConfig();
		for (Player p : location.getWorld().getPlayers()) {
			if (p instanceof SpoutPlayer) {
				((SpoutPlayer)p).addWaypoint(name, location.getX(), location.getY(), location.getZ());
			}
		}
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> getMemorySectionMap(MemorySection section) {
		Field f;
		try {
			f = MemorySection.class.getDeclaredField("map");
			f.setAccessible(true);
			return (Map<String, Object>) f.get(section);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	@SuppressWarnings("unchecked")
	public static List<Waypoint> getWaypoints(String world) {
		List<Waypoint> l = waypoints.get(world);
		if (l == null) {
			return Collections.EMPTY_LIST;
		}
		return l;
	}

	public static boolean isBuildCheck() {
		return buildCheck;
	}

	public static String getKickMessage() {
		return kickMessage;
	}

	public static int getAuthenticateTicks() {
		return authTicks;
	}

	public static boolean isChunkDataCache() {
		return chunkDataCache;
	}
	
	public static boolean isAllowClientCache() {
		return allowClientCache;
	}

	public static boolean isTeleportSmoothing() {
		return teleportSmoothing;
	}

	public static boolean runDeadlockMonitor() {
		return runDeadlockMonitor;
	}
}
