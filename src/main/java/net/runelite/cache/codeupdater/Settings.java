package net.runelite.cache.codeupdater;

import com.google.common.base.Strings;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import lombok.SneakyThrows;
import net.runelite.cache.codeupdater.git.GitUtil;
import net.runelite.cache.codeupdater.git.MutableCommit;
import net.runelite.cache.codeupdater.git.Repo;
import net.runelite.cache.fs.Storage;
import net.runelite.cache.fs.Store;
import net.runelite.cache.fs.flat.FlatStorage;
import net.runelite.cache.fs.jagex.DiskStorage;

public class Settings
{
	private static final Map<String, String> PROPS = new HashMap<>();

	static
	{
		load(Settings.class.getResourceAsStream("default.properties"), null);

		String config = System.getProperty("rlccau.config");
		if (!Strings.isNullOrEmpty(config))
		{
			try
			{
				load(new FileInputStream(config), config);
			}
			catch (IOException e)
			{
				throw new RuntimeException(e);
			}
		}

		for (var ent : System.getProperties().entrySet())
		{
			var key = (String) ent.getKey();
			var value = (String) ent.getKey();

			if (key.startsWith("rlccau"))
			{
				key = key.substring("rlccau.".length());
				if ("config".equals(key))
				{
					continue;
				}

				if (!PROPS.containsKey(key))
				{
					throw new IllegalArgumentException("Incorrect key in system properties " + key);
				}

				PROPS.put(key, value);
			}
		}
	}

	@SneakyThrows
	private static void load(InputStream in, String name)
	{
		var p = new Properties()
		{
			@SneakyThrows
			@Override
			public synchronized Object put(Object keyO, Object valueO)
			{
				var key = (String) keyO;
				var value = (String) valueO;

				if ("config".equals(key))
				{
					load(new FileInputStream(value));
				}
				else
				{
					if (name != null && !PROPS.containsKey(key))
					{
						throw new IllegalArgumentException("Incorrect key in " + name + ": " + key);
					}
					PROPS.put(key, value);
				}

				return null;
			}
		};

		try (in)
		{
			p.load(in);
		}
	}

	public static String get(String key)
	{
		String s = PROPS.get(key);
		if (s == null)
		{
			throw new IllegalArgumentException("missing key " + key);
		}

		return s;
	}

	public static boolean getBool(String key)
	{
		return Boolean.parseBoolean(get(key));
	}

	public static Store openCache(String key) throws IOException
	{
		return openCache(key, null);
	}

	public static Store openCache(String key, MutableCommit mc) throws IOException
	{
		String val = get(key);

		var parts = val.split("=");

		if ("commit".equals(parts[0]))
		{
			return GitUtil.openStore(Repo.OSRS_CACHE.get(), parts[1], mc);
		}
		else if ("dir".equals(parts[0]))
		{
			Storage s;
			if (new File(parts[1], "0.flatcache").exists())
			{
				s = new FlatStorage(new File(parts[1]));
			}
			else
			{
				s = new DiskStorage(new File(parts[1]));
			}

			Store st = new Store(s);
			st.load();
			return st;
		}
		else
		{
			throw new IllegalArgumentException("cache key must be commit= for dir=, not " + parts[0]);
		}
	}

	public static String getCacheName(String key) throws IOException
	{
		String val = Settings.get(key);

		var parts = val.split("=");

		if ("commit".equals(parts[0]))
		{
			return GitUtil.resolve(Repo.OSRS_CACHE.get(), parts[1]).getShortMessage();
		}
		else if ("dir".equals(parts[0]))
		{
			return new File(parts[1]).getName();
		}
		else
		{
			throw new IllegalArgumentException("cache key must be commit= for dir=, not " + parts[0]);
		}
	}
}