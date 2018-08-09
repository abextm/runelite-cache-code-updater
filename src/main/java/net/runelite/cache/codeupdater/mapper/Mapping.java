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

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Mapping
{
	@VisibleForTesting
	static boolean printMap = false;

	public static <T> BiMap<T, T> map(List<T> old, List<T> neew, Mapper<T> compare)
	{
		if (old.size() <= 0 || neew.size() <= 0)
		{
			return HashBiMap.create();
		}

		int stride = neew.size() + 1;
		int[] directions = new int[]{1, stride, stride + 1};
		double[] dist = new double[stride * (old.size() + 1)];
		int[] prev = new int[dist.length];
		PriorityQueue<Integer> nodes = new PriorityQueue<>(dist.length, Comparator.comparingDouble((Integer a) -> dist[a]));

		for (int i = 0; i < dist.length; i++)
		{
			if (i != 0)
			{
				dist[i] = Double.MAX_VALUE;
			}
			prev[i] = -1;
		}

		nodes.add(0);

		for (; !nodes.isEmpty(); )
		{
			int u = nodes.poll();

			if (u == dist.length - 1)
			{
				break;
			}

			for (int d : directions)
			{
				int v = u + d;
				if (v >= dist.length || v % stride < u % stride)
				{
					continue;
				}

				int oi = v / stride;
				int ni = v % stride;
				double alt = 0;
				if (oi > 0 && ni > 0)
				{
					alt = dist[u] + compare.difference(
						old.get(oi - 1),
						neew.get(ni - 1)
					)/* + (Math.abs(oi - ni) / (double) dist.length)*/;
				}

				if (alt < dist[v])
				{
					nodes.remove(v);
					dist[v] = alt;
					prev[v] = u;
					nodes.add(v);
				}
			}
		}

		BiMap<T, T> map = HashBiMap.create();
		int i = dist.length - 1;
		for (; ; )
		{
			int n = prev[i];
			if (n == -1)
			{
				break;
			}

			int d = i - n;

			if (d == stride + 1)
			{
				map.put(old.get((i / stride) - 1), neew.get((i % stride) - 1));
			}
			if (n == 0)
			{
				break;
			}
			i = n;
		}

		if (printMap)
		{
			for (int ii = 0; ii < dist.length; ii++)
			{
				if (ii % stride == 0)
				{
					System.out.println();
				}
				if (dist[ii] == Double.MAX_VALUE)
				{
					System.out.print("      |");
				}
				else
				{
					System.out.printf("%6.1f|", dist[ii]);
				}
			}
			System.out.println();
		}

		return map;
	}
}
