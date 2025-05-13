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
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import lombok.extern.slf4j.Slf4j;
import net.runelite.cache.codeupdater.Settings;
import net.runelite.cache.codeupdater.git.GitUtil;
import net.runelite.cache.codeupdater.git.MutableCommit;
import net.runelite.cache.codeupdater.git.Repo;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.fs.flat.FlatStorage;
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
		Repository repo = Repo.OSRS_CACHE.get();
		String beta = Settings.get("dl.beta");

		String branch = Settings.get("dl.branch");

		var builder = new JS5Client.Builder()
			.fromConfig();

		if (!branch.isEmpty())
		{
			Repo.OSRS_CACHE.branch(branch);

			if (builder.rev() == 0)
			{
				builder.rev(UpdateHandler.extractRevision(repo, branch));
			}
		}

		log.info("Loading store");
		MutableCommit commit = new MutableCommit("Update cache", false);
		Store store;
		if (!branch.isEmpty())
		{
			store = GitUtil.openStore(repo, branch, commit);
		}
		else
		{
			String dir = Settings.get("dl.dir");
			if (dir.isEmpty())
			{
				throw new IllegalArgumentException("must set dl.dir or dl.branch");
			}

			File fdir = new File(dir);
			Storage s;
			if (new File(fdir, "main_file_cache.dat2").exists())
			{
				s = new DiskStorage(fdir);
			}
			else if (new File(fdir, "0.flatcache").exists())
			{
				s = new FlatStorage(fdir);
			}
			else
			{
				fdir.mkdirs();
				String dirmode = Settings.get("dl.dirmode");
				if ("flat".equals(dirmode))
				{
					s = new FlatStorage(fdir);
				}
				else
				{
					s = new DiskStorage(fdir);
				}
			}

			store = new Store(s);
			store.load();
		}

		builder.store(store);

		boolean hostSet = builder.hostname() != null;

		for (; ; )
		{
			String tag = "oops";
			Queue<Integer> todo = new ArrayDeque<>();
			todo.add(0xFF00FF);
			HostSupplier hs = new HostSupplier(!beta.isEmpty());
			for (int attempt = 0; ; attempt++)
			{
				JS5Client jsc = null;
				try
				{
					if (!hostSet)
					{
						String host = hs.getHost(attempt % 16 == 0);
						if (host == null)
						{
							Thread.sleep(5000);
							continue;
						}

						builder.hostname(host);
					}

					jsc = new JS5Client(builder);
					builder.rev(jsc.getRev());
					jsc.toDownload = todo;
					tag = UpdateHandler.calculateTag(repo, jsc.getRev(), beta);

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
				catch (ConnectException | SocketTimeoutException e)
				{
					log.info("Error downloading cache {}", (Object) e);
					Thread.sleep(5000);
				}
				catch (Exception e)
				{
					log.info("Error downloading cache", e);
					Thread.sleep(5000);
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

			log.info("Writing");

			store.save();

			if (!branch.isEmpty())
			{
				commit.setSubject("Cache version " + tag + (!beta.isEmpty() ? (" (" + beta + " beta)") : ""));
				commit.finish(repo, branch);
				commit.clear();

				if (Repo.OSRS_CACHE.isHasOrigin() && Settings.getBool("git.push.allowed"))
				{
					log.info("Pushing");

					try (Git git = new Git(repo))
					{
						List<RefSpec> specs = new ArrayList<>();
						specs.add(new RefSpec(branch + ":" + branch));

						specs.add(new RefSpec(git.tag()
							.setName(tag)
							.setObjectId(GitUtil.resolve(repo, branch))
							.call()
							.getName()));

						git.push()
							.setRemote("origin")
							.setRefSpecs(specs)
							.setThin(true)
							.setProgressMonitor(new TextProgressMonitor())
							.call();
					}

					String exec = Settings.get("dl.after_push_script");
					if (!Strings.isNullOrEmpty(exec))
					{
						Runtime.getRuntime().exec(exec, new String[]{"IDENT=" + tag});
					}
				}
			}

			Runtime.getRuntime().gc();

			if (branch.isEmpty())
			{
				// one shot download
				break;
			}
		}
	}
}
