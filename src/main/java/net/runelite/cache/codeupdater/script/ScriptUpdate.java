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
package net.runelite.cache.codeupdater.script;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.hash.Hashing;
import com.google.common.io.BaseEncoding;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.ref.Reference;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import lombok.extern.slf4j.Slf4j;
import net.runelite.cache.IndexType;
import net.runelite.cache.codeupdater.Git;
import net.runelite.cache.codeupdater.mapper.Mapping;
import net.runelite.cache.definitions.loaders.ScriptLoader;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.script.Instruction;
import net.runelite.cache.script.assembler.Assembler;

@Slf4j
public class ScriptUpdate
{
	public static void update(Store old, Store neew) throws IOException
	{
		ScriptLoader loader = new ScriptLoader();
		ExecutorService tp = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

		File scriptDir = new File("runelite/runelite-client/src/main/scripts");
		List<Future<?>> scripts = new ArrayList<>();
		Object gitLock = new Object();
		for (File hashFile : scriptDir.listFiles((dir, name) -> name.endsWith(".hash")))
		{
			File scriptFile = new File(hashFile.getPath().replace(".hash", ".rs2asm"));
			scripts.add(tp.submit(() ->
			{
				try
				{
					ScriptSource oldModSource = new ScriptSource(new String(Files.readAllBytes(scriptFile.toPath())));
					int id = Integer.parseInt(oldModSource.getHeader().get(".id"));

					String thash = new String(Files.readAllBytes(hashFile.toPath())).trim();

					byte[] oData = get(old, id);
					String ohash = BaseEncoding.base16().encode(Hashing.sha256().hashBytes(oData).asBytes());

					if (!thash.equals(ohash))
					{
						log.warn("old cache hash does not match hash in tree. {} != {}", ohash, thash);
						return;
					}

					byte[] nData = get(neew, id);
					String nhash = BaseEncoding.base16().encode(Hashing.sha256().hashBytes(nData).asBytes());

					if (nhash.equals(ohash))
					{
						log.info("Not updating script {} because it didn't change hash", scriptFile.getName());
						return;
					}

					ScriptSource oldSource = new ScriptSource(loader.load(id, oData));
					ScriptSource newSource = new ScriptSource(loader.load(id, nData));

					String newModSource = updateScript(oldSource, newSource, oldModSource);

					// Just make sure it atleast assembles still
					try
					{
						Assembler assembler = new Assembler(RuneLiteInstructions.instance);
						assembler.assemble(new ByteArrayInputStream(newModSource.getBytes()));
					}
					catch (Exception e)
					{
						log.warn("Updated script does not assemble {}", scriptFile.getName(), e);
					}

					try (OutputStream os = new FileOutputStream(hashFile))
					{
						os.write(nhash.getBytes());
					}
					try (OutputStream os = new FileOutputStream(scriptFile))
					{
						os.write(newModSource.getBytes());
					}

					synchronized (gitLock)
					{
						Git.runelite.add(scriptFile);
						Git.runelite.add(hashFile);
					}
					log.info("Updated script {}", scriptFile.getName());
				}
				catch (Exception e)
				{
					log.warn("Unable to update script {}, ", scriptFile.getName(), e);
				}
			}));
		}
		scripts.forEach(s ->
		{
			try
			{
				s.get();
			}
			catch (Exception e)
			{
				throw new RuntimeException(e);
			}
		});
		tp.shutdown();

		Git.runelite.commitUpdate("Scripts");
	}

	@VisibleForTesting
	static String updateScript(ScriptSource oldS, ScriptSource newS, ScriptSource oldM)
	{
		Mapping<ScriptSource.Line> osDom = Mapping.of(oldS.getLines(), oldM.getLines(), new ScriptSourceMapper());
		Mapping<ScriptSource.Line> osDns = Mapping.of(oldS.getLines(), newS.getLines(), new ScriptSourceMapper());

		StringBuilder out = new StringBuilder();

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
			out.append(formatInstruction(l, Function.identity())).append("\n");//TODO:
		}
		insertions.removeAll(null);

		osDns.forEach((o, n) ->
		{
			if (n != null)
			{
				out.append(formatInstruction(n, Function.identity()));
				ScriptSource.Line oml = osDom.getSame().get(o);
				if (oml != null && oml.getComment() != null)
				{
					out.append(oml.getComment());
				}
				out.append("\n");
			}

			for (ScriptSource.Line l : insertions.get(o))
			{
				out.append(formatInstruction(l, Function.identity())).append("\n");//TODO:
			}
		});

		return out.toString();
	}

	private static String formatInstruction(ScriptSource.Line l, Function<String, String> labelmapper)
	{
		String s = l.getPrefix();
		if (s == null)
		{
			s = "";
		}

		String is = null;
		Instruction in = l.getInstruction();
		if (in != null)
		{
			is = in.getName();
		}
		if (is == null)
		{
			is = l.getOpcode();
		}
		if (is != null)
		{
			if (is.endsWith(":"))
			{
				s += labelmapper.apply(is);
			}
			else
			{
				s += String.format("%-22s", is);
			}
		}

		if (l.getOperand() != null && !l.getOperand().isEmpty())
		{
			s += " " + labelmapper.apply(l.getOperand());
		}
		if (l.getComment() != null && !l.getComment().isEmpty())
		{
			s += l.getComment();
		}
		return s;
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
