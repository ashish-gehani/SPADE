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
 
 USAGE: java CDCConstructor <filename>
 Where the file should contain lines in the format:
 N : N
 where N are numbers
 */
package spade.utility;


import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.Map;
import java.util.HashMap;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.XYChart;
import javafx.stage.Stage;

/**
 * @author Carolina de Senne Garcia
 */
public class DelayChartConstructor extends Application {

	public static String filename;
	public static final String pattern = "^(\\d+)\\s:\\s(\\d+)$";
	public static final double FRACTION = 0.93; // fraction of occurences to be taken into account in the graphic
	public static Map<Integer,Integer> delayOccurences = new HashMap<Integer,Integer>();

	/**
	 * Calculate the delay mean and variance
	 *
	 * @return double array with the mean in the first case and variance in the seconde one
	 */
	public static double[] calculateStatistics() {
		int n = 0;
		double mean = 0;
		double variance = 0;
		// calculate mean
		for(Map.Entry<Integer,Integer> entry: delayOccurences.entrySet()) {
			int weight = entry.getValue();
			int delay = entry.getKey();
			n = n+weight;
			mean = mean+(weight*delay);
		}
		mean = mean/n;
		// calculate variance
		for(Map.Entry<Integer,Integer> entry: delayOccurences.entrySet()) {
			int weight = entry.getValue();
			int delay = entry.getKey();
			variance = variance+(weight*(delay-mean)*(delay-mean));
		}
		variance = variance/n;
		double[] statistics = new double[2];
		statistics[0] = mean;
		statistics[1] = variance;
		return statistics;
	}

	/**
	 * Constructs the chart that contains the delay distribution
	 */
	@Override public void start(Stage stage) {
		// General chart configuration
		stage.setTitle("Delay-Occurences Chart");
		final CategoryAxis xAxis = new CategoryAxis();
		final NumberAxis yAxis = new NumberAxis();
		final BarChart<String,Number> chart = new BarChart<String,Number>(xAxis,yAxis);
		chart.setTitle("Record's Delay Distribution");
		xAxis.setLabel("Delay");
		yAxis.setLabel("Occurences");
		// Series
		XYChart.Series<String,Number> series = new XYChart.Series<String,Number>();
		constructSeries(series,FRACTION);
		// Chart
		Scene scene = new Scene(chart,800,600);
		chart.getData().add(series);
		stage.setScene(scene);
		stage.show();
	}

	/**
	 * Constructs the data to be added to the series in the Cumulative Distribution Function
	 * 
	 * @param series to be constructed
	 * @param fraction of the data that should be inserted to the series (greater occurrences will be ignored)
	 */
	public static void constructSeries(XYChart.Series<String,Number> series, double fraction) {
		int[] info = readMapFromFile(filename);
		int max = info[0];
		int total = info[1];
		Integer occurences;
		int countOccurences = 0;
		for(int i = 1; i < max && countOccurences < fraction*total; i++) {
			occurences = delayOccurences.get(i);
			if(occurences == null) {
				series.getData().add(new XYChart.Data<String,Number>(Integer.toString(i),0));
			} else {
				series.getData().add(new XYChart.Data<String,Number>(Integer.toString(i),occurences));
				countOccurences += occurences;
			}
		}
	}

	/**
	 * Reads file in path in order to construct the mapping delayOccurences between delays found and their respective number of occurrences
	 * 
	 * @param path for the file to be read
	 * @return an int array of size two, with the first value being the maximum delay value found and the second being the total number of occurrences
	 */
	public static int[] readMapFromFile(String path)  {
		// information about data
		int[] info = new int[2];
		info[0] = 0;						// corresponds to max delay value
		info[1] = 0;						// corresponds to total number of occurences
 		// Matcher for file lines
		Pattern p = Pattern.compile(pattern);
		Matcher m = null;
		// read from delayMap file
		int delay=0;
		int occurences=0;
		BufferedReader buffer = openFile(path);
		String line = null;
		try {
			while((line = buffer.readLine()) != null) {
				m = p.matcher(line);
				if(m.find()) {
					// put information from file in the map
					delay = Integer.parseInt(m.group(1));
					occurences = Integer.parseInt(m.group(2));
					delayOccurences.put(delay,occurences);
					if(delay > info[0])
						info[0] = delay;
					info[1] += occurences;
				}
			}
		} catch(IOException e) {
			System.err.println(e.getMessage());
			System.err.println("Problem reading from file");
		}
		return info;
	}

	public static void main(String[] args) {
		if(args.length < 1) {
			System.out.println("Please provide the delayMap file name");
			return;
		}
		filename = args[0];
		launch(args);
		double[] stat = calculateStatistics();
		System.out.println("Mean = "+stat[0]);
		System.out.println("Variance = "+stat[1]);
	}

	/** 
	 * Opens a file and return its respective BufferedReader
	 *
	 * @param path to file to be opened
	 * @return buffer to read the log file explicited in path
	 */
	public static BufferedReader openFile(String path) {
		FileReader fr = null;
		try {
			fr = new FileReader(path);
		} catch(IOException e) {
			System.err.println(e.getMessage());
			System.err.println("Couldn't open file: "+path);
		}
		return new BufferedReader(fr);
	}
}
