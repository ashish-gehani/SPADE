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
package spade.reporter.audit.linux.source.audit.event.record.type.saddr;

/**
 * AF_UNIX sockaddr decoded from its hex payload.
 *
 * Three variants based on sun_path (bytes after the 2-byte family field):
 *   Named    – sun_path[0] is non-null; path is a filesystem path.
 *   Abstract – sun_path[0] is '\0' followed by at least one non-null byte;
 *              path is the name in the abstract namespace (the leading null
 *              is stripped). {@link #isAbstract()} returns true.
 *   Unnamed  – no sun_path bytes, or all null; path is an empty string.
 *
 * {@link #getPath()} returns null if the hex could not be decoded.
 * Root-path resolution for named sockets is left to the caller.
 */
public class UnixSaddr extends Saddr{

	// sun_path starts at this hex-string index (2 bytes of sa_family = 4 hex chars)
	private static final int SUN_PATH_OFFSET = 4;

	private final String path;
	private final boolean abstractSocket;

	private UnixSaddr(final String hex, final String path, final boolean abstractSocket){
		super(hex, Family.UNIX);
		this.path = path;
		this.abstractSocket = abstractSocket;
	}

	static UnixSaddr create(final String hex){
		if(hex.length() <= SUN_PATH_OFFSET){
			return new UnixSaddr(hex, "", false);
		}

		final String firstByte = hex.substring(SUN_PATH_OFFSET, SUN_PATH_OFFSET + 2);

		if(firstByte.equals("00")){
			// Abstract or unnamed: look for any non-null byte after the leading null.
			boolean hasNonNull = false;
			for(int i = SUN_PATH_OFFSET + 2; i <= hex.length() - 2; i += 2){
				if(!hex.substring(i, i + 2).equals("00")){
					hasNonNull = true;
					break;
				}
			}
			if(hasNonNull){
				// Abstract socket: name begins at sun_path[1].
				return new UnixSaddr(hex, decodeName(hex, SUN_PATH_OFFSET + 2), true);
			}else{
				return new UnixSaddr(hex, "", false);
			}
		}else{
			// Named socket: path begins at sun_path[0].
			return new UnixSaddr(hex, decodeName(hex, SUN_PATH_OFFSET), false);
		}
	}

	/**
	 * Decoded path or abstract-namespace name.
	 * Empty string for unnamed sockets, null if decoding failed.
	 */
	public String getPath(){
		return path;
	}

	/** True when this is an abstract-namespace socket (sun_path starts with '\0'). */
	public boolean isAbstract(){
		return abstractSocket;
	}

	private static String decodeName(final String hex, final int startIndex){
		final StringBuilder name = new StringBuilder();
		try{
			for(int i = startIndex; i <= hex.length() - 2; i += 2){
				final char c = (char) Integer.parseInt(hex.substring(i, i + 2), 16);
				if(c == 0){
					break;
				}
				name.append(c);
			}
		}catch(final Exception e){
			return null;
		}
		return name.toString();
	}
}
