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
package net.runelite.cache.codeupdater;

import java.io.File;
import java.io.IOException;
import net.runelite.cache.codeupdater.apifiles.APIUpdate;
import net.runelite.cache.codeupdater.script.ScriptUpdate;
import net.runelite.cache.codeupdater.widgets.WidgetUpdate;
import net.runelite.cache.fs.Store;
import net.runelite.cache.fs.flat.FlatStorage;

public class Main
{
	public static void main(String[] args) throws IOException
	{
		File CACHE_DIR = new File("osrs-cache");
		Git.cache.setWorkingDirectory(CACHE_DIR);

		Store neew = new Store(new FlatStorage(CACHE_DIR));
		neew.load();

		Git.cache.checkout("HEAD^");
		Store old = new Store(new FlatStorage(CACHE_DIR));
		old.load();

		Git.runelite.setWorkingDirectory(new File("runelite"));
		Git.runelite.hardReset();
		if (args.length > 0)
		{
			Git.versionString = args[0];
			String branchname = "cache-code-" + Git.versionString.replace(' ','-').toLowerCase();
			Git.runelite.branch(branchname);
		}
		else
		{
			Git.runelite.setLive(false);
		}

		APIUpdate.update(old, neew);
		WidgetUpdate.update(old, neew);
		ScriptUpdate.update(old, neew);
	}
}
