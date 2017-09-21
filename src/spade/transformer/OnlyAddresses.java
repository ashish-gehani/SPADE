/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2015 SRI International

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

package spade.transformer;

import spade.client.QueryMetaData;
import spade.core.AbstractEdge;
import spade.core.AbstractTransformer;
import spade.core.AbstractVertex;
import spade.core.Edge;
import spade.core.Graph;

import java.util.AbstractMap.SimpleEntry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OnlyAddresses extends AbstractTransformer
{
	
	public Graph putGraph(Graph graph, QueryMetaData queryMetaData)
	{
		/*
		 * Code description:
		 * 
		 * Add all the Address vertices in the final graph when building necessary data structures
		 * Build a map of bitcoin payment vertices to bitcoin address vertices (assumption: every payment vertex is only connected to one address vertex)
		 * Build a map of transaction vertices to 1) list of edges for 'paid from' payment vertices, and 2) list of edges for 'paid to' payment vertices  
		 * For each transaction found, draw edges from addresses of 'paid to' payment vertices to addresses of 'paid from' payment vertices
		 * 
		 * NOTE: Payment-out vertex of one transaction can be Payment-in vertex of another transaction and vice versa.
		 * 
		 * Input graph edges: Tx1 USED P1, P2 WASGENERATEDBY Tx1, P1 -> Address1, P2 -> Address2, Tx2 USED P2 , P3 WASGENERATEDBY Tx2, P3 -> Address3
		 * Output graph edges: Address2 -> Address1, Address3 -> Address2
		 */
		
		Graph resultGraph = new Graph();
		
		Map<AbstractVertex, SimpleEntry<List<AbstractEdge>, List<AbstractEdge>>> transactionsToPayments = new HashMap<>();
		
		Map<AbstractVertex, AbstractVertex> paymentToAddresses = new HashMap<>();
				
		for(AbstractEdge edge : graph.edgeSet())
		{
			if(getAnnotationSafe(edge, "type").equals("WasAttributedTo"))
			{
				paymentToAddresses.put(edge.getChildVertex(), edge.getParentVertex());
				//adding the Agent vertex to the final graph
				resultGraph.putVertex(edge.getParentVertex());
			}
			//list of edges for 'paid from' payment vertices
			else if(getAnnotationSafe(edge, "type").equals("Used"))
			{
				if(transactionsToPayments.get(edge.getChildVertex()) == null)
				{
					transactionsToPayments.put(edge.getChildVertex(), new SimpleEntry<>(new ArrayList<>(), new ArrayList<>()));
				}
				transactionsToPayments.get(edge.getChildVertex()).getKey().add(edge);
			}
			//list of edges for 'paid to' payment vertices
			else if(getAnnotationSafe(edge, "type").equals("WasGeneratedBy"))
			{
				if(transactionsToPayments.get(edge.getParentVertex()) == null)
				{
					transactionsToPayments.put(edge.getParentVertex(), new SimpleEntry<>(new ArrayList<>(), new ArrayList<>()));
				}
				transactionsToPayments.get(edge.getParentVertex()).getValue().add(edge);
			}
		}				
		
		for(AbstractVertex transaction : transactionsToPayments.keySet())
		{
			SimpleEntry<List<AbstractEdge>, List<AbstractEdge>> allPayments = transactionsToPayments.get(transaction);
			if(allPayments != null)
			{
				//'paid from' payments
				List<AbstractEdge> paymentsInEdges = allPayments.getKey();
				//'paid to' payments
				List<AbstractEdge> paymentsOutEdges = allPayments.getValue();
				if(paymentsInEdges != null && paymentsInEdges.size() > 0
						&& paymentsOutEdges != null && paymentsOutEdges.size() > 0)
				{
					for(AbstractEdge paymentInEdge : paymentsInEdges)
					{
						AbstractVertex paymentInAddress = paymentToAddresses.get(paymentInEdge.getParentVertex());
						if(paymentInAddress != null)
						{
							for(AbstractEdge paymentOutEdge : paymentsOutEdges)
							{
								AbstractVertex paymentOutAddress = paymentToAddresses.get(paymentOutEdge.getChildVertex());
								if(paymentOutAddress != null)
								{
									AbstractEdge edge = new Edge(paymentOutAddress, paymentInAddress);
									edge.addAnnotation("type", "ActedOnBehalfOf");
									if(paymentOutEdge.getAnnotation("transactionValue") != null)
									{
										edge.addAnnotation("transactionValue", getAnnotationSafe(paymentOutEdge, "transactionValue"));
									}
									resultGraph.putEdge(edge);
								}
							}
						}
					}
				}
			}
		}
		return resultGraph;
	}
}
