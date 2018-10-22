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
package net.runelite.cache.codeupdater.srn;

import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.io.MoreFiles;
import com.google.common.io.RecursiveDeleteOption;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import lombok.extern.slf4j.Slf4j;
import net.runelite.cache.IndexType;
import net.runelite.cache.ItemManager;
import net.runelite.cache.SpriteManager;
import net.runelite.cache.TextureManager;
import net.runelite.cache.codeupdater.Git;
import net.runelite.cache.definitions.ItemDefinition;
import net.runelite.cache.definitions.ModelDefinition;
import net.runelite.cache.definitions.loaders.ModelLoader;
import net.runelite.cache.definitions.providers.ModelProvider;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Store;
import net.runelite.cache.item.ItemSpriteFactory;

@Slf4j
public class SRNUpdate
{
	public static void update(Store store) throws IOException
	{
		File rootDir = new File("static.runelite.net/cache");
		File itemIconDir = new File(rootDir, "item/icon");
		File namesFile = new File(rootDir, "item/names.json");
		if (itemIconDir.exists())
		{
			MoreFiles.deleteDirectoryContents(itemIconDir.toPath(), RecursiveDeleteOption.ALLOW_INSECURE);
		}
		else
		{
			itemIconDir.mkdirs();
		}

		ItemManager itemManager = new ItemManager(store);
		itemManager.load();

		ModelProvider modelProvider = modelId -> {
			Index models = store.getIndex(IndexType.MODELS);
			Archive archive = models.getArchive(modelId);

			byte[] data = archive.decompress(store.getStorage().loadArchive(archive));
			ModelDefinition inventoryModel = new ModelLoader().load(modelId, data);
			return inventoryModel;
		};

		SpriteManager spriteManager = new SpriteManager(store);
		spriteManager.load();

		TextureManager textureManager = new TextureManager(store);
		textureManager.load();

		Collection<ItemDefinition> items = itemManager.getItems();
		Map<Integer, ItemDefinition> itemIDs = new HashMap<>(items.size());
		for (ItemDefinition d : items)
		{
			if (d.notedTemplate != -1 || d.placeholderTemplateId != -1)
			{
				continue;
			}
			itemIDs.put(d.id, d);
		}
		for (ItemDefinition d : items)
		{
			// Remove all count variants, except the highest one, which we map to the root
			if (d.countObj != null)
			{
				int hiCo = 1;
				int hiObj = d.id;
				for (int i = 0; i < 10; i++)
				{
					itemIDs.remove(d.countObj[i]);
					if (hiCo <= d.countCo[i])
					{
						hiObj = d.countObj[i];
						hiCo = d.countCo[i];
					}
				}
				itemIDs.put(d.id, itemManager.getItem(hiObj));
			}
		}

		itemIDs.entrySet()
			.stream()
			.parallel()
			.forEach(i ->
			{
				try
				{
					BufferedImage img = ItemSpriteFactory.createSprite(
						itemManager, modelProvider, spriteManager, textureManager,
						i.getValue().id, 0, 1, 0x302020, false);
					ImageIO.write(img, "png", new File(itemIconDir, i.getKey() + ".png"));
				}
				catch (Exception e)
				{
					log.warn("Unable to create sprite for {} ({})", i.getKey(), i.getValue().id, e);
				}
			});

		Git.srn.add(itemIconDir);
		Git.srn.commitUpdate("Item Icons");

		Map<Integer, String> itemNames = items.stream()
			.filter(i -> !Strings.isNullOrEmpty(i.name) && !"null".equalsIgnoreCase(i.name))
			.collect(ImmutableMap.toImmutableMap(
				i->i.id,
				i->i.name
			));

		String itemNameJSON = new GsonBuilder()
			.create()
			.toJson(itemNames);
		Files.write(namesFile.toPath(), itemNameJSON.getBytes(Charsets.UTF_8));

		Git.srn.add(namesFile);
		Git.srn.commitUpdate("Item Names");
	}
}
