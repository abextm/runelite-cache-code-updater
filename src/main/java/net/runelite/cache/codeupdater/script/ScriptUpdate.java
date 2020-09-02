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
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import com.google.common.hash.Hashing;
import com.google.common.io.BaseEncoding;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
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
	private static Map<String, String> opcodeGroups = ImmutableMap.<String, String>builder()
		.put("iload", "ilvt")
		.put("istore", "ilvt")
		.put("sstore", "slvt")
		.put("sload", "slvt")
		.put("jump", "label")
		.put("if_icmpeq", "label")
		.put("if_icmpne", "label")
		.put("if_icmplt", "label")
		.put("if_icmpgt", "label")
		.put("if_icmple", "label")
		.put("if_icmpge", "label")
		.build();

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
			.map(ent -> () ->
			{
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

		HashMap<String, String> headers = new LinkedHashMap<>(newS.getHeader());
		HashMap<String, String> modHeaders = new LinkedHashMap<>();
		for (Map.Entry<String, String> hf : oldM.getHeader().entrySet())
		{
			String osv = oldS.getHeader().get(hf.getKey());
			String omv = hf.getValue();
			if (omv != null && osv == null)
			{
				modHeaders.put(hf.getKey(), hf.getValue());
			}
			if (!Objects.equals(osv, omv))
			{
				headers.put(hf.getKey(), hf.getValue());
			}
		}
		modHeaders.putAll(headers);

		for (Map.Entry<String, String> hf : modHeaders.entrySet())
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

		Map<String, String> operandMap = new HashMap<>();

		osDns.getSame().forEach((o, n) ->
		{
			if (!Objects.equals(o.getOpcode(), n.getOpcode()))
			{
				if (o.getOperand().isEmpty() && o.getOpcode().endsWith(":") && n.getOpcode().endsWith(":"))
				{
					operandMap.put("label\0" + o.getOpcode().substring(0, o.getOpcode().length() - 2),
						n.getOpcode().substring(0, n.getOpcode().length() - 2));
				}
				return;
			}

			String group = opcodeGroups.get(o.getOpcode());
			if (o.getOpcode().endsWith(":") && !o.getOperand().isEmpty() && !n.getOperand().isEmpty())
			{
				//case
				group = "label";
			}
			if (group != null)
			{
				operandMap.put(group + "\0" + o.getOperand(), n.getOperand());
			}
		});

		ScriptLineFormatConfig identityConfig = new ScriptLineFormatConfig();
		ScriptLineFormatConfig config = new ScriptLineFormatConfig()
		{
			@Override
			String mapLabel(String label)
			{
				String newLabel = operandMap.get("label\0" + label.substring(0, label.length() - 2));
				if (newLabel != null)
				{
					return newLabel + ":";
				}
				return label;
			}

			@Override
			public String mapOperand(String opcode, String operand)
			{
				String group = opcodeGroups.get(opcode);
				if (opcode.endsWith(":") && !operand.isEmpty())
				{
					// case
					group = "label";
				}
				if (group != null)
				{
					return operandMap.getOrDefault(group + "\0" + operand, operand);
				}
				return operand;
			}
		};

		for (ScriptSource.Line l : insertions.get(null))
		{
			out.append(l.format(config)).append("\n");
		}
		insertions.removeAll(null);

		osDns.forEach((o, n) ->
		{
			if (n != null)
			{
				out.append(n.format(identityConfig));
				ScriptSource.Line oml = osDom.getSame().get(o);
				if (oml != null && oml.getComment() != null)
				{
					out.append(oml.getComment());
				}
				out.append("\n");
			}

			for (ScriptSource.Line l : insertions.get(o))
			{
				out.append(l.format(config)).append("\n");
			}
		});

		return out.toString();
	}

	static byte[] get(Store store, int id) throws IOException
	{
		Storage storage = store.getStorage();
		Index index = store.getIndex(IndexType.CLIENTSCRIPT);
		Archive archive = index.getArchive(id);
		byte[] compressed = storage.loadArchive(archive);
		return archive.decompress(compressed);
	}
}
