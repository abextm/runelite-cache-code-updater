/*
 * Copyright (c) 2023 Abex
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

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.IntPredicate;
import javax.annotation.Nullable;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.experimental.StandardException;

@RequiredArgsConstructor
public class INIParser
{
	abstract static class Token
	{
		Token prev;
		Token next;
		int line;
		int lineOffset;

		abstract void print(Writer out) throws IOException;
	}

	@AllArgsConstructor
	static class SpecialToken extends Token
	{
		char tok;

		@Override
		void print(Writer out) throws IOException
		{
			out.write(tok);
		}
	}

	@AllArgsConstructor
	static class WhitespaceToken extends Token
	{
		CharSequence ws;

		@Override
		void print(Writer out) throws IOException
		{
			out.write(ws.toString());
		}
	}

	@AllArgsConstructor
	static class NewlineToken extends Token
	{
		CharSequence nl;

		@Override
		void print(Writer out) throws IOException
		{
			out.write(nl.toString());
		}
	}

	@AllArgsConstructor
	static class IntValueToken extends Token
	{
		long intValue;
		CharSequence value;

		public void setValue(long value)
		{
			intValue = value;
			this.value = "" + value;
		}

		@Override
		void print(Writer out) throws IOException
		{
			out.write(value.toString());
		}
	}

	@AllArgsConstructor
	static class StringValueToken extends Token
	{
		String stringValue;
		CharSequence value;

		@Override
		void print(Writer out) throws IOException
		{
			out.write(value.toString());
		}
	}

	static class Table
	{
		Token start;
		@Nullable
		StringValueToken key;
		Map<String, Token> children = new LinkedHashMap<>();

		public void print(Writer out) throws IOException
		{
			for (Token n = start; n != null; n = n.next)
			{
				n.print(out);
			}
		}
	}

	static class Document
	{
		Table rootTable = new Table();
		LinkedHashMap<String, Table> tables = new LinkedHashMap<>();

		public void print(Writer out) throws IOException
		{
			rootTable.print(out);
			for (var tab : tables.values())
			{
				tab.print(out);
			}
		}

		@SneakyThrows
		public String print()
		{
			var wr = new StringWriter();
			print(wr);
			return wr.toString();
		}
	}

	@StandardException
	static class ParseException extends RuntimeException
	{
	}

	final String src;
	int line = 1;
	int lineOffset;
	int ptr;

	int startPtr;
	Token curToken;
	Table currentTable;

	int readChar()
	{
		if (ptr == src.length())
		{
			return '\0';
		}

		// if we needed I would get a full code point here
		return src.charAt(ptr++);
	}

	void unread()
	{
		if (ptr != src.length())
		{
			ptr--;
		}
	}

	private boolean readWhile(int startChar, IntPredicate pred)
	{
		if (!pred.test(startChar))
		{
			return false;
		}

		for (; pred.test(readChar()); ) ;
		unread();

		return true;
	}

	private Token readToken(boolean isValue)
	{
		startPtr = ptr;
		int ch = readChar();
		if (ch == '\0')
		{
			if (lineOffset != 1)
			{
				return attrib(new NewlineToken(""));
			}
			return null;
		}
		if (readWhile(ch, c -> c == ' ' || c == '\t'))
		{
			return attrib(new WhitespaceToken(currentChunk()));
		}
		if (ch == '\r')
		{
			ch = readChar();
			if (ch == '\n')
			{
				return attrib(new NewlineToken(currentChunk()));
			}
			throw fail("wanted newline");
		}
		if (ch == '\n')
		{
			return attrib(new NewlineToken(currentChunk()));
		}
		if (ch == '#')
		{
			readWhile(ch, c -> c != '\r' && c != '\n' && c != '\0');
			return attrib(new WhitespaceToken(currentChunk()));
		}
		if (isValue && ch >= '0' && ch <= '9')
		{
			long value = ch - '0';
			for (; ; )
			{
				ch = readChar();
				if (ch >= '0' && ch <= '9')
				{
					value = (value * 10) + (ch - '0');
				}
				else if (ch != '_')
				{
					break;
				}
			}
			unread();
			return attrib(new IntValueToken(value, currentChunk()));
		}
		if (!isValue && readWhile(ch, this::isBareStrChar))
		{
			return attrib(new StringValueToken(currentChunk().toString(), currentChunk()));
		}
		if (ch == '[' || ch == ']' || ch == '=')
		{
			return attrib(new SpecialToken((char) ch));
		}
		throw fail("unexpected char '" + (char) ch + "'");
	}

	private boolean isBareStrChar(int c)
	{
		return c == '_'
			|| c == '-'
			|| (c >= 'A' && c <= 'Z')
			|| (c >= 'a' && c <= 'z')
			|| (c >= '0' && c <= '9');
	}

	private CharSequence currentChunk()
	{
		return src.subSequence(startPtr, ptr);
	}

	private ParseException fail(String message)
	{
		return new ParseException(message + " at " + line + ":" + (lineOffset + (ptr - startPtr - 1)));
	}

	private ParseException fail(Token tok)
	{
		throw new ParseException("unexpected " + tok.getClass().getSimpleName() + " at " + tok.line + ":" + tok.lineOffset);
	}

	private Token attrib(Token token)
	{
		token.line = line;
		token.lineOffset = lineOffset;
		token.prev = curToken;
		if (curToken != null)
		{
			curToken.next = token;
		}
		else
		{
			currentTable.start = token;
		}
		curToken = token;

		lineOffset += (ptr - startPtr);
		if (token instanceof NewlineToken)
		{
			line++;
			lineOffset = 1;
		}
		return token;
	}

	private Token readTokNoWhitespace(boolean isValue)
	{
		for (;;)
		{
			var tok = readToken(isValue);
			if (tok instanceof WhitespaceToken)
			{
				continue;
			}
			return tok;
		}
	}

	public Document parse()
	{
		var out = new Document();
		currentTable = out.rootTable;
		for (Token tok;;)
		{
			tok = readTokNoWhitespace(false);
			if (tok == null)
			{
				break;
			}
			if (tok instanceof SpecialToken && ((SpecialToken) tok).tok == '[')
			{
				currentTable = new Table();
				curToken = null;

				tok = readTokNoWhitespace(false);
				if (tok instanceof StringValueToken)
				{
					currentTable.key = (StringValueToken) tok;
				}
				else
				{
					fail(tok);
				}
				out.tables.put(currentTable.key.stringValue, currentTable);

				tok = readTokNoWhitespace(false);
				if (tok instanceof SpecialToken && ((SpecialToken) tok).tok != ']')
				{
					fail(tok);
				}

				tok = readTokNoWhitespace(false);
				if (!(tok instanceof NewlineToken))
				{
					fail(tok);
				}
				continue;
			}
			if (tok instanceof StringValueToken)
			{
				var key = (StringValueToken) tok;

				tok = readTokNoWhitespace(false);
				if (tok instanceof SpecialToken && ((SpecialToken) tok).tok != '=')
				{
					fail(tok);
				}

				Token value = readTokNoWhitespace(true);
				if (!(value instanceof StringValueToken || value instanceof IntValueToken))
				{
					fail(value);
				}

				tok = readTokNoWhitespace(false);
				if (!(tok instanceof NewlineToken))
				{
					fail(tok);
				}

				currentTable.children.put(key.stringValue, value);
				continue;
			}
			if (tok instanceof NewlineToken)
			{
				continue;
			}
			throw fail(tok);
		}

		return out;
	}
}
