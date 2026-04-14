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
package spade.reporter.audit.linux.platform.type.device;

public class Device {

	private final Type deviceType;
	private final long major;
	private final long minor;

	public Device(
		final Type deviceType, final long major, final long minor
	){
		if(deviceType == null){
			throw new IllegalArgumentException("deviceType cannot be NULL");
		}
		this.major = major;
		this.minor = minor;
		this.deviceType = deviceType;
	}

	public long getMajor(){
		return major;
	}

	public long getMinor(){
		return minor;
	}

	public Type getDeviceType(){
		return deviceType;
	}

}
