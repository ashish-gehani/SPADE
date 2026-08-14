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
package spade.utility.setting.source.file;

import java.io.IOException;

import spade.utility.setting.source.Source;

public abstract class File extends Source {
  private final java.io.File file;
  private boolean loaded;

  public File(java.io.File file) {
    this.file = file;
  }

  public java.io.File getFile() {
    return file;
  }

  public boolean isLoaded() {
    return loaded;
  }

  @Override
  public spade.utility.setting.source.Type getSourceType() {
    return spade.utility.setting.source.Type.FILE;
  }

  public abstract Type getType();

  /**
   * Reads the file and loads its contents into the internal representation.
   *
   * @throws IOException if the file cannot be read.
   * @throws IllegalStateException if the file has already been loaded.
   */
  public final void load() throws IOException, IllegalStateException {
    if (loaded) {
      throw new IllegalStateException("File has already been loaded: " + file);
    }
    doLoad();
    loaded = true;
  }

  protected abstract void doLoad() throws IOException;
}
