/*
 * Copyright (c) 2025 Abex
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
package net.runelite.cache.codeupdater.apifiles;

import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.Getter;
import lombok.Setter;
import net.runelite.cache.codeupdater.git.MutableCommit;

public class IDClass
{
	private static final Pattern ALPHANUM = Pattern.compile("[^a-zA-Z0-9_]");

	private final StringBuilder out;
	private final String name;
	private final String indent;
	private final String pkg;

	@Setter
	private boolean isInterface;

	@Setter
	private boolean isPublic = true;

	@Setter
	private boolean isFinal = true;

	@Getter
	private final List<String> extend = new ArrayList<>();

	private final List<IDClass> innerClasses = new ArrayList<>();

	private IDClass(String name, String pkg, String indent)
	{
		this.out = new StringBuilder();
		this.name = name;
		this.indent = indent;
		this.pkg = pkg;
	}

	public IDClass(String name, String pkg)
	{
		this(name, pkg, "\t");
	}

	public IDClass(String name)
	{
		this(name, "net.runelite.api.gameval");
	}

	public IDClass inner(String name)
	{
		var c = new IDClass(name, pkg, indent + "\t");
		innerClasses.add(c);
		return c;
	}

	public void newline()
	{
		out.append("\n");
	}

	public void add(String name, int id)
	{
		String javaName = sanitizeSnake(name);
		out.append(indent);
		if (!isInterface)
		{
			out.append("public static final ");
		}
		out.append("int ").append(javaName).append(" = ").append(id).append(";\n");
	}

	public void add(String name, String javaValue)
	{
		String javaName = sanitizeSnake(name);
		out.append(indent);
		if (!isInterface)
		{
			out.append("public static final ");
		}
		out.append("int ").append(javaName).append(" = ").append(javaValue).append(";\n");
	}

	public void add(String name, int id, String comment)
	{
		if (comment != null && !comment.equals("null"))
		{
			out.append('\n');
			out.append(indent).append("/**\n");
			out.append(indent).append(" * ");
			for (int i = 0; i < comment.length(); i++)
			{
				char c = comment.charAt(i);
				switch (c)
				{
					case '@':
					case '<':
					case '>':
					case '&':
						out.append("&#").append((int) c).append(';');
						break;
					default:
						out.append(c);
						break;
				}
			}
			out.append("\n");
			out.append(indent).append(" */\n");
		}
		add(name, id);
	}

	public void write(MutableCommit commit)
	{
		try (var c = new PrintWriter(
			commit.writeFile("runelite-api/src/main/java/" + pkg.replace('.', '/') + "/" + name + ".java"),
			false,
			StandardCharsets.UTF_8))
		{
			c.print("/* This file is automatically generated. Do not edit. */\n");
			c.print("package ");
			c.print(pkg);
			c.print(";\n");
			c.print("\n");
			c.print("@SuppressWarnings(\"unused\")\n");
			write(c, "");
		}
	}

	private void write(PrintWriter c, String indent)
	{
		c.print(indent);
		if (isPublic)
		{
			c.print("public ");
		}
		if (!isInterface)
		{
			if (!indent.isEmpty())
			{
				c.print("static ");
			}
			if (isFinal)
			{
				c.print("final ");
			}
		}
		c.print(isInterface ? "interface " : "class ");
		c.print(name);
		if (!extend.isEmpty())
		{
			c.print(" extends ");
			c.print(String.join(extend.size() > 10 ? ",\n\t" + indent : ", ", extend));
		}
		c.print("\n");
		c.print(indent);
		c.print("{\n");
		c.append(out);

		for (var ic : innerClasses)
		{
			c.print("\n");
			ic.write(c, indent + "\t");
		}

		if (indent.isEmpty())
		{
			c.print("/* This file is automatically generated. Do not edit. */\n");
		}
		c.print(indent);
		c.print("}\n");
	}

	public static String sanitizeSnake(String in)
	{
		return sanitize(in).toUpperCase();
	}

	public static String sanitize(String in)
	{
		String s = ALPHANUM.matcher(in).replaceAll("_");
		return Character.isDigit(s.charAt(0)) || "_".equals(s) ? "_" + s : s;
	}

	public static class Overflow
	{
		private static final int BREAKPOINT = 32000;

		private final String name;
		private final Map<Integer, IDClass> files = new HashMap<>();

		public Overflow(String name)
		{
			this.name = name;
		}

		public void add(String name, int id, String comment)
		{
			var idc = files.computeIfAbsent(id / BREAKPOINT, n ->
				new IDClass(this.name + (n == 0 ? "" : "" + n)));
			idc.add(name, id, comment);
		}

		public void write(MutableCommit mc)
		{
			String last = null;
			for (var e : files.entrySet().stream()
				.sorted(Comparator.comparing(e -> -e.getKey()))
				.collect(Collectors.toList()))
			{
				if (last != null)
				{
					e.getValue().getExtend().add(last);
				}
				if (e.getValue().isFinal && e.getKey() != 0)
				{
					e.getValue().isPublic = false;
					e.getValue().isFinal = false;
				}
				e.getValue().write(mc);
				last = e.getValue().name;
			}
		}
	}
}
