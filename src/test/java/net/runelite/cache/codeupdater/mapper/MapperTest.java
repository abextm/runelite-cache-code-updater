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
package net.runelite.cache.codeupdater.mapper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Assert;
import org.junit.Test;

public class MapperTest
{
	static
	{
		Mapping.printMap = true;
	}

	@Test
	public void testNewEnd()
	{
		testMapping(
			1, 1,
			2, 2,
			3, 3,
			4, 4,
			null, 5
		);
	}

	@Test
	public void testNewStart()
	{
		testMapping(
			null, 0,
			1, 1,
			2, 2,
			3, 3,
			4, 4
		);
	}

	@Test
	public void testNewMiddle()
	{
		testMapping(
			2, 2,
			null, 3,
			4, 4
		);
	}

	@Test
	public void testNewEndInverse()
	{
		testMapping(
			1, 1,
			2, 2,
			3, 3,
			4, 4,
			5, null
		);
	}

	@Test
	public void testNewStartInverse()
	{
		testMapping(
			0, null,
			1, 1,
			2, 2,
			3, 3,
			4, 4
		);
	}

	@Test
	public void testNewMiddleInverse()
	{
		testMapping(
			1, 1,
			2, 2,
			3, null,
			4, 4,
			5, 5
		);
	}

	private void testMapping(Integer... t)
	{
		List<Integer> old = new ArrayList<>(t.length / 2);
		List<Integer> neew = new ArrayList<>(t.length / 2);
		for (int i = 0; i < t.length; )
		{
			Integer o = t[i++];
			if (o != null)
			{
				old.add(o);
			}

			Integer n = t[i++];
			if (n != null)
			{
				neew.add(n);
			}
		}

		Mapping<Integer> map = Mapping.of(old, neew, (a, b) -> Math.abs(a - b));
		List<Integer> expectedNewOnly = new ArrayList<>();
		List<Integer> expectedOldOnly = new ArrayList<>();

		for (int i = 0; i < t.length; i += 2)
		{
			String ident = "idx " + (i / 2) + ": " + t[i] + " <-> " + t[i + 1] + ": ";
			if (t[i] == null)
			{
				expectedNewOnly.add(t[i + 1]);
			}
			else
			{
				Assert.assertEquals(ident + "old > new wrong", t[i + 1], map.getSame().get(t[i]));
			}
			if (t[i + 1] == null)
			{
				expectedOldOnly.add(t[i]);
			}
			else
			{
				Assert.assertEquals(ident + "new > old wrong", t[i], map.getSame().inverse().get(t[i + 1]));
			}
		}
		Assert.assertEquals(
			expectedNewOnly,
			map.getNewOnly()
		);
		Assert.assertEquals(
			expectedOldOnly,
			map.getOldOnly()
		);
		AtomicInteger i = new AtomicInteger(0);
		map.forEach((o, n) ->
		{
			int ii = i.getAndAdd(2);
			String ident = "idx " + (ii / 2) + ": " + t[ii] + " <-> " + t[ii + 1] + ": ";
			Assert.assertEquals(ident + "foreach old wrong", t[ii], o);
			Assert.assertEquals(ident + "foreach new wrong", t[ii + 1], n);
		});
	}
}
