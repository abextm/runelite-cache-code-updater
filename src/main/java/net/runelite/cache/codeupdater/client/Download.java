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

import com.google.common.base.Strings;
import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import lombok.extern.slf4j.Slf4j;
import net.runelite.cache.codeupdater.git.GitUtil;
import net.runelite.cache.codeupdater.git.MutableCommit;
import net.runelite.cache.codeupdater.git.Repo;
import net.runelite.cache.fs.Store;
import net.runelite.cache.fs.jagex.DiskStorage;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.transport.RefSpec;

@Slf4j
public class Download
{
	public static void main(String[] args) throws Exception
	{
		String mode = GitUtil.envOr("DOWNLOAD_MODE", "TEST");
		switch (mode)
		{
			case "TEST":
				doSingle(true);
				break;
			case "RUN":
				doSingle(false);
				break;
			case "JAGEX":
				doJagex(new File(args[0]), Integer.parseInt(args[1]));
				break;
			default:
				throw new IllegalArgumentException(mode + " is not a valid DOWNLOAD_MODE");
		}
	}

	private static void doJagex(File path, int rev) throws Exception
	{
		path.mkdirs();
		Store store = new Store(new DiskStorage(path));
		HostSupplier hs = new HostSupplier();
		JS5Client jsc = new JS5Client(store, hs.getHost(false), rev, false);
		jsc.enqueueRoot();
		jsc.process();
		store.save();
	}

	private static void doSingle(boolean test) throws Exception
	{
		Repository repo = Repo.OSRS_CACHE.get();
		String branch = "master";
		if (test)
		{
			branch = "test";
		}

		try (Git git = new Git(repo))
		{
			String branchPoint = test ? GitUtil.envOr("TEST_POINT", "upstream/master^") : "upstream/master";
			git.branchCreate()
				.setForce(true)
				.setName(branch)
				.setStartPoint(branchPoint)
				.call();
		}

		int oldRev = UpdateHandler.extractRevision(repo, branch);

		log.info("Loading store");
		MutableCommit commit = new MutableCommit("Update cache", false);
		Store store = GitUtil.openStore(repo, branch, commit);

		for (; ; )
		{
			String tag = "oops";
			Queue<Integer> todo = new ArrayDeque<>();
			todo.add(0xFF00FF);
			HostSupplier hs = new HostSupplier();
			for (int attempt = 0; ; attempt++)
			{
				JS5Client jsc = null;
				try
				{
					String host = hs.getHost(attempt % 16 == 0);
					jsc = new JS5Client(store, host, oldRev, false);
					jsc.toDownload = todo;
					tag = UpdateHandler.calculateTag(repo, jsc.getRev());

					for (; ; )
					{
						jsc.process();
						if (jsc.hasSeenChange())
						{
							break;
						}
						Thread.sleep(5000);
						jsc.enqueueRoot();
					}
				}
				catch (Exception e)
				{
					log.info("Error downloading cache", e);
				}
				finally
				{
					try
					{
						if (jsc != null)
						{
							jsc.close();
						}
					}
					catch (Exception e)
					{
						log.warn("Error closing connection", e);
					}
				}

				if (jsc != null && jsc.hasSeenChange())
				{
					if (todo.isEmpty() && jsc.getUnreceivedRequests().isEmpty())
					{
						break;
					}
					todo.addAll(jsc.getUnreceivedRequests());
				}
			}

			log.info("Writing commit");
			store.save();
			commit.setSubject("Cache version " + tag);
			commit.finish(repo, branch);
			commit.clear();

			if (Strings.isNullOrEmpty(System.getenv("NO_NETWORK")) &&
				Strings.isNullOrEmpty(System.getenv("NO_PUSH")))
			{
				log.info("Pushing");

				try (Git git = new Git(repo))
				{
					List<RefSpec> specs = new ArrayList<>();
					specs.add(new RefSpec(branch + ":" + branch));

					if (!test)
					{
						specs.add(new RefSpec(git.tag()
							.setName(tag)
							.setObjectId(GitUtil.resolve(repo, branch))
							.call()
							.getName()));
					}

					git.push()
						.setRemote("origin")
						.setRefSpecs(specs)
						.setThin(true)
						.setProgressMonitor(new TextProgressMonitor())
						.setForce(test)
						.call();
				}
			}
			log.info("Done");

			if (test)
			{
				log.info("Diff:");
				GitUtil.diff(repo, "upstream/master", branch);
				break;
			}
			else
			{
				String exec = System.getenv("AFTER_PUSH");
				if (!Strings.isNullOrEmpty(exec))
				{
					Runtime.getRuntime().exec(exec, new String[]{"IDENT=" + tag});
				}
			}

			Runtime.getRuntime().gc();
		}
	}
}
