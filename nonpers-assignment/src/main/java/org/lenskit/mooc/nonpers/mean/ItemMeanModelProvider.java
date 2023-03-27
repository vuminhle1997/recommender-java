package org.lenskit.mooc.nonpers.mean;

import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import org.lenskit.data.dao.DataAccessObject;
import org.lenskit.data.ratings.Rating;
import org.lenskit.inject.Transient;
import org.lenskit.util.io.ObjectStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;

import javax.inject.Inject;
import javax.inject.Provider;

/**
 * Provider class that builds the mean rating item scorer, computing item means
 * from the
 * ratings in the DAO.
 */
public class ItemMeanModelProvider implements Provider<ItemMeanModel> {
    /**
     * A logger that you can use to emit debug messages.
     */
    private static final Logger logger = LoggerFactory.getLogger(ItemMeanModelProvider.class);

    /**
     * The data access object, to be used when computing the mean ratings.
     */
    private final DataAccessObject dao;

    /**
     * Constructor for the mean item score provider.
     *
     * <p>
     * The {@code @Inject} annotation tells LensKit to use this constructor.
     *
     * @param dao The data access object (DAO), where the builder will get ratings.
     *            The {@code @Transient}
     *            annotation on this parameter means that the DAO will be used to
     *            build the model, but the
     *            model will <strong>not</strong> retain a reference to the DAO.
     *            This is standard procedure
     *            for LensKit models.
     */
    @Inject
    public ItemMeanModelProvider(@Transient DataAccessObject dao) {
        this.dao = dao;
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
                if (!items.containsKey(r.getItemId())) {
                    ArrayList<Rating> emptyList = new ArrayList<>();
                    items.put(r.getItemId(), emptyList);
                } else {
                    long key = r.getItemId();
                    ArrayList<Rating> list = items.get(key);
                    list.add(r);
                    items.put(key, list);
                }
            }
        }

        Long2DoubleOpenHashMap means = new Long2DoubleOpenHashMap();
        for (long key : items.keySet()) {
            double sum = 0;
            for (Rating r : items.get(key)) {
                sum += r.getValue();
            }
            sum = sum / items.get(key).size();
            means.put(key, sum);
        }

        logger.info("computed mean ratings for {} items", means.size());
        return new ItemMeanModel(means);
    }
}
