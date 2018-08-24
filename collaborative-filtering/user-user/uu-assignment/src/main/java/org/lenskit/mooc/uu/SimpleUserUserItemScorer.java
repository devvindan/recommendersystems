package org.lenskit.mooc.uu;

import com.google.common.collect.Maps;
import it.unimi.dsi.fastutil.longs.Long2DoubleMap;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2DoubleSortedMap;
import it.unimi.dsi.fastutil.longs.LongSet;
import org.lenskit.api.Result;
import org.lenskit.api.ResultMap;
import org.lenskit.basic.AbstractItemScorer;
import org.lenskit.data.dao.DataAccessObject;
import org.lenskit.data.dao.UserEventDAO;
import org.lenskit.data.entities.CommonAttributes;
import org.lenskit.data.entities.EntityType;
import org.lenskit.data.ratings.Rating;
import org.lenskit.results.Results;
import org.lenskit.util.ScoredIdAccumulator;
import org.lenskit.util.TopNScoredIdAccumulator;
import org.lenskit.util.collections.LongUtils;
import org.lenskit.util.math.Scalars;
import org.lenskit.util.math.Vectors;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.util.*;

/**
 * User-user item scorer.
 * @author <a href="http://www.grouplens.org">GroupLens Research</a>
 */
public class SimpleUserUserItemScorer extends AbstractItemScorer {
    private final DataAccessObject dao;
    private final int neighborhoodSize;

    /**
     * Instantiate a new user-user item scorer.
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
        // TODO Score the items for the user with user-user CF

        List<Result> results = new ArrayList<>();


        // getting list of user id's
        LongSet userIds = dao.getEntityIds(EntityType.forName("user"));

        Long2DoubleOpenHashMap mainVector = getUserRatingVector(user);
        double mainMean = Vectors.mean(mainVector);

        // subtract mean from each rating - normalizing
        for (long key: mainVector.keySet()){
            mainVector.put(key, mainVector.get(key) - mainMean);
        }

        List<Result> similiarities = new ArrayList<>();


        for (long user_id : userIds){

            if (user_id == user){
                continue;
            }

            Long2DoubleOpenHashMap userVector = getUserRatingVector(user_id);
            double userMean = Vectors.mean(userVector);

            // subtract mean from each rating
            for (long key: userVector.keySet()){
                userVector.put(key, userVector.get(key) - userMean);
            }

            double cosineDistance = Vectors.dotProduct(mainVector, userVector) / (Vectors.euclideanNorm(mainVector) * Vectors.euclideanNorm(userVector));
            similiarities.add(Results.create(user_id, cosineDistance));

        }


        // defining a comparator to sort similiarities
        Comparator<Result> descendingSimiliarityComporator = new Comparator<Result>() {
            @Override
            public int compare(Result t1, Result t2) {

                return Double.compare(t2.getScore(), t1.getScore());

            }
        };

        // sort similiarities
        Collections.sort(similiarities, descendingSimiliarityComporator);


        // iterate over each item and compute n-nearest-neighbors weighted average prediction
        for (long item : items){

            // first, find 2 <= n <= 30 neighbors with positive similiarities to the target user
            int neighborCounter = 0;

            double weightedDeviationsSum = 0;
            double weightsSum = 0;

            // iterate over each user
            for (Result similiarityScore : similiarities){

                long user_id = similiarityScore.getId();
                double user_similiarity_score = similiarityScore.getScore();

                Long2DoubleOpenHashMap userVector = getUserRatingVector(user_id);

                // count only users who rated the item and has positive similiarity score with the target user
                if (userVector.containsKey(item) && user_similiarity_score > 0){
                    neighborCounter += 1;
                    double user_mean = Vectors.mean(userVector);
                    weightedDeviationsSum += user_similiarity_score * (userVector.get(item) - user_mean);
                    weightsSum += user_similiarity_score;

                    // use max 30 neighbors
                    if (neighborCounter == 30){
                        break;
                    }

                }

            }

            // refuse to score items if there are less then 2 neighbors scored it
            if (neighborCounter < 2){
                continue;
            }

            double item_score = mainMean + (weightedDeviationsSum / weightsSum);
            results.add(Results.create(item, item_score));

        }


        return Results.newResultMap(results);

    }

    /**
     * Get a user's rating vector.
     * @param user The user ID.
     * @return The rating vector, mapping item IDs to the user's rating
     *         for that item.
     */
    private Long2DoubleOpenHashMap getUserRatingVector(long user) {
        List<Rating> history = dao.query(Rating.class)
                                  .withAttribute(CommonAttributes.USER_ID, user)
                                  .get();

        Long2DoubleOpenHashMap ratings = new Long2DoubleOpenHashMap();
        for (Rating r: history) {
            ratings.put(r.getItemId(), r.getValue());
        }

        return ratings;
    }

}
