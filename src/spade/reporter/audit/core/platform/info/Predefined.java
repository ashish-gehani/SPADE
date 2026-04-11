/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2026 SRI International

 This program is free software: you can redistribute it and/or
 modify it under the terms of the GNU General Public License as
 published by the Free Software Foundation, either version 3 of the
 License, or (at your option) any later version.

 This program is distributed in the hope that it will be useful,
 but WITHOUT ANY WARRANTY; without even the implied warranty of
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 General Public License for more details.

 You should have received a copy of the GNU General Public License
 along with this program. If not, see <http://www.gnu.org/licenses/>.
 --------------------------------------------------------------------------------
 */
package spade.reporter.audit.core.platform.info;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import spade.reporter.audit.core.platform.ID;

/**
 * Hardcoded registry of known platform configurations.
 *
 * Add new entries here; {@link Table} loads them automatically.
 */
public class Predefined{

	public static final ID LINUX_X86_64_KERNEL_4_19 = new ID("linux-x86_64-4.19");
	public static final ID LINUX_X86_64_KERNEL_5_4  = new ID("linux-x86_64-5.4");
	public static final ID LINUX_X86_64_KERNEL_5_15 = new ID("linux-x86_64-5.15");
	public static final ID LINUX_X86_64_KERNEL_6_1  = new ID("linux-x86_64-6.1");
	public static final ID LINUX_X86_64_KERNEL_6_8  = new ID("linux-x86_64-6.8");

	private static final List<Info> ENTRIES = Collections.unmodifiableList(Arrays.asList(
		entry(LINUX_X86_64_KERNEL_4_19, OS.LINUX, Architecture.X86_64,
			Version.parse("18.04"), KernelVersion.parse("4.19.0-generic")),

		entry(LINUX_X86_64_KERNEL_5_4,  OS.LINUX, Architecture.X86_64,
			Version.parse("20.04"), KernelVersion.parse("5.4.0-generic")),

		entry(LINUX_X86_64_KERNEL_5_15, OS.LINUX, Architecture.X86_64,
			Version.parse("22.04"), KernelVersion.parse("5.15.0-generic")),

		entry(LINUX_X86_64_KERNEL_6_1,  OS.LINUX, Architecture.X86_64,
			Version.parse("23.04"), KernelVersion.parse("6.1.0-generic")),

		entry(LINUX_X86_64_KERNEL_6_8,  OS.LINUX, Architecture.X86_64,
			Version.parse("24.04"), KernelVersion.parse("6.8.0-generic"))
	));

	static List<Info> entries(){
		return ENTRIES;
	}

	private static Info entry(
		final ID id,
		final OS os,
		final Architecture architecture,
		final Version osVersion,
		final KernelVersion kernelVersion
	){
		return new Info(id, os, architecture, osVersion, kernelVersion);
	}

	private Predefined(){}

}
