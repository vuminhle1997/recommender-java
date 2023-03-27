package org.lenskit.mooc.cbf;

import org.lenskit.data.ratings.Rating;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Build a user profile from all positive ratings.
 */
public class WeightedUserProfileBuilder implements UserProfileBuilder {
    /**
     * The tag model, to get item tag vectors.
     */
    private final TFIDFModel model;

    @Inject
    public WeightedUserProfileBuilder(TFIDFModel m) {
        model = m;
    }

    @Override
    public Map<String, Double> makeUserProfile(@Nonnull List<Rating> ratings) {
        // Create a new vector over tags to accumulate the user profile
        Map<String, Double> profile = new HashMap<>();
        Double sum = 0.0;
        for (Rating r : ratings) {
            sum += r.getValue();
        }

        Double avgSum = sum / ratings.size();
        // TODO Normalize the user's ratings
        // TODO Build the user's weighted profile
        for (Rating r : ratings) {
            Map<String, Double> itemVector = model.getItemVector(r.getItemId());
            for (Map.Entry<String, Double> entry : itemVector.entrySet()) {
                String tag = entry.getKey();
                Double weigtedValue = (r.getValue() - avgSum) * entry.getValue();
                if (profile.containsKey(tag)) {
                    profile.put(tag, profile.get(tag) + weigtedValue);
                } else {
                    profile.put(tag, weigtedValue);
                }
            }
        }

        // The profile is accumulated, return it.
        return profile;
    }
}
