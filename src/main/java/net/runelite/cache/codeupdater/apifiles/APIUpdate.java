/*
 * Copyright (c) 2018 Abex
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
package net.runelite.cache.codeupdater.apifiles;

import java.io.File;
import java.io.IOException;
import net.runelite.cache.ItemManager;
import net.runelite.cache.NpcManager;
import net.runelite.cache.ObjectManager;
import net.runelite.cache.codeupdater.Git;
import net.runelite.cache.fs.Store;

public class APIUpdate
{
	public static void update(Store old, Store neew) throws IOException
	{
		File apiDir = new File("runelite/runelite-api/src/main/java/net/runelite/api");

		{
			ItemManager im = new ItemManager(neew);
			im.load();
			im.java(apiDir);
			Git.runelite.add(new File(apiDir, "ItemID.java"));
			Git.runelite.commitUpdate("Item IDs");
		}

		{
			ObjectManager om = new ObjectManager(neew);
			om.load();
			om.java(apiDir);
			Git.runelite.add(new File(apiDir, "ObjectID.java"));
			Git.runelite.add(new File(apiDir, "NullObjectID.java"));
			Git.runelite.commitUpdate("Object IDs");
		}

		{
			NpcManager nm = new NpcManager(neew);
			nm.load();
			nm.java(apiDir);
			Git.runelite.add(new File(apiDir, "NpcID.java"));
			Git.runelite.commitUpdate("NPC IDs");
		}
	}
}
