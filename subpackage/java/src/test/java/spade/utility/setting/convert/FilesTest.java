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
package spade.utility.setting.convert;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class FilesTest {
  @TempDir
  Path tempDir;

  @Test
  public void parsesExecutableFile() throws IOException {
    File file = newFile("script");
    file.setExecutable(true);

    assertEquals(file, Files.parseExecutableFile(file.getPath()));
  }

  @Test
  public void rejectsNonExecutableFile() throws IOException {
    File file = newFile("data");
    file.setExecutable(false);

    assertThrows(IllegalArgumentException.class, () -> Files.parseExecutableFile(file.getPath()));
  }

  @Test
  public void rejectsMissingExecutableFile() {
    File file = tempDir.resolve("missing").toFile();

    assertThrows(IllegalArgumentException.class, () -> Files.parseExecutableFile(file.getPath()));
  }

  @Test
  public void parsesReadableFile() throws IOException {
    File file = newFile("data");
    file.setReadable(true);

    assertEquals(file, Files.parseReadableFile(file.getPath()));
  }

  @Test
  public void rejectsNonReadableFile() throws IOException {
    File file = newFile("data");
    file.setReadable(false);

    assertThrows(IllegalArgumentException.class, () -> Files.parseReadableFile(file.getPath()));
  }

  @Test
  public void rejectsReadableFileWhenPathIsDirectory() {
    File dir = tempDir.resolve("dir").toFile();
    dir.mkdir();

    assertThrows(IllegalArgumentException.class, () -> Files.parseReadableFile(dir.getPath()));
  }

  @Test
  public void parsesWritableFile() throws IOException {
    File file = newFile("data");
    file.setWritable(true);

    assertEquals(file, Files.parseWritableFile(file.getPath()));
  }

  @Test
  public void rejectsNonWritableFile() throws IOException {
    File file = newFile("data");
    file.setWritable(false);

    assertThrows(IllegalArgumentException.class, () -> Files.parseWritableFile(file.getPath()));
  }

  @Test
  public void parsesReadableDirectory() {
    File dir = tempDir.resolve("dir").toFile();
    dir.mkdir();
    dir.setReadable(true);

    assertEquals(dir, Files.parseReadableDirectory(dir.getPath()));
  }

  @Test
  public void rejectsReadableDirectoryWhenPathIsFile() throws IOException {
    File file = newFile("data");

    assertThrows(IllegalArgumentException.class, () -> Files.parseReadableDirectory(file.getPath()));
  }

  @Test
  public void parsesWritableDirectory() {
    File dir = tempDir.resolve("dir").toFile();
    dir.mkdir();
    dir.setWritable(true);

    assertEquals(dir, Files.parseWritableDirectory(dir.getPath()));
  }

  @Test
  public void rejectsNonWritableDirectory() {
    File dir = tempDir.resolve("dir").toFile();
    dir.mkdir();
    dir.setWritable(false);

    assertThrows(IllegalArgumentException.class, () -> Files.parseWritableDirectory(dir.getPath()));
  }

  @Test
  public void parsesCreatableFileWhenAlreadyExistsAndWritable() throws IOException {
    File file = newFile("existing.txt");
    file.setWritable(true);

    assertEquals(file, Files.parseCreatableFile(file.getPath()));
  }

  @Test
  public void parsesCreatableFileWhenMissingButParentIsWritable() {
    File file = tempDir.resolve("new.txt").toFile();

    assertEquals(file, Files.parseCreatableFile(file.getPath()));
  }

  @Test
  public void rejectsCreatableFileWhenParentIsMissing() {
    File file = tempDir.resolve("missingParent").resolve("new.txt").toFile();

    assertThrows(IllegalArgumentException.class, () -> Files.parseCreatableFile(file.getPath()));
  }

  @Test
  public void rejectsCreatableFileWhenPathIsExistingDirectory() {
    File dir = tempDir.resolve("dir").toFile();
    dir.mkdir();

    assertThrows(IllegalArgumentException.class, () -> Files.parseCreatableFile(dir.getPath()));
  }

  @Test
  public void rejectsCreatableFileWhenExistingFileNotWritable() throws IOException {
    File file = newFile("readonly.txt");
    file.setWritable(false);

    assertThrows(IllegalArgumentException.class, () -> Files.parseCreatableFile(file.getPath()));
  }

  @Test
  public void parsesCreatableDirectoryWhenAlreadyExistsAndWritable() {
    File dir = tempDir.resolve("existingDir").toFile();
    dir.mkdir();
    dir.setWritable(true);

    assertEquals(dir, Files.parseCreatableDirectory(dir.getPath()));
  }

  @Test
  public void parsesCreatableDirectoryWhenMissingButParentIsWritable() {
    File dir = tempDir.resolve("newDir").toFile();

    assertEquals(dir, Files.parseCreatableDirectory(dir.getPath()));
  }

  @Test
  public void rejectsCreatableDirectoryWhenParentIsMissing() {
    File dir = tempDir.resolve("missingParent").resolve("newDir").toFile();

    assertThrows(IllegalArgumentException.class, () -> Files.parseCreatableDirectory(dir.getPath()));
  }

  @Test
  public void rejectsCreatableDirectoryWhenPathIsExistingFile() throws IOException {
    File file = newFile("data");

    assertThrows(IllegalArgumentException.class, () -> Files.parseCreatableDirectory(file.getPath()));
  }

  @Test
  public void rejectsCreatableDirectoryWhenExistingDirectoryNotWritable() {
    File dir = tempDir.resolve("readonlyDir").toFile();
    dir.mkdir();
    dir.setWritable(false);

    assertThrows(IllegalArgumentException.class, () -> Files.parseCreatableDirectory(dir.getPath()));
  }

  @Test
  public void handlesRootPathWithoutNullPointerException() {
    // "/" always exists, so this can't reach the null-parent branch through the real filesystem;
    // it only guards against a NullPointerException there. Whether "/" is writable (and so
    // whether this throws IllegalArgumentException) depends on the user running the test.
    assertDoesNotThrow(() -> {
      try {
        Files.parseCreatableDirectory("/");
      } catch (IllegalArgumentException expectedWhenRootIsNotWritable) {
        // Ignored: the point of this test is the absence of a NullPointerException.
      }
    });
  }

  private File newFile(String name) throws IOException {
    File file = tempDir.resolve(name).toFile();
    file.createNewFile();
    return file;
  }
}
