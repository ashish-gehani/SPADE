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
package spade.utility.setting;

/**
 * Thrown by consumers of {@link Setting} when a setting is missing, malformed, or otherwise
 * fails a consumer-defined validation rule, as opposed to the parse/resolve/convert failures
 * raised by the {@code setting} package's own stages.
 */
public class InvalidSettingException extends Exception {
  public InvalidSettingException(String msg) {
    super(msg);
  }

  public InvalidSettingException(String msg, Throwable cause) {
    super(msg, cause);
  }
}
