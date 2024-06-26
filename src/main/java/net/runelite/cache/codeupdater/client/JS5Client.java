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
package net.runelite.cache.codeupdater.client;

import java.io.Closeable;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashSet;
import java.util.Queue;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import net.runelite.cache.fs.Archive;
import net.runelite.cache.fs.Container;
import net.runelite.cache.fs.Index;
import net.runelite.cache.fs.Store;
import net.runelite.cache.fs.jagex.CompressionType;
import net.runelite.cache.index.ArchiveData;
import net.runelite.cache.index.IndexData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JS5Client implements Closeable
{
	public static final int SKIP = -1;
	public static final int EXIT = -2;

	private static final int MAX_REV_BUMPS = 10;
	private static final int MAX_OUT = 200;

	private final Logger log;

	private final String hostname;
	private final Socket socket;
	private final DataOutputStream w;
	private final DataInputStream r;
	private final Store store;

	private final Set<Integer> out = new HashSet<>();

	@Getter
	@Setter
	protected Queue<Integer> toDownload = new ArrayDeque<>();

	private boolean seenChange = false;

	@Getter
	private final int rev;

	public JS5Client(Store store, String hostname, int rev, boolean wasBumped) throws IOException
	{
		this.store = store;
		this.hostname = hostname;
		this.log = LoggerFactory.getLogger(toString());

		Socket socket;
		DataOutputStream w;
		DataInputStream r;

		for (int i = 0; ; i++)
		{
			socket = new Socket();
			socket.setSoTimeout(2500);
			socket.setReceiveBufferSize(0xFFFF);
			socket.setSendBufferSize(0xFFFF);
			socket.setTcpNoDelay(false);

			socket.connect(new InetSocketAddress(hostname, 43594), 2500);

			w = new DataOutputStream(socket.getOutputStream());
			r = new DataInputStream(socket.getInputStream());

			w.writeByte(15);
			w.writeInt(rev);
			w.writeInt(0);
			w.writeInt(0);
			w.writeInt(0);
			w.writeInt(0);
			w.flush();

			int status = r.read();
			if (status == 0)
			{
				break;
			}

			if (status == 6 && i < MAX_REV_BUMPS && !wasBumped)
			{
				rev++;
				log.info("Got rev mismatch, bumping to {}", rev);
				continue;
			}

			throw new IOException("Handshake error " + status);
		}

		this.socket = socket;
		this.w = w;
		this.r = r;
		this.rev = rev;

		log.info("Connected with rev {}", rev);
	}

	public void enqueueDownload(int index, int archive)
	{
		toDownload.add(index << 16 | archive);
	}

	public void enqueueRoot()
	{
		enqueueDownload(255, 255);
	}

	protected int getNext()
	{
		Integer pid = toDownload.poll();
		if (pid == null)
		{
			for (int o : out)
			{
				if (o >>> 16 == 255) // We cannot exit with metadata requests out because those can chain other requests
				{
					return SKIP;
				}
			}
			return EXIT;
		}
		return pid;
	}

	public void process() throws IOException
	{
		for (boolean run = true; run; )
		{
			boolean skip = false;
			for (; out.size() < MAX_OUT; )
			{
				int pid = getNext();
				if (pid == SKIP)
				{
					skip = true;
					break;
				}
				if (pid == EXIT)
				{
					skip = true;
					run = false;
					break;
				}
				run = true;

				w.writeByte(1);
				write24(pid);
				out.add(pid);
			}

			w.flush();

			for (; out.size() > 0; )
			{
				if (run && !skip && out.size() < MAX_OUT && r.available() < 520)
				{
					break;
				}

				int index = r.readUnsignedByte();
				int archive = r.readUnsignedShort();

				byte compressionType = r.readByte();
				int len = r.readInt();

				int headerLen = 5;
				if (compressionType != CompressionType.NONE)
				{
					headerLen += 4;
				}

				byte[] buffer = new byte[len + headerLen];
				buffer[0] = compressionType;
				buffer[1] = (byte) (len >> 24);
				buffer[2] = (byte) (len >> 16);
				buffer[3] = (byte) (len >> 8);
				buffer[4] = (byte) (len);

				int cr = 504;
				for (int ptr = 5; ptr < buffer.length; )
				{
					if (cr <= 0)
					{
						int h = r.readUnsignedByte();
						assert h == 0xFF;
						cr = 511;
					}

					int rem = buffer.length - ptr;
					if (rem > cr)
					{
						rem = cr;
					}

					int l = r.read(buffer, ptr, rem);
					if (l < 0)
					{
						throw new EOFException("unable to read " + buffer.length + " bytes for " + index + "/" + archive);
					}
					cr -= l;
					ptr += l;
				}

				out.remove(index << 16 | archive);
				skip = false;

				handleDownload(index, archive, buffer);
			}
		}
	}

	protected void handleDownload(int indexID, int archiveID, byte[] compressed) throws IOException
	{
		if (indexID == 255)
		{
			Container con = Container.decompress(compressed, null);

			if (archiveID == 255)
			{
				ByteBuffer bb = ByteBuffer.wrap(con.data);
				for (int id = 0; bb.hasRemaining(); id++)
				{
					int crc = bb.getInt();
					int rev = bb.getInt();
					if (id == 16)
					{
						continue;
					}
					Index idx = store.findIndex(id);
					if (idx == null)
					{
						idx = store.addIndex(id);
						log.info("New index {}", id);
					}
					else
					{
						if (idx.getCrc() == crc && idx.getRevision() == rev)
						{
							continue;
						}
					}
					seenChange = true;
					idx.setCrc(crc);
					idx.setRevision(rev);

					enqueueDownload(255, id);
				}
			}
			else
			{
				Index idx = store.findIndex(archiveID);
				IndexData idxd = new IndexData();
				idxd.load(con.data);
				idx.setProtocol(idxd.getProtocol());
				idx.setNamed(idxd.isNamed());
				assert con.crc == idx.getCrc();
				assert con.revision == idx.getRevision();

				Set<Integer> archiveIDs = new HashSet<>();

				for (ArchiveData ard : idxd.getArchives())
				{
					archiveIDs.add(ard.getId());
					Archive ar = idx.getArchive(ard.getId());
					if (ar == null)
					{
						ar = idx.addArchive(ard.getId());
						log.info("New archive {}/{}", idx.getId(), ard.getId());
					}
					else
					{
						if (ar.getRevision() == ard.getRevision()
							&& ar.getCrc() == ard.getCrc()
							& ar.getNameHash() == ard.getNameHash())
						{
							continue;
						}
					}

					seenChange = true;

					ar.setRevision(ard.getRevision());
					ar.setCrc(ard.getCrc());
					ar.setNameHash(ard.getNameHash());

					ar.setFileData(ard.getFiles());

					enqueueDownload(idx.getId(), ar.getArchiveId());
				}

				if (idx.getArchives().removeIf(ar -> !archiveIDs.contains(ar.getArchiveId())))
				{
					seenChange = true;
				}
			}
		}
		else
		{
			seenChange = true;
			Index idx = store.findIndex(indexID);
			Archive ar = idx.getArchive(archiveID);
			store.getStorage().saveArchive(ar, compressed);
		}

		if (seenChange)
		{
			log.info("Got {}/{} with {} bytes", indexID, archiveID, compressed.length);
		}
	}

	public boolean hasSeenChange()
	{
		return seenChange;
	}

	public Set<Integer> getUnreceivedRequests()
	{
		return Collections.unmodifiableSet(out);
	}

	private void write24(int value) throws IOException
	{
		w.writeByte(value >> 16);
		w.writeByte(value >> 8);
		w.writeByte(value);
	}

	@Override
	public void close() throws IOException
	{
		try
		{
			w.flush();
		}
		finally
		{
			try
			{
				w.close();
			}
			finally
			{
				try
				{
					r.close();
				}
				finally
				{
					socket.close();
				}
			}
		}
	}

	@Override
	public String toString()
	{
		return "JS5Client(\"" + hostname + "\")";
	}
}
