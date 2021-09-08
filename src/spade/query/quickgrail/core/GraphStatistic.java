/*
 --------------------------------------------------------------------------------
 SPADE - Support for Provenance Auditing in Distributed Environments.
 Copyright (C) 2020 SRI International
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
package spade.query.quickgrail.core;

import java.io.Serializable;
import java.text.DecimalFormat;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.commons.math3.util.Precision;

import spade.query.quickgrail.types.StringType;
import spade.query.quickgrail.utility.ResultTable;
import spade.query.quickgrail.utility.Schema;
import spade.utility.HelperFunctions;

public abstract class GraphStatistic implements Serializable{

	private static final long serialVersionUID = -8182957484328591997L;

	private int precisionScale;
	private final DecimalFormat df = new DecimalFormat("#");

	public int getPrecisionScale(){
		return precisionScale;
	}

	public void setPrecisionScale(final int precisionScale){
		this.precisionScale = precisionScale; 
	}

	protected String scaleDouble(double value){
		final int pscale = getPrecisionScale();
		if(pscale >= 0){
			value = Precision.round(value, pscale);
			df.setMaximumFractionDigits(pscale);
		}
		return df.format(value);
	}

	public abstract void privatize(final double epsilon);
	public abstract ResultTable getAsResultTable();

	@Override
	public String toString(){
		final ResultTable table = getAsResultTable();
		return table.toString();
	}

	public static class Count extends GraphStatistic{
		private static final long serialVersionUID = 6370902206087786629L;
		private long vertices, edges;

		public Count(){
			this(0, 0);
		}

		public Count(final long vertices, final long edges){
			this.vertices = vertices;
			this.edges = edges;
		}

		public long getVertices(){
			return vertices;
		}

		public long getEdges(){
			return edges;
		}
		
		public boolean isEmpty(){
			return vertices == 0 && edges == 0;
		}

		@Override
		public void privatize(final double epsilon){
			vertices = (long)HelperFunctions.differentiallyPrivatize(vertices, epsilon);
			edges = (long)HelperFunctions.differentiallyPrivatize(edges, epsilon);
		}

		@Override
		public ResultTable getAsResultTable(){
			final ResultTable table = new ResultTable();
			final ResultTable.Row row = new ResultTable.Row();
			row.add(vertices);
			row.add(edges);

			table.addRow(row);

			final Schema schema = new Schema();
			schema.addColumn("Number of Vertices", StringType.GetInstance());
			schema.addColumn("Number of Edges", StringType.GetInstance());

			table.setSchema(schema);

			return table;
		}
	}

	public static class Histogram extends GraphStatistic{
		private static final long serialVersionUID = 6008488148398456093L;
		private SortedMap<String, Double> histogram = new TreeMap<>();

		public Histogram(){
			this(null);
		}

		public Histogram(final SortedMap<String, Double> histogram){
			if(histogram != null){
				this.histogram.putAll(histogram);
			}
		}

		public SortedMap<String, Double> getHistogram(){
			return new TreeMap<>(this.histogram);
		}

		@Override
		public void privatize(final double epsilon){
			for(final Map.Entry<String, Double> entry : this.histogram.entrySet()){
				final Double value = entry.getValue();
				final double privatized = Math.max(
						0, (int)HelperFunctions.differentiallyPrivatize(value, epsilon)
						);
				entry.setValue(privatized);
			}
		}

		@Override
		public ResultTable getAsResultTable(){
			final ResultTable table = new ResultTable();
			for(final Map.Entry<String, Double> entry : this.histogram.entrySet()){
				final ResultTable.Row row = new ResultTable.Row();
				row.add(entry.getKey());
				row.add(scaleDouble(entry.getValue()));
				table.addRow(row);
			}
			final Schema schema = new Schema();
			schema.addColumn("Value", StringType.GetInstance());
			schema.addColumn("Count", StringType.GetInstance());

			table.setSchema(schema);

			return table;
		}
	}

	public static class StandardDeviation extends GraphStatistic{
		private static final long serialVersionUID = -1464752101315726846L;
		private double standardDeviation;

		public StandardDeviation(){
			this(0);
		}

		public StandardDeviation(final double standardDeviation){
			this.standardDeviation = standardDeviation;
		}

		public double getStandardDeviation(){
			return standardDeviation;
		}

		@Override
		public void privatize(final double epsilon){
			standardDeviation = HelperFunctions.differentiallyPrivatize(standardDeviation, epsilon);
		}

		@Override
		public ResultTable getAsResultTable(){
			final ResultTable table = new ResultTable();
			final ResultTable.Row row = new ResultTable.Row();
			row.add("Standard Deviation");
			row.add(scaleDouble(standardDeviation));

			table.addRow(row);

			final Schema schema = new Schema();
			schema.addColumn("Statistic", StringType.GetInstance());
			schema.addColumn("Value", StringType.GetInstance());

			table.setSchema(schema);

			return table;
		}
	}

	public static class Mean extends GraphStatistic{
		private static final long serialVersionUID = 6992349772700489521L;
		private double mean;

		public Mean(){
			this(0);
		}

		public Mean(final double mean){
			this.mean = mean;
		}

		public double getMean(){
			return mean;
		}

		@Override
		public void privatize(final double epsilon){
			mean = HelperFunctions.differentiallyPrivatize(mean, epsilon);
		}

		@Override
		public ResultTable getAsResultTable(){
			final ResultTable table = new ResultTable();
			final ResultTable.Row row = new ResultTable.Row();
			row.add("Mean");
			row.add(scaleDouble(mean));

			table.addRow(row);

			final Schema schema = new Schema();
			schema.addColumn("Statistic", StringType.GetInstance());
			schema.addColumn("Value", StringType.GetInstance());

			table.setSchema(schema);

			return table;
		}
	}

	public static class Distribution extends GraphStatistic{
		private static final long serialVersionUID = 8394823240406671826L;
		private SortedMap<Interval, Double> distribution = new TreeMap<>();

		public Distribution(){
			this(null);
		}

		public Distribution(final SortedMap<Interval, Double> distribution){
			if(distribution != null){
				this.distribution.putAll(distribution);
			}
		}

		public SortedMap<Interval, Double> getDistribution(){
			return new TreeMap<>(this.distribution);
		}

		@Override
		public void privatize(final double epsilon){
			for(final Map.Entry<Interval, Double> entry : this.distribution.entrySet()){
				final Double value = entry.getValue();
				final double privatized = Math.max(
						0, (int)HelperFunctions.differentiallyPrivatize(value, epsilon)
						);
				entry.setValue(privatized);
			}
		}

		@Override
		public ResultTable getAsResultTable(){
			final ResultTable table = new ResultTable();
			for(final Map.Entry<Interval, Double> entry : this.distribution.entrySet()){
				final ResultTable.Row row = new ResultTable.Row();
				final Interval intervalKey = entry.getKey();
				row.add(scaleDouble(intervalKey.from));
				row.add(scaleDouble(intervalKey.to));
				row.add(scaleDouble(entry.getValue()));
				table.addRow(row);
			}
			final Schema schema = new Schema();
			schema.addColumn("From", StringType.GetInstance());
			schema.addColumn("To", StringType.GetInstance());
			schema.addColumn("Count", StringType.GetInstance());

			table.setSchema(schema);

			return table;
		}
	}
	
	public static class Interval implements Comparable<Interval>, Serializable{
		private static final long serialVersionUID = -3602806522724173024L;
		public final double from, to;
		public Interval(final double from, final double to){
			this.from = from;
			this.to = to;
		}
		@Override
		public int hashCode(){
			final int prime = 31;
			int result = 1;
			long temp;
			temp = Double.doubleToLongBits(from);
			result = prime * result + (int)(temp ^ (temp >>> 32));
			temp = Double.doubleToLongBits(to);
			result = prime * result + (int)(temp ^ (temp >>> 32));
			return result;
		}
		@Override
		public boolean equals(Object obj){
			if(this == obj)
				return true;
			if(obj == null)
				return false;
			if(getClass() != obj.getClass())
				return false;
			Interval other = (Interval)obj;
			if(Double.doubleToLongBits(from) != Double.doubleToLongBits(other.from))
				return false;
			if(Double.doubleToLongBits(to) != Double.doubleToLongBits(other.to))
				return false;
			return true;
		}
		@Override
		public String toString(){
			return from + " - " + to;
		}
		@Override
		public int compareTo(final Interval o){
			if(o == null){
				return -1;
			}
			if(from < o.from){
				return -1;
			}else if(from > o.from){
				return 1;
			}else{
				if(to < o.to){
					return -1;
				}else if(to > o.to){
					return 1;
				}else{
					return 0;
				}
			}
		}
	}
}