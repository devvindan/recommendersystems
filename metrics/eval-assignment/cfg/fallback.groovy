import org.lenskit.api.ItemScorer
import org.lenskit.baseline.BaselineScorer
import org.lenskit.bias.BiasItemScorer
import org.lenskit.bias.BiasModel
import org.lenskit.bias.UserItemBiasModel

import org.lenskit.knn.user.UserUserItemScorer
import org.lenskit.knn.NeighborhoodSize
import org.grouplens.lenskit.vectors.similarity.VectorSimilarity
import org.grouplens.lenskit.vectors.similarity.PearsonCorrelation
import org.lenskit.transform.normalize.MeanCenteringVectorNormalizer
import org.grouplens.lenskit.vectors.similarity.CosineVectorSimilarity
import org.lenskit.transform.normalize.VectorNormalizer



bind (BaselineScorer, ItemScorer) to BiasItemScorer
bind BiasModel to UserItemBiasModel
