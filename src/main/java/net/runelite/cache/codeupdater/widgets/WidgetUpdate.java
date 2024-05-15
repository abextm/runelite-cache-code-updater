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
package net.runelite.cache.codeupdater.widgets;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import net.runelite.cache.InterfaceManager;
import net.runelite.cache.codeupdater.Main;
import net.runelite.cache.codeupdater.git.GitUtil;
import net.runelite.cache.codeupdater.git.MutableCommit;
import net.runelite.cache.codeupdater.git.Repo;
import net.runelite.cache.codeupdater.mapper.Mapping;
import net.runelite.cache.definitions.InterfaceDefinition;
import org.eclipse.jgit.lib.Repository;

@Slf4j
public class WidgetUpdate
{
	public static void update() throws IOException
	{
		InterfaceManager ifmOld = new InterfaceManager(Main.previous);
		ifmOld.load();
		InterfaceManager ifmNew = new InterfaceManager(Main.next);
		ifmNew.load();

		Repository rl = Repo.RUNELITE.get();
		String interfacesPath = "runelite-api/src/main/interfaces/interfaces.toml";

		INIParser.Document doc = new INIParser(GitUtil.readFileString(rl, Main.branchName, interfacesPath)).parse();
		MutableCommit mc = new MutableCommit("Widget IDs");

		for (var iface : doc.tables.entrySet())
		{
			int group = (int) ((INIParser.IntValueToken) iface.getValue().children.get("id")).intValue;

			InterfaceDefinition[] newIG = getInterface(ifmNew, group);
			if (newIG == null)
			{
				mc.log("lost interface [{}] {}", iface.getKey(), group);
				for (INIParser.Token value : iface.getValue().children.values())
				{
					((INIParser.IntValueToken) value).setValue(-1);
				}
				continue;
			}

			InterfaceDefinition[] oldIG = getInterface(ifmOld, group);
			if (oldIG == null)
			{
				mc.log("nonexistent interface referenced [{}] {}", iface.getKey(), group);
				continue;
			}
			InterfaceDefinition[] oldIGA = ifmOld.getIntefaceGroup(group);

			if (!Arrays.equals(oldIG, newIG))
			{
				log.info("interface [{}] {} changed", iface.getKey(), group);
			}

			Mapping<InterfaceDefinition> mapping = Mapping.of(
				sorted(oldIG),
				sorted(newIG),
				new WidgetMapper());

			for (var childEntry : iface.getValue().children.entrySet())
			{
				if ("id".equals(childEntry.getKey()))
				{
					continue;
				}

				var tok = (INIParser.IntValueToken) childEntry.getValue();
				int child = (int) tok.intValue;
				try
				{
					if (child >= oldIGA.length)
					{
						tok.setValue(-1);
						mc.log("nonexistent widget [{}]{} {}.{}", iface.getKey(), childEntry.getKey(), group, child);
						continue;
					}
					InterfaceDefinition ifd = mapping.getSame().get(oldIGA[child]);
					if (ifd == null)
					{
						tok.setValue(-1);
						mc.log("lost widget [{}]{} {}.{}", iface.getKey(), childEntry.getKey(), group, child);
					}
					else
					{
						tok.setValue(ifd.id & 0xFFFF);
					}
				}
				catch (Exception e)
				{
					mc.log("Error mapping [{}]{} {}.{}: .", iface.getKey(), childEntry.getKey(), group, child, e);
				}
			}
		}

		mc.writeFile(interfacesPath, doc.print());
		mc.finish(rl, Main.branchName);
	}

	private static InterfaceDefinition[] getInterface(InterfaceManager ifm, int ifid)
	{
		var ifaces = ifm.getInterfaces();
		if (ifaces.length <= ifid)
		{
			return null;
		}
		return ifaces[ifid];
	}

	private static List<InterfaceDefinition> sorted(InterfaceDefinition[] defs)
	{
		List<InterfaceDefinition> out = new ArrayList<>();
		sorted(out, defs, -1);
		return out;
	}

	private static void sorted(List<InterfaceDefinition> out, InterfaceDefinition[] defs, int parent)
	{
		for (InterfaceDefinition id : defs)
		{
			if (id.getParentId() != parent)
			{
				continue;
			}

			out.add(id);
			sorted(out, defs, id.getId());
		}
	}
}
