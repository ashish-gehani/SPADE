/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2025 SRI International

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

package spade.utility;

public class BufferTtlState {

    private final long ttlMillis;

    private volatile long lastFlushMillis = -1;
    private volatile boolean expired = false;

    public BufferTtlState (final long bufferTtl) {
        this.ttlMillis = bufferTtl;
    }

    public void reset() {
        this.lastFlushMillis = System.currentTimeMillis();
        this.expired = false;
    }

    public void initOrUpdate() {
        // init
        if (this.lastFlushMillis == -1) {
            reset();
        }
        // update
        if (System.currentTimeMillis() - this.lastFlushMillis >= this.ttlMillis) {
            this.expired = true;
        }
    }

    public boolean isExpired() {
        return this.expired;
    }
}
