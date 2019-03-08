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
package net.runelite.cache.codeupdater.script;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.hash.Hashing;
import com.google.common.io.BaseEncoding;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import net.runelite.cache.IndexType;
import net.runelite.cache.codeupdater.Main;
import net.runelite.cache.codeupdater.git.GitUtil;
import net.runelite.cache.codeupdater.git.MutableCommit;
import net.runelite.cache.codeupdater.git.Repo;
import net.runelite.cache.codeupdater.mapper.Mapping;
import net.runelite.cache.definitions.loaders.ScriptLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.script.assembler.Assembler;
import net.runelite.cache.script.disassembler.Disassembler;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;

@Slf4j
public class ScriptUpdate
{
	public static void update() throws IOException, GitAPIException
	{
		ScriptLoader loader = new ScriptLoader();

		String root = "runelite-client/src/main/scripts";
		Repository rl = Repo.RUNELITE.get();

		MutableCommit mc = new MutableCommit("Scripts");
		MutableCommit oldDelta = new MutableCommit("Old vanilla ", false);
		MutableCommit newDelta = new MutableCommit("New vanilla ", false);

		Main.execAllAndWait(GitUtil.listDirectory(rl, Main.branchName, root, n -> n.endsWith(".hash"))
			.entrySet()
			.stream()
			.map(ent -> () -> {
				String hashFile = ent.getKey();
				String scriptFile = hashFile.replace(".hash", ".rs2asm");
				String scriptFilePath = GitUtil.pathJoin(root, scriptFile);

				byte[] rs2asmSrc = GitUtil.readFile(rl, Main.branchName, scriptFilePath);
				ScriptSource oldModSource = new ScriptSource(new String(rs2asmSrc));

				int id = Integer.parseInt(oldModSource.getHeader().get(".id"));

				String thash;
				try (ObjectReader or = rl.newObjectReader())
				{
					thash = new String(or.open(ent.getValue()).getBytes()).trim();
				}

				byte[] oData = get(Main.previous, id);
				String ohash = BaseEncoding.base16().encode(Hashing.sha256().hashBytes(oData).asBytes());

				if (!thash.equals(ohash))
				{
					mc.log("old cache hash does not match hash in tree. {} != {}", ohash, thash);
					return;
				}

				byte[] nData = get(Main.next, id);
				String nhash = BaseEncoding.base16().encode(Hashing.sha256().hashBytes(nData).asBytes());

				if (nhash.equals(ohash))
				{
					return;
				}

				Disassembler disassembler = new Disassembler();

				String oldSrcStr = disassembler.disassemble(loader.load(id, oData));
				ScriptSource oldSource = new ScriptSource(oldSrcStr);
				oldDelta.writeFile(scriptFilePath, oldSrcStr.getBytes());

				String newSrcStr = disassembler.disassemble(loader.load(id, nData));
				ScriptSource newSource = new ScriptSource(newSrcStr);
				newDelta.writeFile(scriptFilePath, newSrcStr.getBytes());

				String newModSource = updateScript(oldSource, newSource, oldModSource);

				// Just make sure it atleast assembles still
				try
				{
					Assembler assembler = new Assembler(RuneLiteInstructions.instance);
					assembler.assemble(new ByteArrayInputStream(newModSource.getBytes()));
				}
				catch (Exception e)
				{
					mc.log("Updated script does not assemble {}", scriptFile, e);
				}

				mc.writeFile(GitUtil.pathJoin(root, hashFile), nhash.getBytes());
				mc.writeFile(scriptFilePath, newModSource.getBytes());
				log.info("Updated script {}", scriptFile);
			}));

		try (Git git = new Git(rl))
		{
			String newBranch = Main.branchName + "-previous";
			git.branchCreate()
				.setForce(true)
				.setName(newBranch)
				.setStartPoint(Main.branchName)
				.call();
			oldDelta.finish(rl, newBranch);
			GitUtil.pushBranch(rl, newBranch);
		}
		mc.finish(rl, Main.branchName);

		try (Git git = new Git(rl))
		{
			String newBranch = Main.branchName + "-next";
			git.branchCreate()
				.setForce(true)
				.setName(newBranch)
				.setStartPoint(Main.branchName)
				.call();
			newDelta.finish(rl, newBranch);
			GitUtil.pushBranch(rl, newBranch);
		}
	}

	@VisibleForTesting
	static String updateScript(ScriptSource oldS, ScriptSource newS, ScriptSource oldM)
	{
		Mapping<ScriptSource.Line> osDom = Mapping.of(oldS.getLines(), oldM.getLines(), new ScriptSourceMapper());
		Mapping<ScriptSource.Line> osDns = Mapping.of(oldS.getLines(), newS.getLines(), new ScriptSourceMapper());

		StringBuilder out = new StringBuilder();
		out.append(oldM.getPrelude());

		for (Map.Entry<String, String> hf : oldM.getHeader().entrySet())
		{
			out.append(String.format("%-19s %s\n", hf.getKey(), hf.getValue()));
		}

		Multimap<ScriptSource.Line, ScriptSource.Line> insertions = MultimapBuilder.hashKeys().arrayListValues().build();
		AtomicReference<ScriptSource.Line> lastOld = new AtomicReference<>(null);
		osDom.forEach((o, n) ->
		{
			if (o == null)
			{
				insertions.put(lastOld.get(), n);
			}
			else
			{
				lastOld.set(o);
			}
		});

		for (ScriptSource.Line l : insertions.get(null))
		{
			out.append(l.format(false, Function.identity())).append("\n");//TODO:
		}
		insertions.removeAll(null);

		osDns.forEach((o, n) ->
		{
			if (n != null)
			{
				out.append(n.format(false, Function.identity()));
				ScriptSource.Line oml = osDom.getSame().get(o);
				if (oml != null && oml.getComment() != null)
				{
					out.append(oml.getComment());
				}
				out.append("\n");
			}

			for (ScriptSource.Line l : insertions.get(o))
			{
				out.append(l.format(false, Function.identity())).append("\n");//TODO:
			}
		});

		return out.toString();
	}

	private static byte[] get(Store store, int id) throws IOException
	{
		Storage storage = store.getStorage();
		Index index = store.getIndex(IndexType.CLIENTSCRIPT);
		Archive archive = index.getArchive(id);
		byte[] compressed = storage.loadArchive(archive);
		return archive.decompress(compressed);
	}
}
