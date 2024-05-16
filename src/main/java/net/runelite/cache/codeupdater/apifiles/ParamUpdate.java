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
import net.runelite.cache.ConfigType;
import net.runelite.cache.codeupdater.JavaIDList;
import net.runelite.cache.codeupdater.Main;
import net.runelite.cache.definitions.ParamDefinition;
import net.runelite.cache.definitions.loaders.ParamLoader;

public class ParamUpdate
{
	public static void update() throws IOException
	{
		var loader = new ParamLoader();
		var nextVarb = Main.configLoader(Main.next, ConfigType.PARAMS, (id, d) -> loader.load(d));
		var prevVarb = Main.configLoader(Main.previous, ConfigType.PARAMS, (id, d) -> loader.load(d));

		JavaIDList.update("Param IDs", "runelite-api/src/main/java/net/runelite/api/ParamID.java", (mc, l) ->
		{
			int id = l.intValue();

			ParamDefinition next = nextVarb.apply(id);
			if (next == null)
			{
				l.delete();
				mc.log("lost param {} {}", l.getName(), id);
				return;
			}

			ParamDefinition prev = prevVarb.apply(id);
			if (prev != null)
			{
				if (prev.getType() != next.getType())
				{
					mc.log("param {} {} changed type ({} -> {})", l.getName(), id, prev.getType(), next.getType());
				}
			}
		});
	}
}
