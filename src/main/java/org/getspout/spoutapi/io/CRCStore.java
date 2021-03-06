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
package org.getspout.spoutapi.io;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.concurrent.ConcurrentHashMap;

import org.getspout.spoutapi.io.store.FlatFileStore;

public class CRCStore {
	private static FlatFileStore<String> urlCRCStore;
	private final static Object urlCRCStoreSync = new Object();
	private final static ConcurrentHashMap<String,Long> lastCheck = new ConcurrentHashMap<String,Long>();

	public static void setConfigFile(FlatFileStore<String> config) {
		synchronized(urlCRCStoreSync) {
			urlCRCStore = config;
			urlCRCStore.load();
		}
	}

	public static long getCRC(String urlString, byte[] buffer) {
		if (urlString == null) {
			return 0;
		}

		URL url;
		try {
			url = new URL(urlString);
		} catch (MalformedURLException mue) {
			return 0;
		}

		String key = url.toString();
		String info;
		long modified = 0;
		long crc = 0;

		synchronized(urlCRCStoreSync) {
			if (urlCRCStore != null) {
				info = urlCRCStore.get(key);
				if (info != null) {
					String[] split = info.split(":");
					if (split.length == 2) {
						try {
							modified = Long.parseLong(split[0]);
							crc = Long.parseLong(split[1]);
						} catch (NumberFormatException nfe) {
						}
					}
				}
			}
		}

		URLConnection urlConn = null;
		InputStream in = null;
		try {
			urlConn = url.openConnection();
		} catch (IOException ioe) {
			return 0;
		}

		try {
			in = urlConn.getInputStream();

			long currentTime = System.currentTimeMillis();

			Long previous = lastCheck.get(urlString);
			previous = previous == null ? 0 : previous;

			boolean timeExpired = currentTime -  previous > 600000; // Recheck every 10 mins

			long urlLastModified = 0;

			if (timeExpired) {
				urlLastModified = urlConn.getLastModified();
				lastCheck.put(urlString, currentTime);
			}

			boolean cacheHit = crc != 0;
			boolean notUpdated = urlLastModified == modified && modified != 0;

			if (cacheHit && (!timeExpired || notUpdated)) {
				//System.out.println("Cached");
				return crc;
			} else {
				crc = FileUtil.getCRC(in, buffer);
				info = urlLastModified + ":" + crc;
				if (urlCRCStore == null) {
					System.out.println("[SpoutPlugin] Attempted to create CRC for URL " + urlString + " before urlCRCStore was initialized!");
					return 0L;
				}
				synchronized(urlCRCStoreSync) {
					urlCRCStore.set(key, info);
					urlCRCStore.save();
				}
				return crc;
			}
		} catch (IOException ioe) {
			crc = FileUtil.getCRC(in, buffer);
			synchronized(urlCRCStoreSync) {
				urlCRCStore.remove(key);
				urlCRCStore.save();
			}
			return crc;
		} finally {
			if (in != null) {
				try {
					in.close();
				} catch (IOException e) {
				}
			}
		}
	}

	private static ConcurrentHashMap<String,Thread> CRCDownloads = new ConcurrentHashMap<String,Thread>();

	public static class URLCheck extends Thread {
		final String url;
		final CRCStoreRunnable runnable;
		final byte[] buffer;

		public URLCheck(String url, byte[] buffer, CRCStoreRunnable runnable) {
			this.url = url;
			this.runnable = runnable;
			this.buffer = buffer;
		}

		public void run() {
			Thread downloadThread = CRCDownloads.get(url);

			if (downloadThread == null) {
				Thread old = CRCDownloads.putIfAbsent(url, this);
				if (old != null) {
					downloadThread = old;
				} else {
					downloadThread = this;
				}
			}

			if (downloadThread != this) {
				try {
					downloadThread.join();
				} catch (InterruptedException e) {
				}
			}

			Long crc = null;
			crc = CRCStore.getCRC(url, buffer);

			if (crc == null) {
				crc = 0L;
			}

			CRCDownloads.remove(url, this);

			if (runnable != null) {
				runnable.setCRC(crc);
				runnable.run();
			}
		}
	}
}
