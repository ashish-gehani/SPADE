/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2017 SRI International
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


import java.io.Serializable;

public class Query implements Serializable
{
    private String queryString;
    private String queryTime;   //used as nonce

    public Query()
    {
    }

    public Query(String queryString, String queryTime)
    {
        this.queryString = queryString;
        this.queryTime = queryTime;
    }

    public String getQueryString()
    {
        return queryString;
    }

    public void setQueryString(String queryString)
    {
        this.queryString = queryString;
    }

    public String getQueryTime()
    {
        return queryTime;
    }

    public void setQueryTime(String queryTime)
    {
        this.queryTime = queryTime;
    }
}
