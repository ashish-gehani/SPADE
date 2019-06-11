/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2018 SRI International

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
package spade.storage.quickstep;

/**
 * Response data from Quickstep server.
 */
public class QuickstepResponse {
  private String stdout;
  private String stderr;

  public QuickstepResponse(String stdout, String stderr) {
    this.stdout = stdout;
    this.stderr = stderr;
  }

  /**
   * @return Output from Quickstep's standard output stream.
   */
  public String getStdout() {
    return stdout;
  }

  /**
   * @return Output from Quickstep's standard error stream.
   */
  public String getStderr() {
    return stderr;
  }
}
