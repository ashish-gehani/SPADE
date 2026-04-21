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
package spade.reporter.audit.linux.platform.type.fs;

import java.util.ArrayDeque;
import java.util.Deque;

import spade.reporter.audit.linux.platform.constant.Constant;

public class Path{

	private final static String PATH_SEPARATOR = Constant.INSTANCE.PATH.SEPARATOR;

	private final String root;
	private final String unresolvedPath;
	private final String resolvedPath;

	public Path(final String root, final String unresolvedPath){
		if(root == null){
			throw new IllegalArgumentException("root cannot be NULL");
		}
		if(!root.startsWith(PATH_SEPARATOR)){
			throw new IllegalArgumentException("root must be an absolute path starting with '/'");
		}
		if(unresolvedPath == null){
			throw new IllegalArgumentException("unresolvedPath cannot be NULL");
		}
		if(!unresolvedPath.startsWith(PATH_SEPARATOR)){
			throw new IllegalArgumentException("unresolvedPath must be an absolute path starting with '/'");
		}
		this.root = root;
		this.unresolvedPath = unresolvedPath;
		this.resolvedPath = resolvePath();
	}

	public String getRoot(){ return root; }
	public String getUnresolvedPath(){ return unresolvedPath; }
	public String getResolvedPath(){ return resolvedPath; }

	private String resolvePath(){
		// When root is filesystem root, pathStr is already the resolved path.
		// Otherwise prepend root (chroot case): /container/rootfs + /etc/passwd
		final String combined = root.equals(PATH_SEPARATOR)
				? unresolvedPath
				: root + unresolvedPath;

		// Normalize: resolve '.' and '..', collapse consecutive slashes
		final String[] parts = combined.split(PATH_SEPARATOR, -1);
		final Deque<String> stack = new ArrayDeque<>();
		for(final String part : parts){
			if(part.isEmpty() || part.equals(".")){
				// skip empty segments (from leading/trailing/consecutive slashes) and current-dir refs
				continue;
			} else if(part.equals("..")){
				// parent-dir: pop last component, but never escape above root
				if(!stack.isEmpty()){
					stack.pollLast();
				}
			} else{
				stack.addLast(part);
			}
		}

		if(stack.isEmpty()){
			return PATH_SEPARATOR;
		}

		final StringBuilder sb = new StringBuilder();
		for(final String part : stack){
			sb.append(PATH_SEPARATOR).append(part);
		}
		return sb.toString();
	}

	public Path(final Path other){
		this(other.root, other.unresolvedPath);
	}

}
