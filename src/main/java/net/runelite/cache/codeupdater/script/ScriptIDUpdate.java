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

import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.MarkerAnnotationExpr;
import com.github.javaparser.ast.expr.NormalAnnotationExpr;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import net.runelite.cache.codeupdater.JavaFile;
import net.runelite.cache.codeupdater.Main;
import net.runelite.cache.codeupdater.git.MutableCommit;
import net.runelite.cache.codeupdater.git.Repo;
import net.runelite.cache.definitions.ScriptDefinition;
import net.runelite.cache.definitions.loaders.ScriptLoader;
import org.eclipse.jgit.lib.Repository;

@Slf4j
public class ScriptIDUpdate
{
	public static void update() throws IOException
	{
		ScriptLoader loader = new ScriptLoader();

		Repository rl = Repo.RUNELITE.get();

		JavaFile scriptIDCU = new JavaFile(rl, Main.branchName, "runelite-api/src/main/java/net/runelite/api/ScriptID.java");
		MutableCommit mc = new MutableCommit("Script arguments");

		for (BodyDeclaration bdecl : scriptIDCU.getCompilationUnit().getTypes().get(0).getMembers())
		{
			try
			{
				if (!(bdecl instanceof FieldDeclaration))
				{
					continue;
				}

				FieldDeclaration fdecl = (FieldDeclaration) bdecl;
				if (!fdecl.isStatic() || !fdecl.isFinal())
				{
					continue;
				}

				assert fdecl.getVariables().size() == 1;

				VariableDeclarator vdecl = fdecl.getVariable(0);
				IntegerLiteralExpr scriptIDLiteral = (IntegerLiteralExpr) vdecl.getInitializer().get();

				int id = scriptIDLiteral.asInt();

				if (id > 10000)
				{
					// RuneLite script
					continue;
				}

				byte[] newScript = ScriptUpdate.get(Main.next, id);
				if (newScript == null)
				{
					mc.log("lost script {}", id);
					scriptIDLiteral.replace(new IntegerLiteralExpr(-1));
					continue;
				}
				ScriptDefinition script = loader.load(id, newScript);

				AnnotationExpr allAnn = fdecl.getAnnotationByName("ScriptArguments").orElse(null);
				NormalAnnotationExpr ann = null;
				if (allAnn instanceof NormalAnnotationExpr)
				{
					ann = (NormalAnnotationExpr) allAnn;
				}
				if (allAnn instanceof MarkerAnnotationExpr)
				{
					fdecl.remove(allAnn);
				}
				if (ann == null)
				{
					ann = fdecl.addAndGetAnnotation("ScriptArguments");
				}
				ann.getPairs().clear();

				if (script.getIntStackCount() != 0)
				{
					ann.addPair("integer", new IntegerLiteralExpr(script.getIntStackCount()));
				}
				if (script.getStringStackCount() != 0)
				{
					ann.addPair("string", new IntegerLiteralExpr(script.getStringStackCount()));
				}
			}
			catch (Exception e)
			{
				mc.log("Error reading ScriptID.java:{} : {}", bdecl.getBegin().map(p -> p.toString()).orElse("Unknown"), e);
				log.info("ScriptIDUpdate", e);
			}
		}

		scriptIDCU.save(mc);
		mc.finish(rl, Main.branchName);
	}
}
