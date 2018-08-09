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

import com.google.common.collect.BiMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.antlr.v4.runtime.misc.MultiMap;
import org.junit.Assert;
import org.junit.Test;

public class MapperTest
{
	@Test
	public void testMapper()
	{
		Mapping.printMap = true;

		testMapping("new end",
			1, 1,
			2, 2,
			3, 3,
			4, 4,
			null, 5
		);

		testMapping("new start",
			null, 0,
			1, 1,
			2, 2,
			3, 3,
			4, 4
		);

		testMapping("new middle",
			1, 1,
			2, 2,
			null, 3,
			4, 4,
			5, 5
		);
	}

	private void testMapping(String name, Integer... t)
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

		BiMap<Integer, Integer> map = Mapping.map(old, neew, (a, b) -> Math.abs(a - b));

		for (int i = 0; i < t.length; i += 2)
		{
			if (t[i] != null)
			{
				Assert.assertEquals(name + ": " + t[i] + " @ " + (i / 2), map.get(t[i]), t[i + 1]);
			}
		}

	}
}
