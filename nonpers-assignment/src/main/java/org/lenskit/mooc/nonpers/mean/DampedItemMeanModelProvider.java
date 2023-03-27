package org.lenskit.mooc.nonpers.mean;

import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import org.lenskit.baseline.MeanDamping;
import org.lenskit.data.dao.DataAccessObject;
import org.lenskit.data.ratings.Rating;
import org.lenskit.inject.Transient;
import org.lenskit.util.io.ObjectStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import javax.inject.Inject;
import javax.inject.Provider;

/**
 * Provider class that builds the mean rating item scorer, computing damped item
 * means from the
 * ratings in the DAO.
 */
public class DampedItemMeanModelProvider implements Provider<ItemMeanModel> {
    /**
     * A logger that you can use to emit debug messages.
     */
    private static final Logger logger = LoggerFactory.getLogger(DampedItemMeanModelProvider.class);

    /**
     * The data access object, to be used when computing the mean ratings.
     */
    private final DataAccessObject dao;
    /**
     * The damping factor.
     */
    private final double damping;

    /**
     * Constructor for the mean item score provider.
     *
     * <p>
     * The {@code @Inject} annotation tells LensKit to use this constructor.
     *
     * @param dao     The data access object (DAO), where the builder will get
     *                ratings. The {@code @Transient}
     *                annotation on this parameter means that the DAO will be used
     *                to build the model, but the
     *                model will <strong>not</strong> retain a reference to the DAO.
     *                This is standard procedure
     *                for LensKit models.
     * @param damping The damping factor for Bayesian damping. This is number of
     *                fake global-mean ratings to
     *                assume. It is provided as a parameter so that it can be
     *                reconfigured. See the file
     *                {@code damped-mean.groovy} for how it is used.
     */
    @Inject
    public DampedItemMeanModelProvider(@Transient DataAccessObject dao,
            @MeanDamping double damping) {
        this.dao = dao;
        this.damping = damping;
    }

    /**
     * Construct an item mean model.
     *
     * <p>
     * The {@link Provider#get()} method constructs whatever object the provider
     * class is intended to build.
     * </p>
     *
     * @return The item mean model with mean ratings for all items.
     */
    @Override
    public ItemMeanModel get() {
        HashMap<Long, ArrayList<Rating>> items = new HashMap<>();
        try (ObjectStream<Rating> ratings = dao.query(Rating.class).stream()) {
            for (Rating r : ratings) {
                long key = r.getItemId();
                if (!items.containsKey(key)) {
                    items.put(key, new ArrayList<Rating>());
                } else {
                    ArrayList<Rating> array = items.get(key);
                    array.add(r);
                    items.put(key, array);
                }
            }
        }

        // Compute global mean
        double globalMean = 0f;
        int size = 0;
        for (long key : items.keySet()) {
            List<Rating> ratings = items.get(key);
            for (Rating r : ratings) {
                globalMean += r.getValue();
                size++;
            }
        }
        globalMean = globalMean / size;

        // Compute damped means for every item
        Long2DoubleOpenHashMap means = new Long2DoubleOpenHashMap();
        for (long key : items.keySet()) {
            List<Rating> ratings = items.get(key);
            double sum = 0f;
            for (Rating r : ratings) {
                sum += r.getValue();
            }
            sum = (sum + (damping * globalMean)) / (ratings.size() + damping);
            means.put(key, sum);
        }

        logger.info("computed damped mean ratings for {} items", means.size());
        return new ItemMeanModel(means);
    }
}
