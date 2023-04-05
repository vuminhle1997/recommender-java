package org.lenskit.mooc.uu;

import com.google.common.collect.Maps;
import it.unimi.dsi.fastutil.longs.Long2DoubleMap;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2DoubleSortedMap;
import it.unimi.dsi.fastutil.longs.LongSet;

import org.lenskit.api.Result;
import org.lenskit.api.ResultList;
import org.lenskit.api.ResultMap;
import org.lenskit.basic.AbstractItemScorer;
import org.lenskit.data.dao.DataAccessObject;
import org.lenskit.data.entities.CommonAttributes;
import org.lenskit.data.entities.CommonTypes;
import org.lenskit.data.ratings.Rating;
import org.lenskit.results.Results;
import org.lenskit.util.ScoredIdAccumulator;
import org.lenskit.util.TopNScoredIdAccumulator;
import org.lenskit.util.collections.LongUtils;
import org.lenskit.util.math.Scalars;
import org.lenskit.util.math.Vectors;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

/**
 * User-user item scorer.
 * 
 * @author <a href="http://www.grouplens.org">GroupLens Research</a>
 */
public class SimpleUserUserItemScorer extends AbstractItemScorer {
    private final DataAccessObject dao;
    private final int neighborhoodSize;

    /**
     * Instantiate a new user-user item scorer.
     * 
     * @param dao The data access object.
     */
    @Inject
    public SimpleUserUserItemScorer(DataAccessObject dao) {
        this.dao = dao;
        neighborhoodSize = 30;
    }

    @Nonnull
    @Override
    public ResultMap scoreWithDetails(long user, @Nonnull Collection<Long> items) {
        List<Result> results = new ArrayList<>();

        // ratings of User u
        Long2DoubleOpenHashMap userRatings = getUserRatingVector(user);
        LongSet users = dao.getEntityIds(CommonTypes.USER);

        // Set of other users V
        Map<Long, Long2DoubleOpenHashMap> V = new HashMap<>();
        // iterate through V, get the ratings of v and adapt the ratings riv = (r_v -
        // avgROfV) and of User u
        for (Long v : users) {
            Long2DoubleOpenHashMap ratings = getUserRatingVector(v);
            Double meanV = Vectors.mean(ratings);
            for (Entry<Long, Double> e : ratings.entrySet()) {
                e.setValue(e.getValue() - meanV);
            }
            V.put(v, ratings);
        }

        // pearson correlation aka. cosine similarities
        Map<Long, Double> similarities = new HashMap<>();

        for (Long v : users) {
            if (v != user) {
                Long2DoubleOpenHashMap vRatings = V.get(v);
                Double numerator = Vectors.dotProduct(V.get(user), vRatings);
                Double denominator = Vectors.euclideanNorm(vRatings) * Vectors.euclideanNorm(V.get(user));
                Double sim = numerator / denominator;
                if (sim > 0.0) {
                    similarities.put(v, sim);
                }
            }
        }

        List<Entry<Long, Double>> list = new ArrayList<>(similarities.entrySet());
        Collections.sort(list, new Comparator<Map.Entry<Long, Double>>() {
            @Override
            public int compare(Map.Entry<Long, Double> o1, Map.Entry<Long, Double> o2) {
                return o2.getValue().compareTo(o1.getValue());
            }
        });

        for (Long item : items) {
            double numerator = 0.0;
            double denomoniator = 0.0;
            int i = 0;
            for (Entry<Long, Double> e : list) {
                if (V.get(e.getKey()).containsKey(item)) {
                    if (i >= this.neighborhoodSize) {
                        break;
                    }
                    i++;
                    Double r = V.get(e.getKey()).get(item);
                    numerator += r * e.getValue();
                    denomoniator += e.getValue();
                }
            }
            if (denomoniator == 0.0 || i < 2) {
                results.add(Results.create(item, 0.0));
            } else {
                Double avg = Vectors.mean(userRatings);
                Double score = avg + numerator / denomoniator;
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
     * Get a user's rating vector.
     * 
     * @param user The user ID.
     * @return The rating vector, mapping item IDs to the user's rating
     *         for that item.
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
