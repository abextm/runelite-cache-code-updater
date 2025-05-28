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
package net.runelite.cache.codeupdater.git;

import com.google.common.base.Strings;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;
import lombok.extern.slf4j.Slf4j;
import net.runelite.cache.fs.Store;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.CanonicalTreeParser;
import org.eclipse.jgit.treewalk.TreeWalk;

@Slf4j
public final class GitUtil
{
	private GitUtil()
	{
	}

	public static Store openStore(Repository repo, String commitish) throws IOException
	{
		return openStore(repo, commitish, null);
	}

	public static Store openStore(Repository repo, String commitish, MutableCommit commit) throws IOException
	{
		GitFlatStorage fs = new GitFlatStorage(repo, commitish, commit);
		Store store = new Store(fs);
		store.load();

		return store;
	}

	public static RevCommit resolve(Repository repo, String commitish) throws IOException
	{
		return repo.parseCommit(repo.resolve(commitish));
	}

	public static Map<String, ObjectId> listDirectory(Repository repo, String commitish, String path, Predicate<String> filter) throws IOException
	{
		Map<String, ObjectId> out = new HashMap<>();
		RevCommit commit = resolve(repo, commitish);
		TreeWalk tw = null;
		int depth = 0;
		try (ObjectReader or = repo.newObjectReader())
		{
			if (Strings.isNullOrEmpty(path))
			{
				tw = new TreeWalk(repo, or);
				tw.reset(commit.getTree());
				tw.setRecursive(false);
			}
			else
			{
				tw = TreeWalk.forPath(repo, or, path, commit.getTree());
				tw.enterSubtree();
				depth = tw.getDepth();
			}
			for (; tw.next() && tw.getDepth() == depth; )
			{
				if (tw.isSubtree())
				{
					continue;
				}
				String name = tw.getNameString();
				if (filter.test(name))
				{
					out.put(name, tw.getObjectId(0));
				}
			}
		}
		finally
		{
			if (tw != null)
			{
				tw.close();
			}
		}
		return out;
	}

	public static byte[] readFile(Repository repo, String commitish, String path) throws IOException
	{
		RevCommit commit = resolve(repo, commitish);
		try (TreeWalk tw = TreeWalk.forPath(repo, path, commit.getTree()))
		{
			if (tw == null)
			{
				return null;
			}

			return tw.getObjectReader().open(tw.getObjectId(0)).getBytes();
		}
	}

	public static String readFileString(Repository repo, String commitish, String path) throws IOException
	{
		byte[] b = readFile(repo, commitish, path);
		if (b == null)
		{
			return null;
		}

		return new String(b, StandardCharsets.UTF_8);
	}

	public static String pathJoin(String a, String b)
	{
		if (a.endsWith("/"))
		{
			return a + b;
		}
		return a + "/" + b;
	}

	public static void diff(Repository repo, String commita, String commitb) throws GitAPIException, IOException
	{
		try (Git git = new Git(repo);
			ObjectReader or = repo.newObjectReader())
		{
			CanonicalTreeParser tpa = new CanonicalTreeParser();
			tpa.reset(or, resolve(repo, commita).getTree());

			CanonicalTreeParser tpb = new CanonicalTreeParser();
			tpb.reset(or, resolve(repo, commitb).getTree());

			git.diff()
				.setOutputStream(System.out)
				.setOldTree(tpa)
				.setNewTree(tpb)
				.call();
		}
	}
}
