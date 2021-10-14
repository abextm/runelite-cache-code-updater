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

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseProblemException;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.printer.lexicalpreservation.LexicalPreservingPrinter;
import java.io.IOException;
import lombok.Getter;
import net.runelite.cache.codeupdater.git.GitUtil;
import net.runelite.cache.codeupdater.git.MutableCommit;
import org.eclipse.jgit.lib.Repository;

public class JavaFile
{
	@Getter
	private final CompilationUnit compilationUnit;

	@Getter
	private final String path;

	public JavaFile(Repository repo, String commitish, String file) throws IOException
	{
		this(defaultConfiguration(), repo, commitish, file);
	}

	public static ParserConfiguration defaultConfiguration()
	{
		ParserConfiguration config = new ParserConfiguration();
		config.setLexicalPreservationEnabled(true);
		config.setAttributeComments(false);
		return config;
	}

	public JavaFile(ParserConfiguration config, Repository repo, String commitish, String file) throws IOException
	{
		this.path = file;
		String src = GitUtil.readFileString(repo, commitish, path);

		ParseResult<CompilationUnit> result = new JavaParser(config)
			.parse(src);
		if (!result.isSuccessful())
		{
			throw new ParseProblemException(result.getProblems());
		}

		this.compilationUnit = result.getResult().get();
		LexicalPreservingPrinter.setup(this.compilationUnit);
	}

	public void save(MutableCommit mc)
	{
		String str = LexicalPreservingPrinter.print(compilationUnit);
		mc.writeFile(path, str);
	}
}
