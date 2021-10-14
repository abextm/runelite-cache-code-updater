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
package net.runelite.cache.codeupdater.widgets;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumConstantDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.IntegerLiteralExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import lombok.extern.slf4j.Slf4j;
import net.runelite.cache.InterfaceManager;
import net.runelite.cache.codeupdater.JavaFile;
import net.runelite.cache.codeupdater.Main;
import net.runelite.cache.codeupdater.git.MutableCommit;
import net.runelite.cache.codeupdater.git.Repo;
import net.runelite.cache.codeupdater.mapper.Mapping;
import net.runelite.cache.definitions.InterfaceDefinition;
import org.eclipse.jgit.lib.Repository;

@Slf4j
public class WidgetUpdate
{
	public static void update() throws IOException
	{
		InterfaceManager ifmOld = new InterfaceManager(Main.previous);
		ifmOld.load();
		InterfaceManager ifmNew = new InterfaceManager(Main.next);
		ifmNew.load();

		Repository rl = Repo.RUNELITE.get();

		JavaFile widgetInfoCU = new JavaFile(rl, Main.branchName, "runelite-api/src/main/java/net/runelite/api/widgets/WidgetInfo.java");
		JavaFile widgetIDCU = new JavaFile(rl, Main.branchName, "runelite-api/src/main/java/net/runelite/api/widgets/WidgetID.java");
		MutableCommit mc = new MutableCommit("Widget IDs");

		Map<Integer, Map<Integer, IntegerLiteralExpr>> groupChildDecls = new HashMap<>();
		Map<Integer, Set<IntegerLiteralExpr>> groupDecls = new HashMap<>();

		EnumDeclaration widgetInfo = (EnumDeclaration) widgetInfoCU.getCompilationUnit().getTypes().get(0);
		for (EnumConstantDeclaration decl : widgetInfo.getEntries())
		{
			IntegerLiteralExpr group = resolveFieldAccess(widgetIDCU.getCompilationUnit(), decl.getArguments().get(0));
			int groupID = group.asInt();
			IntegerLiteralExpr child = resolveFieldAccess(widgetIDCU.getCompilationUnit(), decl.getArguments().get(1));
			groupChildDecls
				.computeIfAbsent(groupID, v -> new HashMap<>())
				.put(child.asInt(), child);
			groupDecls
				.computeIfAbsent(groupID, v -> new HashSet<>())
				.add(group);
		}

		for (Map.Entry<Integer, Map<Integer, IntegerLiteralExpr>> groupEntry : groupChildDecls.entrySet())
		{
			int group = groupEntry.getKey();

			InterfaceDefinition[] newIG = ifmNew.getIntefaceGroup(group);
			if (newIG == null)
			{
				mc.log("lost interface {}", group);
				for (IntegerLiteralExpr gexpr : groupDecls.get(group))
				{
					gexpr.replace(new IntegerLiteralExpr(-1));
				}
				continue;
			}

			InterfaceDefinition[] oldIG = ifmOld.getIntefaceGroup(group);

			if (!Arrays.equals(oldIG, newIG))
			{
				log.info("interface {} changed", group);
			}

			Mapping<InterfaceDefinition> mapping = Mapping.of(
				ImmutableList.copyOf(oldIG),
				ImmutableList.copyOf(newIG),
				new WidgetMapper());

			for (Map.Entry<Integer, IntegerLiteralExpr> childEntry : groupEntry.getValue().entrySet())
			{
				int child = childEntry.getKey();
				try
				{
					InterfaceDefinition ifd = mapping.getSame().get(ifmOld.getInterface(group, child));
					if (ifd == null)
					{
						childEntry.getValue().replace(new IntegerLiteralExpr(-1));
						mc.log("Lost widget {}.{}", group, child);
					}
					else
					{
						childEntry.getValue().replace(new IntegerLiteralExpr(ifd.id & 0xFFFF));
					}
				}
				catch (Exception e)
				{
					mc.log("Error mapping {}.{}:", group, child, e);
				}
			}
		}

		widgetInfoCU.save(mc);
		widgetIDCU.save(mc);
		mc.finish(rl, Main.branchName);
	}

	private static IntegerLiteralExpr resolveFieldAccess(CompilationUnit widgetIDCU, Node n)
	{
		if (n instanceof FieldAccessExpr)
		{
			FieldAccessExpr axr = (FieldAccessExpr) n;
			SimpleName targetName = axr.getName();
			NodeList<? extends Node> members = widgetIDCU.getTypes();
			for (String className : axr.getScope().toString().split("\\."))
			{
				ClassOrInterfaceDeclaration cd = members.stream()
					.filter(v -> v instanceof ClassOrInterfaceDeclaration)
					.map(v -> (ClassOrInterfaceDeclaration) v)
					.filter(v -> v.getNameAsString().equals(className))
					.findFirst()
					.get();
				members = cd.getMembers();
			}
			n = members.stream()
				.flatMap(v ->
				{
					if (!(v instanceof FieldDeclaration))
					{
						return Stream.empty();
					}
					FieldDeclaration d = (FieldDeclaration) v;
					return d.getVariables().stream();
				})
				.filter(v -> v.getName().equals(targetName))
				.findFirst().get()
				.getInitializer().get();
		}
		return (IntegerLiteralExpr) n;
	}
}
