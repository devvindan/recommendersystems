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
import java.util.*;

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
     * @param user The user ID.
     * @param items The score vector.  Its key domain is the items to score, and the scores
     *               (rating predictions) should be written back to this vector.
     */
    @Override
    public ResultMap scoreWithDetails(long user, @Nonnull Collection<Long> items) {
        Long2DoubleMap itemMeans = model.getItemMeans();
        Long2DoubleMap ratings = getUserRatingVector(user);

        // TODO Normalize the user's ratings by subtracting the item mean from each one.
        for (long item : ratings.keySet()){
            double rating = ratings.get(item);
            ratings.put(item, rating - itemMeans.get(item));
        }


        Comparator<Result> descendingSimiliarityComporator = new Comparator<Result>() {
            @Override
            public int compare(Result t1, Result t2) {

                return Double.compare(t2.getScore(), t1.getScore());

            }
        };


        List<Result> results = new ArrayList<>();

        for (long item: items ) {
            // TODO Compute the user's score for each item, add it to results
            Long2DoubleMap itemNeighbors = model.getNeighbors(item);
            List<Result> similarities = new ArrayList<>();


            for (long neighborId : itemNeighbors.keySet()){
                double similarity = itemNeighbors.get(neighborId);
                similarities.add(Results.create(neighborId, similarity));
            }

            Collections.sort(similarities, descendingSimiliarityComporator);

            int neighborCounter = 0;

            double numerator = 0;
            double denominator = 0;

            for (Result similarityScore : similarities){
                long item_id = similarityScore.getId();
                double item_score = similarityScore.getScore();

                if (ratings.containsKey(item_id)){
                    neighborCounter += 1;
                    numerator += item_score * ratings.get(item_id);
                    denominator += item_score;
                }

                if (neighborCounter == 20){
                    break;
                }

            }

            double score = itemMeans.get(item) + (numerator / denominator);

            results.add(Results.create(item, score));

        }

        return Results.newResultMap(results);

    }

    /**
     * Get a user's ratings.
     * @param user The user ID.
     * @return The ratings to retrieve.
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
