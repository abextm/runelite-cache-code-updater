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
package net.runelite.cache.codeupdater.apifiles;

import com.google.common.io.Files;
import java.io.File;
import java.io.IOException;
import net.runelite.cache.ItemManager;
import net.runelite.cache.NpcManager;
import net.runelite.cache.ObjectManager;
import net.runelite.cache.codeupdater.Main;
import net.runelite.cache.codeupdater.git.MutableCommit;
import net.runelite.cache.codeupdater.git.Repo;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;

public class APIUpdate
{
	public static void update() throws IOException, GitAPIException
	{
		Repository repo = Repo.RUNELITE.get();
		String repoPath = "runelite-api/src/main/java/net/runelite/api/";
		File tmp = Files.createTempDir();

		{
			ItemManager im = new ItemManager(Main.next);
			im.load();
			im.java(tmp);
			MutableCommit mc = new MutableCommit("Item IDs");
			mc.writeFileInDir(repoPath, tmp, "ItemID.java");
			mc.writeFileInDir(repoPath, tmp, "NullItemID.java");
			mc.finish(repo, Main.branchName);
		}
		{
			ObjectManager om = new ObjectManager(Main.next);
			om.load();
			om.java(tmp);
			MutableCommit mc = new MutableCommit("Object IDs");
			mc.writeFileInDir(repoPath, tmp, "ObjectID.java");
			mc.writeFileInDir(repoPath, tmp, "NullObjectID.java");
			mc.finish(repo, Main.branchName);
		}

		{
			NpcManager nm = new NpcManager(Main.next);
			nm.load();
			nm.java(tmp);
			MutableCommit mc = new MutableCommit("NPC IDs");
			mc.writeFileInDir(repoPath, tmp, "NpcID.java");
			mc.finish(repo, Main.branchName);
		}
	}
}
