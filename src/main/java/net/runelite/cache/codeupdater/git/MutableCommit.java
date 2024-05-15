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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import lombok.Setter;
import net.runelite.cache.codeupdater.Main;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheEditor;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.helpers.MessageFormatter;

public class MutableCommit
{
	private static long lastCommitTime = 0;
	private static final Logger logger = LoggerFactory.getLogger(MutableCommit.class);

	@Setter
	private String subject;

	public MutableCommit(String subject)
	{
		this(subject, true);
	}

	public MutableCommit(String subject, boolean includeUpdate)
	{
		if (includeUpdate)
		{
			this.subject = "Update " + subject + " to " + Main.versionText;
		}
		else
		{
			this.subject = subject;
		}
	}

	private final StringBuilder log = new StringBuilder();
	private final Map<String, byte[]> files = Collections.synchronizedMap(new HashMap<>());

	public void log(String line)
	{
		synchronized (log)
		{
			log.append(line).append("\n");
		}
		logger.info("> {}", line);
	}

	public void log(String fmt, Object... args)
	{
		log(MessageFormatter.arrayFormat(fmt, args).getMessage());
	}

	public void writeFile(String path, byte[] contents)
	{
		files.put(path, contents);
	}

	public void writeFile(String path, String contents)
	{
		files.put(path, contents.getBytes(StandardCharsets.UTF_8));
	}

	public void removeFile(String path)
	{
		files.put(path, null);
	}

	public OutputStream writeFile(String path)
	{
		return new ByteArrayOutputStream()
		{
			@Override
			public void close()
			{
				writeFile(path, toByteArray());
			}
		};
	}

	public void writeFile(String path, File file) throws IOException
	{
		byte[] data = Files.readAllBytes(file.toPath());
		writeFile(path, data);
	}

	public void writeFileInDir(String path, File directory, String name) throws IOException
	{
		writeFile(GitUtil.pathJoin(path, name), new File(directory, name));
	}

	public ObjectId finish(Repository repo, ObjectId parent) throws IOException
	{
		try (ObjectInserter inser = repo.newObjectInserter())
		{
			ObjectId parentTreeId = repo.parseCommit(parent).getTree().getId();
			DirCache index;
			if (parent == null)
			{
				index = DirCache.newInCore();
			}
			else
			{
				try (ObjectReader or = repo.newObjectReader())
				{
					index = DirCache.read(or, parentTreeId);
				}
			}

			DirCacheEditor indexBuilder = index.editor();
			for (Map.Entry<String, byte[]> file : files.entrySet())
			{
				if (file.getValue() == null)
				{
					indexBuilder.add(new DirCacheEditor.DeletePath(file.getKey()));
					continue;
				}
				ObjectId blob = inser.insert(Constants.OBJ_BLOB, file.getValue());
				indexBuilder.add(new PathEdit(file.getKey(), e -> {
					e.setObjectId(blob);
					e.setFileMode(FileMode.REGULAR_FILE);
					long now = System.currentTimeMillis();
					synchronized (MutableCommit.class)
					{
						// Some (GitKraken) breaks if commits are less than 1 second apart
						// So commit stuff into the future if we are committing too fast
						now = Math.max(now, lastCommitTime + 1000);
						lastCommitTime = now;
					}
					e.setLastModified(now);
				}));
			}
			indexBuilder.finish();

			ObjectId tree = index.writeTree(inser);
			if (tree.equals(parentTreeId) && log.length() == 0)
			{
				// Empty commit
				return null;
			}

			CommitBuilder cb = new CommitBuilder();
			cb.setParentId(parent);
			cb.setTreeId(tree);
			cb.setMessage(subject + "\n\n" + log.toString());

			PersonIdent author = new PersonIdent("RuneLite Cache-Code Autoupdater", GitUtil.getOwner());
			cb.setAuthor(author);
			cb.setCommitter(author);

			ObjectId newCommit = inser.insert(cb);
			logger.info("Wrote commit for {} ({})", subject, newCommit.abbreviate(10).name());
			return newCommit;
		}
	}

	public void finish(Repository repo, String branch) throws IOException
	{
		synchronized (repo)
		{
			ObjectId commitish = repo.resolve(branch);
			repo.parseCommit(commitish);
			ObjectId newCommit = finish(repo, commitish);
			if (newCommit == null)
			{
				return;
			}

			RefUpdate ru = repo.updateRef("refs/heads/" + branch);
			ru.setNewObjectId(newCommit);
			ru.setRefLogMessage("commit (rlccau)", false);
			ru.setExpectedOldObjectId(commitish);
			ru.forceUpdate();
		}
	}

	public void clear()
	{
		log.setLength(0);
		log.trimToSize();
		files.clear();
	}
}
