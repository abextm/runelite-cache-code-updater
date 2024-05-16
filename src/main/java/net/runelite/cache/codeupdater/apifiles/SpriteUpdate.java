/*
 * Copyright (c) 2024 Abex
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

import java.io.IOException;
import net.runelite.cache.IndexType;
import net.runelite.cache.codeupdater.JavaIDList;
import net.runelite.cache.codeupdater.Main;
import net.runelite.cache.definitions.SpriteDefinition;
import net.runelite.cache.definitions.loaders.SpriteLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;

public class SpriteUpdate
{
	public static void update() throws IOException
	{
		Index spriteIndex = Main.next.getIndex(IndexType.SPRITES);
		SpriteLoader loader = new SpriteLoader();

		JavaIDList.update("Sprite IDs", "runelite-api/src/main/java/net/runelite/api/SpriteID.java", (mc, l) ->
		{
			int id = l.intValue();

			Archive ar = spriteIndex.getArchive(id);

			if (ar == null)
			{
				l.delete();
				mc.log("lost sprite {} {}", l.getName(), id);
				return;
			}

			byte[] contents = ar.decompress(Main.next.getStorage().loadArchive(ar));
			SpriteDefinition[] sd = loader.load(id, contents);
			if (sd.length == 0 || (sd.length == 1 && sd[0].getWidth() == 0 && sd[0].getHeight() == 0))
			{
				l.delete();
				mc.log("lost sprite {} {}", l.getName(), id);
				return;
			}
		});
	}
}
