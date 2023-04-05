package org.lenskit.mooc.ii;

import it.unimi.dsi.fastutil.longs.Long2DoubleMap;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import org.lenskit.api.Result;
import org.lenskit.api.ResultMap;
import org.lenskit.basic.AbstractItemScorer;
import org.lenskit.data.dao.DataAccessObject;
import org.lenskit.data.entities.CommonAttributes;
import org.lenskit.data.ratings.Rating;
import org.lenskit.results.Results;
import org.lenskit.util.ScoredIdAccumulator;
import org.lenskit.util.TopNScoredIdAccumulator;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * @author <a href="http://www.grouplens.org">GroupLens Research</a>
 */
public class SimpleItemItemScorer extends AbstractItemScorer {
    private final SimpleItemItemModel model;
    private final DataAccessObject dao;
    private final int neighborhoodSize;

    @Inject
    public SimpleItemItemScorer(SimpleItemItemModel m, DataAccessObject dao) {
        model = m;
        this.dao = dao;
        neighborhoodSize = 20;
    }

    /**
     * Score items for a user.
     * 
     * @param user  The user ID.
     * @param items The score vector. Its key domain is the items to score, and the
     *              scores
     *              (rating predictions) should be written back to this vector.
     */
    @Override
    public ResultMap scoreWithDetails(long user, @Nonnull Collection<Long> items) {
        Long2DoubleMap itemMeans = model.getItemMeans();
        Long2DoubleMap ratings = getUserRatingVector(user);

        // TODO Normalize the user's ratings by subtracting the item mean from each one.
        for (Map.Entry<Long, Double> rating : ratings.entrySet()) {
            Double mean = itemMeans.get(rating.getKey());
            rating.setValue(rating.getValue() - mean);
        }
        List<Result> results = new ArrayList<>();
        for (long item : items) {
            double mean = itemMeans.get(item);
            Long2DoubleMap similarityMatrix = this.model.getNeighbors(item);
            // TODO Compute the user's score for each item, add it to results
            List<Map.Entry<Long, Double>> list = new ArrayList<Map.Entry<Long, Double>>(similarityMatrix.entrySet());
            Collections.sort(list, new Comparator<Map.Entry<Long, Double>>() {
                @Override
                public int compare(Map.Entry<Long, Double> o1, Map.Entry<Long, Double> o2) {
                    return o2.getValue().compareTo(o1.getValue());
                }
            });
            int i = 0;
            double numerator = 0;
            double denominator = 0;
            for (Map.Entry<Long, Double> neighbor : list) {
                if (i >= this.neighborhoodSize) {
                    break;
                }
                if (ratings.containsKey(neighbor.getKey())) {
                    Double sim = neighbor.getValue();
                    Double ratingScore = ratings.get(neighbor.getKey());
                    numerator += sim * ratingScore;
                    denominator += sim;
                    i++;
                }
            }
            double score = (numerator / denominator) + mean;
            if (score > 0) {
                results.add(Results.create(item, score));
            }
        }

        Collections.sort(results, new Comparator<Result>() {
            @Override
            public int compare(Result o1, Result o2) {
                return new Double(o2.getScore()).compareTo(new Double(o1.getScore()));
            }
        });

        return Results.newResultMap(results);

    }

    /**
     * Get a user's ratings.
     * 
     * @param user The user ID.
     * @return The ratings to retrieve.
     */
    private Long2DoubleOpenHashMap getUserRatingVector(long user) {
        List<Rating> history = dao.query(Rating.class)
                .withAttribute(CommonAttributes.USER_ID, user)
                .get();

        Long2DoubleOpenHashMap ratings = new Long2DoubleOpenHashMap();
        for (Rating r : history) {
            ratings.put(r.getItemId(), r.getValue());
        }

        return ratings;
    }

}
