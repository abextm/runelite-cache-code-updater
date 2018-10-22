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
package net.runelite.cache.codeupdater;

import java.io.File;
import java.io.IOException;
import lombok.Getter;
import lombok.Setter;

public class Git
{
	public static String versionString = "";
	public static Git runelite = new Git();
	public static Git srn = new Git();
	public static Git cache = new Git();

	@Getter
	@Setter
	private boolean live = true;

	@Getter
	@Setter
	private File workingDirectory = null;

	public void checkout(String branch)
	{
		exec(false, "git", "checkout", branch);
	}

	public void add(String file)
	{
		exec(false, "git", "add", file);
	}

	public void branch(String name)
	{
		exec(false, "git", "branch", "-f", name);
		exec(false, "git", "checkout", name);
	}

	public void add(File file)
	{
		add(file.getAbsolutePath());
	}

	public void commit(String message)
	{
		exec(true, "git", "commit", "--author=RuneLite Cache-Code Autoupdater <mii7303+rlccau@gmail.com>", "-m", message);
	}

	public void commitUpdate(String object)
	{
		commit("Update " + object + " to " + versionString);
	}

	public void hardReset()
	{
		exec(false, "git", "reset", "--hard");
	}

	private void exec(boolean ignoreError, String... cmd)
	{
		if (!live)
		{
			return;
		}

		try
		{
			ProcessBuilder pb = new ProcessBuilder(cmd);
			pb.directory(workingDirectory);
			pb.inheritIO();
			int exitCode = pb.start().waitFor();
			if (exitCode != 0 && !ignoreError)
			{
				throw new RuntimeException(cmd[0] + " returned " + exitCode);
			}
		}
		catch (IOException | InterruptedException e)
		{
			throw new RuntimeException(e);
		}
	}
}
