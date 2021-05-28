package spade.utility;

import com.google.privacy.differentialprivacy.Count;
import spade.query.quickgrail.core.GraphStats;
import spade.query.quickgrail.instruction.StatGraph.AggregateType;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DifferentialPrivacy
{
	private static final Logger logger = Logger.getLogger(DifferentialPrivacy.class.getName());

    /** algorithm for getting private histogram based on the
     * description and use case as described in:
     * https://github.com/google/differential-privacy/blob/main/examples/java/CountVisitsPerHour.java
     */
    private static Double privateStd(final Double std, final double epsilon)
    {
        Count dpCount = Count
                .builder()
                .epsilon(epsilon)
                .maxPartitionsContributed(1)
                .build();
        dpCount.incrementBy(Math.round(std));
        Double privateStd = (double) dpCount.computeResult();
        return privateStd;
    }

    private static Double privateMean(final Double mean, final double epsilon)
    {
        Count dpCount = Count
                .builder()
                .epsilon(epsilon)
                .maxPartitionsContributed(1)
                .build();
        dpCount.incrementBy(Math.round(mean));
        Double privateMean = (double) dpCount.computeResult();
        return privateMean;
    }

    private static SortedMap<String, Integer> privateHistogram(final Map<String, Integer> histogram, final double epsilon)
    {
        SortedMap<String, Count> dpCounts = new TreeMap<>();
        for (Map.Entry<String, Integer> entry: histogram.entrySet())
        {
            String key = entry.getKey();
            Integer value = entry.getValue();
            Count dpCount = Count
                    .builder()
                    .epsilon(epsilon)
                    .maxPartitionsContributed(1)
                    .build();
            dpCount.incrementBy(value);
            dpCounts.put(key, dpCount);
        }
        SortedMap<String, Integer> privateHistogram = new TreeMap<>();
        for(Map.Entry<String, Count> dpCount: dpCounts.entrySet())
        {
            String key = dpCount.getKey();
            privateHistogram.put(key, Math.max(0, (int) dpCount.getValue().computeResult()));
        }
        return privateHistogram;
    }

    private static SortedMap<String, Integer> privateDistribution(final Map<String, Integer> distribution, final double epsilon)
    {
        SortedMap<String, Count> dpCounts = new TreeMap<>();
        for (Map.Entry<String, Integer> entry: distribution.entrySet())
        {
            String key = entry.getKey();
            Integer value = entry.getValue();
            Count dpCount = Count
                    .builder()
                    .epsilon(epsilon)
                    .maxPartitionsContributed(1)
                    .build();
            dpCount.incrementBy(value);
            dpCounts.put(key, dpCount);
        }
        SortedMap<String, Integer> privateDistribution = new TreeMap<>();
        for(Map.Entry<String, Count> dpCount: dpCounts.entrySet())
        {
            String key = dpCount.getKey();
            privateDistribution.put(key, Math.max(0, (int) dpCount.getValue().computeResult()));
        }
        return privateDistribution;
    }

    public static void run(GraphStats graphStats, final AggregateType aggregateType, final double epsilon)
    {
        if(aggregateType.equals(AggregateType.HISTOGRAM))
        {
            Map<String, Integer> histogram = graphStats.getAggregateStats().getHistogram();
            SortedMap<String, Integer> privateHistogram = privateHistogram(histogram, epsilon);
            graphStats.getAggregateStats().setHistogram(privateHistogram);
        }
        else if(aggregateType.equals((AggregateType.MEAN)))
        {
            Double mean = graphStats.getAggregateStats().getMean();
            Double privateMean = privateMean(mean, epsilon);
            graphStats.getAggregateStats().setMean(privateMean);
        }
        else if(aggregateType.equals(AggregateType.STD))
        {
            Double std = graphStats.getAggregateStats().getStd();
            Double privateStd = privateStd(std, epsilon);
            graphStats.getAggregateStats().setStd(privateStd);
        }
        else if(aggregateType.equals(AggregateType.DISTRIBUTION))
        {
            Map<String, Integer> distribution = graphStats.getAggregateStats().getDistribution();
            SortedMap<String, Integer> privateDistribution = privateDistribution(distribution, epsilon);
            graphStats.getAggregateStats().setDistribution(privateDistribution);
        }else{
		throw new RuntimeException("Unsupported aggregate statistics type: " + aggregateType);
	}
    }

}
