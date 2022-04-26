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

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Map;

public class ScriptOpcodeFixer
{
	public static void main(String[] args) throws IOException
	{
		boolean noNames = "-nonames".equalsIgnoreCase(args[0]);
		ScriptLineFormatConfig config = new ScriptLineFormatConfig()
		{
			@Override
			public boolean isNoNames()
			{
				return noNames;
			}
		};

		File root = new File(args[1]);
		Files.walkFileTree(root.toPath(), new SimpleFileVisitor<Path>()
		{
			@Override
			public FileVisitResult visitFile(Path path, BasicFileAttributes attr) throws IOException
			{
				if (path.toString().endsWith(".rs2asm"))
				{
					System.out.println("Editing \"" + path + "\"");
					ScriptSource ss = new ScriptSource(new String(Files.readAllBytes(path), StandardCharsets.UTF_8));
					StringBuilder out = new StringBuilder();
					out.append(ss.getPrelude());
					for (Map.Entry<String, ScriptSource.Line> hf : ss.getHeader().entrySet())
					{
						out.append(String.format("%-19s ", hf.getKey()));
						out.append(hf.getValue().getOperand());
						if (hf.getValue().getComment() != null)
						{
							out.append(hf.getValue().getComment());
						}
						out.append("\n");
					}
					for (ScriptSource.Line line : ss.getLines())
					{
						out.append(line.format(config)).append("\n");
					}
					Files.write(path, out.toString().getBytes(StandardCharsets.UTF_8));
				}
				return FileVisitResult.CONTINUE;
			}
		});
	}
}
