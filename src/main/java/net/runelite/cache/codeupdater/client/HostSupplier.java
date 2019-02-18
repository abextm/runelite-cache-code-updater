/*
 * Copyright (c) 2019 Abex
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND
 * ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package net.runelite.cache.codeupdater.client;

import com.google.common.collect.ImmutableList;
import java.io.DataInputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Queue;
import lombok.extern.slf4j.Slf4j;
import net.runelite.cache.codeupdater.git.GitUtil;

@Slf4j
public class HostSupplier
{
	private static class World
	{
		String hostname;
		int location;
		int flags;
		int players;
	}

	private static final long HOST_EXPIRE = 5 * 60 * 1000;

	private static final List<String> FALLBACK_WORLDS = ImmutableList.of(
		"oldschool1.runescape.COM",
		"oldschool2.runescape.COM",
		"oldschool3.runescape.COM",
		"oldschool4.runescape.COM",
		"oldschool5.runescape.COM",
		"oldschool6.runescape.COM",
		"oldschool7.runescape.COM",
		"oldschool8.runescape.COM"
	);

	private int randomWorld = 0;

	private Queue<String> hosts = null;
	private long lastHostUpdate = 0;

	public synchronized String getHost(boolean forceRefresh)
	{
		long now = System.currentTimeMillis();
		if (!forceRefresh && lastHostUpdate > now - HOST_EXPIRE && !hosts.isEmpty())
		{
			return hosts.poll();
		}
		int preferredLocationCode = Integer.parseInt(GitUtil.envOr("LOCATION_CODE", "0"));
		ArrayList<World> worlds = new ArrayList<>();
		for (int attempt = 0; attempt < 10; attempt++)
		{
			log.info("Querying worldlist for servers");
			try
			{
				URLConnection urlConn = new URL("http://www.runescape.com/g=oldscape/slr.ws?order=P")
					.openConnection();
				urlConn.setConnectTimeout(2500);
				urlConn.setReadTimeout(1500);
				urlConn.setRequestProperty("User-Agent", "RuneLite-Cache-Code-Autoupdater/1.0 (+" + GitUtil.getOwner() + ")");
				try (DataInputStream ds = new DataInputStream(urlConn.getInputStream()))
				{
					ds.readInt(); // len
					int len = ds.readUnsignedShort();
					if (len <= 0)
					{
						log.info("Got no servers, trying again");
						Thread.sleep(1000);
						continue;
					}
					worlds.ensureCapacity(len);
					for (int i = 0; i < len; i++)
					{
						World w = new World();
						ds.readUnsignedShort(); // number
						w.flags = ds.readInt();
						StringBuilder sb = new StringBuilder();
						for (; ; )
						{
							byte b = ds.readByte();
							if (b == 0)
							{
								break;
							}
							sb.append((char) b);
						}
						w.hostname = sb.toString();
						for (; ds.readByte() != 0; )
						{
							// description
						}
						w.location = ds.readByte();
						w.players = ds.readUnsignedShort();
						worlds.add(w);
					}

					worlds.sort(Comparator.comparing((World w) -> w.location == preferredLocationCode)
						.reversed()
						.thenComparing(w -> w.players));

					hosts = new ArrayDeque<>();
					for (World world : worlds)
					{
						if (world.location != preferredLocationCode && hosts.size() > 32)
						{
							break;
						}
						hosts.add(world.hostname);
					}
					lastHostUpdate = now;
					return hosts.poll();
				}
			}
			catch (MalformedURLException e)
			{
				throw new RuntimeException(e);
			}
			catch (Exception e)
			{
				log.info("Unable to download world list", e);
			}
		}

		return FALLBACK_WORLDS.get(randomWorld++ % FALLBACK_WORLDS.size());
	}
}
