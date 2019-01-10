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
import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.URIish;

@Slf4j
@RequiredArgsConstructor
public enum Repo
{
	OSRS_CACHE("https://github.com/Abextm/osrs-cache.git", "git@github.com:Abextm/osrs-cache.git", "master"),
	RUNELITE("https://github.com/runelite/runelite.git", "git@github.com:Abextm/runelite.git", "master"),
	SRN("https://github.com/runelite/static.runelite.net.git", "git@github.com:Abextm/static.runelite.net.git", "gh-pages");

	private final String upstream;
	private final String push;
	private final String masterBranch;

	private Repository repo;

	public Repository get() throws IOException
	{
		if (repo != null)
		{
			return repo;
		}

		try
		{
			String name = name().toLowerCase();
			File localCache = new File(name);

			repo = new FileRepositoryBuilder()
				.setWorkTree(localCache)
				.build();

			if (Strings.isNullOrEmpty(System.getenv("NO_NETWORK")))
			{
				try (Git git = new Git(repo))
				{
					if (git.remoteList().call().size() <= 0)
					{
						repo.create();
					}

					setRemote(git, name, "upstream", upstream);
					setRemote(git, name, "origin", push);

					log.info("Updating {}", name);
					git.fetch()
						.setProgressMonitor(new TextProgressMonitor())
						.setRemote("upstream")
						.call();
					log.info("Done");
				}
			}

			return repo;
		}
		catch (GitAPIException e)
		{
			throw new AssertionError(e);
		}
	}

	private static void setRemote(Git git, String repoName, String remoteName, String defaul)
	{
		String remoteURI = GitUtil.envOr("REMOTE_" + repoName.toUpperCase() + "_" + remoteName.toUpperCase(), defaul);
		if ("none".equals(remoteURI))
		{
			return;
		}
		try
		{
			git.remoteAdd()
				.setName(remoteName)
				.setUri(new URIish(remoteURI))
				.call();
		}
		catch (GitAPIException | URISyntaxException e)
		{
			throw new RuntimeException(e);
		}
	}

	public static void closeAll()
	{
		for (Repo repo : values())
		{
			if (repo.repo != null)
			{
				repo.repo.close();
			}
		}
	}

	public void branch(String branchName) throws GitAPIException, IOException
	{
		try (Git git = new Git(get()))
		{
			git.branchCreate()
				.setForce(true)
				.setName(branchName)
				.setStartPoint(GitUtil.envOr("BRANCH_POINT_" + name(), "upstream/" + masterBranch))
				.call();
		}
	}
}
