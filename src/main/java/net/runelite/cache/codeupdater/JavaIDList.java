/*
 * Copyright (c) 2024 Abex
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

import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import java.io.IOException;
import java.util.ArrayList;
import java.util.ListIterator;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.cache.codeupdater.git.MutableCommit;
import net.runelite.cache.codeupdater.git.Repo;
import org.eclipse.jgit.lib.Repository;

@Slf4j
public class JavaIDList
{
	private final ListIterator<BodyDeclaration<?>> declIter;

	@Getter
	private FieldDeclaration fdecl;

	public JavaIDList(TypeDeclaration<?> typeDecl)
	{
		this.declIter = new ArrayList<>(typeDecl.getMembers()).listIterator();
	}

	public int intValue()
	{
		assert fdecl.getVariables().size() == 1;
		var ile = (IntegerLiteralExpr) fdecl.getVariable(0).getInitializer().get();
		return ile.asInt();
	}

	public void setIntValue(int value)
	{
		fdecl.getVariable(0).getInitializer().get().replace(new IntegerLiteralExpr(value));
	}

	public String getName()
	{
		assert fdecl.getVariables().size() == 1;
		return fdecl.getVariable(0).getNameAsString();
	}

	public void delete()
	{
		fdecl.remove();
	}

	boolean next()
	{
		for (; declIter.hasNext(); )
		{
			var decl = declIter.next();
			if (decl instanceof FieldDeclaration)
			{
				this.fdecl = (FieldDeclaration) decl;
				if (this.fdecl.isStatic() && this.fdecl.isFinal())
				{
					return true;
				}
			}
		}
		return false;
	}

	public interface Consumer
	{
		void accept(MutableCommit commit, JavaIDList list) throws Exception;
	}

	public static void update(String name, String path, Consumer consumer) throws IOException
	{
		Repository rl = Repo.RUNELITE.get();

		JavaFile scriptIDCU = new JavaFile(rl, Main.branchName, path);
		MutableCommit mc = new MutableCommit(name);

		JavaIDList l = new JavaIDList(scriptIDCU.getCompilationUnit().getTypes().get(0));
		for (; l.next(); )
		{
			try
			{
				consumer.accept(mc, l);
			}
			catch (Exception e)
			{
				mc.log("Error reading {}:{} : {}", path, l.getFdecl().getBegin().map(p -> p.toString()).orElse("Unknown"), e);
				log.info("{}", name, e);
			}
		}

		scriptIDCU.save(mc);
		mc.finish(Repo.RUNELITE.get(), Main.branchName);
	}
}
