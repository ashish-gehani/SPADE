package spade.utility;

import com.google.privacy.differentialprivacy.Count;
import spade.query.quickgrail.core.GraphStats;
import spade.query.quickgrail.instruction.StatGraph.AggregateType;

import java.util.*;

public class DifferentialPrivacy
{
    /** algorithm for getting private histogram based on the
     * description and use case as described in:
     * https://github.com/google/differential-privacy/blob/main/examples/java/CountVisitsPerHour.java
     */
    private static final double EPSILON = Math.log(3);

    private Double privateStd(final Double std)
    {
        Count dpCount = Count
                .builder()
                .epsilon(EPSILON)
                .maxPartitionsContributed(1)
                .build();
        dpCount.incrementBy(Math.round(std));
        Double privateStd = (double) dpCount.computeResult();
        return privateStd;
    }

    private Double privateMean(final Double mean)
    {
        Count dpCount = Count
                .builder()
                .epsilon(EPSILON)
                .maxPartitionsContributed(1)
                .build();
        dpCount.incrementBy(Math.round(mean));
        Double privateMean = (double) dpCount.computeResult();
        return privateMean;
    }

    private SortedMap<String, Integer> privateHistogram(final Map<String, Integer> histogram)
    {
        SortedMap<String, Count> dpCounts = new TreeMap<>();
        for (Map.Entry<String, Integer> entry: histogram.entrySet())
        {
            String key = entry.getKey();
            Integer value = entry.getValue();
            Count dpCount = Count
                    .builder()
                    .epsilon(EPSILON)
                    .maxPartitionsContributed(1)
                    .build();
            dpCount.incrementBy(value);
            dpCounts.put(key, dpCount);
        }
        SortedMap<String, Integer> privateHistogram = new TreeMap<>();
        for(Map.Entry<String, Count> dpCount: dpCounts.entrySet())
        {
            String key = dpCount.getKey();
            privateHistogram.put(key, (int) dpCount.getValue().computeResult());
        }
        return privateHistogram;
    }

    private SortedMap<String, Integer> privateDistribution(final Map<String, Integer> distribution)
    {
        SortedMap<String, Count> dpCounts = new TreeMap<>();
        for (Map.Entry<String, Integer> entry: distribution.entrySet())
        {
            String key = entry.getKey();
            Integer value = entry.getValue();
            Count dpCount = Count
                    .builder()
                    .epsilon(EPSILON)
                    .maxPartitionsContributed(1)
                    .build();
            dpCount.incrementBy(value);
            dpCounts.put(key, dpCount);
        }
        SortedMap<String, Integer> privateDistribution = new TreeMap<>();
        for(Map.Entry<String, Count> dpCount: dpCounts.entrySet())
        {
            String key = dpCount.getKey();
            privateDistribution.put(key, (int) dpCount.getValue().computeResult());
        }
        return privateDistribution;
    }

    public void run(GraphStats graphStats, final AggregateType aggregateType)
    {
        if(aggregateType.equals(AggregateType.HISTOGRAM))
        {
            Map<String, Integer> histogram = graphStats.getAggregateStats().getHistogram();
            SortedMap<String, Integer> privateHistogram = privateHistogram(histogram);
            graphStats.getAggregateStats().setHistogram(privateHistogram);
        }
        else if(aggregateType.equals((AggregateType.MEAN)))
        {
            Double mean = graphStats.getAggregateStats().getMean();
            Double privateMean = privateMean(mean);
            graphStats.getAggregateStats().setMean(privateMean);
        }
        else if(aggregateType.equals(AggregateType.STD))
        {
            Double std = graphStats.getAggregateStats().getMean();
            Double privateStd = privateStd(std);
            graphStats.getAggregateStats().setStd(privateStd);
        }
        else if(aggregateType.equals(AggregateType.DISTRIBUTION))
        {
            Map<String, Integer> distribution = graphStats.getAggregateStats().getDistribution();
            SortedMap<String, Integer> privateDistribution = privateDistribution(distribution);
            graphStats.getAggregateStats().setDistribution(privateDistribution);
        }
    }

}
