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
package net.runelite.cache.codeupdater;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import net.runelite.cache.codeupdater.apifiles.APIUpdate;
import net.runelite.cache.codeupdater.apifiles.ItemVariationsUpdate;
import net.runelite.cache.codeupdater.git.GitUtil;
import net.runelite.cache.codeupdater.git.Repo;
import net.runelite.cache.codeupdater.script.ScriptIDUpdate;
import net.runelite.cache.codeupdater.script.ScriptUpdate;
import net.runelite.cache.codeupdater.srn.SRNUpdate;
import net.runelite.cache.codeupdater.widgets.WidgetUpdate;
import net.runelite.cache.fs.Store;
import net.runelite.cache.fs.jagex.DiskStorage;
import org.eclipse.jgit.lib.Repository;

@Slf4j
public class Main
{
	public static Store next;
	public static Store previous;

	public static String branchName;
	public static String versionText;

	public static ExecutorService exec = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors() + 2);

	public static Gson GSON = new GsonBuilder()
		.disableHtmlEscaping()
		.setPrettyPrinting()
		.create();

	public static void main(String[] args) throws Exception
	{
		try
		{
			String oneline;
			if (args.length != 0)
			{
				next = new Store(new DiskStorage(new File(args[0])));
				next.load();
				oneline = args[1];

				String prevCommitish = GitUtil.envOr("CACHE_PREVIOUS", "upstream/master");
				previous = GitUtil.openStore(Repo.OSRS_CACHE.get(), prevCommitish);
			}
			else
			{
				String nextCommitish = GitUtil.envOr("CACHE_NEXT", "upstream/master");
				next = GitUtil.openStore(Repo.OSRS_CACHE.get(), nextCommitish);
				oneline = GitUtil.resolve(Repo.OSRS_CACHE.get(), nextCommitish).getShortMessage();

				String prevCommitish = GitUtil.envOr("CACHE_PREVIOUS", "upstream/master^");
				previous = GitUtil.openStore(Repo.OSRS_CACHE.get(), prevCommitish);
			}

			versionText = oneline.replace("Cache version ", "");
			branchName = GitUtil.envOr("BRANCH_NAME", "cache-code-" + versionText);

			updateRunelite();

			updateSRN();
		}
		finally
		{
			exec.shutdown();
			exec.awaitTermination(5000, TimeUnit.MILLISECONDS);
			Repo.closeAll();
		}
	}

	private static void updateRunelite() throws Exception
	{
		log.info("Starting runelite update on branch {}", branchName);
		Repository rl = Repo.RUNELITE.get();
		Repo.RUNELITE.branch(branchName);

		execAllAndWait(
			APIUpdate::update,
			ItemVariationsUpdate::update,
			WidgetUpdate::update,
			ScriptUpdate::update,
			ScriptIDUpdate::update
		);

		GitUtil.pushBranch(rl, branchName);
	}

	private static void updateSRN() throws Exception
	{
		log.info("Starting static.runelite.net update on branch {}", branchName);
		Repository srn = Repo.SRN.get();
		Repo.SRN.branch(branchName);

		SRNUpdate.update();

		GitUtil.pushBranch(srn, branchName);
	}

	public interface RunAndThrow
	{
		void run() throws Exception;
	}

	public static <T extends Throwable> void execAllAndWait(RunAndThrow... runnables) throws T
	{
		execAllAndWait(Stream.of(runnables));
	}

	public static <T extends Throwable> void execAllAndWait(Stream<RunAndThrow> runnables) throws T
	{
		List<Future<?>> futs = runnables.map(r -> exec.submit(() ->
		{
			r.run();
			return null;
		})).collect(Collectors.toList());
		for (Future<?> fut : futs)
		{
			try
			{
				fut.get();
			}
			catch (ExecutionException e)
			{
				throw (T) e.getCause();
			}
			catch (InterruptedException e)
			{
				throw new RuntimeException(e);
			}
		}
	}
}
