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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.function.BiConsumer;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class Mapping<T>
{
	@VisibleForTesting
	static boolean printMap = false;

	@Getter
	private final BiMap<T, T> same = HashBiMap.create();

	@Getter
	private final List<T> oldOnly = new ArrayList<>();

	@Getter
	private final List<T> newOnly = new ArrayList<>();

	@Getter
	private final List<T> old;

	private final List<T> neew;

	private List<T> getNew()
	{
		return neew;
	}

	public static <T> Mapping<T> of(List<T> old, List<T> neew, Mapper<T> compare)
	{
		Mapping<T> mapping = new Mapping<>(old, neew);

		if (old.size() <= 0 || neew.size() <= 0)
		{
			return mapping;
		}

		int stride = neew.size() + 1;
		int[] directions = new int[]{stride + 1, 1, stride};
		double[] dist = new double[stride * (old.size() + 1)];
		int[] prev = new int[dist.length];
		PriorityQueue<Integer> nodes = new PriorityQueue<>(dist.length, Comparator.comparingDouble((Integer a) -> dist[a]));

		for (int i = 0; i < dist.length; i++)
		{
			if (i != 0)
			{
				dist[i] = Double.MAX_VALUE;
			}
			prev[i] = Integer.MIN_VALUE;
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
				double alt = dist[u] + 1;
				if (oi > 0 && ni > 0)
				{
					double cost = 1;
					if (d == (stride + 1))
					{
						cost = compare.difference(
							old.get(oi - 1),
							neew.get(ni - 1)
						);
					}
					alt = dist[u] + cost;
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

		if (printMap)
		{
			System.out.println();
			System.out.print("      |");
			System.out.print("      |");
			System.out.print("\033[41m");
			for (int i = 1; i < stride; i++)
			{
				String str = "     " + neew.get(i - 1);
				System.out.print(str.substring(str.length() - 6) + "|");
			}
			System.out.print("\033[0m");
			for (int ii = 0; ii < dist.length; ii++)
			{
				if (ii % stride == 0)
				{
					System.out.println();
					if (ii < stride)
					{
						System.out.print("      |");
					}
					else
					{
						String str = "     " + old.get((ii / stride) - 1);
						System.out.print("\033[44m" + str.substring(str.length() - 6) + "|\033[0m");
					}
				}
				if (dist[ii] == Double.MAX_VALUE)
				{
					System.out.print("      |");
				}
				else
				{
					String color = null;
					for (int i = dist.length - 1; i >= 0; )
					{
						int n = prev[i];
						if (i == ii)
						{
							int d = i - n;
							if (d == stride + 1 || n == Integer.MIN_VALUE)
							{
								color = "\033[7m";
							}
							else if (d == 1)
							{
								color = "\033[41m";
							}
							else if (d == stride)
							{
								color = "\033[44m";
							}
							break;
						}
						i = n;
					}
					if (color != null)
					{
						System.out.print(color);
					}
					System.out.printf("%6.1f|", dist[ii]);
					if (color != null)
					{
						System.out.print("\033[0m");
					}
				}
			}
			System.out.println();
		}

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
				T ov = old.get((i / stride) - 1);
				T nv = neew.get((i % stride) - 1);
				mapping.same.put(ov, nv);
			}
			else if (d == 1)
			{
				T nv = neew.get((i % stride) - 1);
				mapping.newOnly.add(nv);
			}
			else if (d == stride)
			{
				T ov = old.get((i / stride) - 1);
				mapping.oldOnly.add(ov);
			}
			else
			{
				throw new IllegalStateException();
			}

			if (n == 0)
			{
				break;
			}
			i = n;
		}

		return mapping;
	}

	public void forEach(BiConsumer<T, T> consumer)
	{
		int oi = 0;
		int ni = 0;
		for (; ; )
		{
			boolean nend = ni >= neew.size();
			boolean oend = oi >= old.size();

			if (nend && oend)
			{
				return;
			}

			T o = oend ? null : old.get(oi);
			T n = nend ? null : neew.get(ni);

			if (oend || nend)
			{
				consumer.accept(o, n);
				oi++;
				ni++;
				continue;
			}

			T map = same.get(o);

			if (map == null)
			{
				consumer.accept(o, null);
				oi++;
				continue;
			}

			if (map != n)
			{
				consumer.accept(null, n);
				ni++;
				continue;
			}

			consumer.accept(o, n);
			oi++;
			ni++;
		}
	}
}
