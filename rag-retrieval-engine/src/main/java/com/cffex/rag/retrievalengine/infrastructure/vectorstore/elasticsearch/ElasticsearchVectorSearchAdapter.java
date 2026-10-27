package com.cffex.rag.retrievalengine.infrastructure.vectorstore.elasticsearch;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.cffex.rag.common.exception.RagServiceException;
import com.cffex.rag.retrievalengine.application.debug.RetrievalDebugTraceWriter;
import com.cffex.rag.retrievalengine.config.ElasticsearchProperties;
import com.cffex.rag.retrievalengine.domain.DenseVectorSearchRequest;
import com.cffex.rag.retrievalengine.domain.RetrievalCandidate;
import com.cffex.rag.retrievalengine.domain.port.VectorSearchPort;

/**
 * 基于 Elasticsearch knn 的 dense 向量检索适配器。
 *
 * <p>分数为 ES cosine 的 {@code _score = (1 + cos) / 2}。Dify 的 ES 实现不会按 score_threshold 过滤
 * dense 结果（阈值只决定是否写入 metadata.score），默认与其保持一致；
 * 需要真正过滤时打开 {@code elasticsearch.dense-score-threshold-enabled}。
 */
@Component
public class ElasticsearchVectorSearchAdapter implements VectorSearchPort {

    static final String ENGINE_ID = "elasticsearch";

    private final ElasticsearchSearchClient searchClient;
    private final ElasticsearchSearchSupport searchSupport;
    private final ElasticsearchSearchRequestFactory searchRequestFactory;
    private final RetrievalDebugTraceWriter debugTraceWriter;
    private final boolean scoreThresholdFilterEnabled;

    public ElasticsearchVectorSearchAdapter(
            ElasticsearchSearchClient searchClient,
            ElasticsearchSearchSupport searchSupport,
            ElasticsearchSearchRequestFactory searchRequestFactory,
            RetrievalDebugTraceWriter debugTraceWriter,
            ElasticsearchProperties properties
    ) {
        this.searchClient = Objects.requireNonNull(searchClient);
        this.searchSupport = Objects.requireNonNull(searchSupport);
        this.searchRequestFactory = Objects.requireNonNull(searchRequestFactory);
        this.debugTraceWriter = Objects.requireNonNull(debugTraceWriter);
        this.scoreThresholdFilterEnabled = properties.denseScoreThresholdEnabled();
    }

    @Override
    public String engineId() {
        return ENGINE_ID;
    }

    @Override
    public List<RetrievalCandidate> search(DenseVectorSearchRequest request) {
        try {
            float[] queryVector = request.queryVector();
            Map<String, Object> body = searchRequestFactory.buildDenseSearchBody(
                    request.docIds(),
                    request.candidateK(),
                    queryVector
            );
            if (debugTraceWriter.enabled()) {
                debugTraceWriter.record("elasticsearch.dense.request", Map.of(
                        "knowledgeBaseId", request.knowledgeBaseId(),
                        "engineId", request.engineId(),
                        "request", searchRequestFactory.describeDenseSearchRequest(
                                request.collectionName(),
                                request.docIds(),
                                request.candidateK(),
                                queryVector
                        ),
                        "queryVector", debugTraceWriter.embeddingSummary(queryVector)
                ));
            }
            List<RetrievalCandidate> candidates = searchSupport.toDenseCandidates(
                    searchClient.search(request.collectionName(), body),
                    request.knowledgeBaseId()
            );
            boolean filterApplied = scoreThresholdFilterEnabled && request.scoreThresholdEnabled();
            List<RetrievalCandidate> filteredCandidates = filterApplied
                    ? candidates.stream().filter(candidate -> candidate.finalScore() > request.scoreThreshold()).toList()
                    : candidates;
            if (debugTraceWriter.enabled()) {
                debugTraceWriter.record("elasticsearch.dense.response", Map.of(
                        "knowledgeBaseId", request.knowledgeBaseId(),
                        "collectionName", request.collectionName(),
                        "scoreThresholdEnabled", request.scoreThresholdEnabled(),
                        "scoreThresholdApplied", filterApplied,
                        "scoreThreshold", request.scoreThreshold(),
                        "rawCount", candidates.size(),
                        "count", filteredCandidates.size(),
                        "candidates", debugTraceWriter.candidates(filteredCandidates)
                ));
            }
            return filteredCandidates;
        } catch (RagServiceException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            if (debugTraceWriter.enabled()) {
                debugTraceWriter.record("elasticsearch.dense.failed", Map.of(
                        "knowledgeBaseId", request.knowledgeBaseId(),
                        "collectionName", request.collectionName(),
                        "error", Objects.toString(ex.getMessage(), ex.getClass().getSimpleName())
                ));
            }
            throw ElasticsearchSearchSupport.retrievalFailed(
                    "dense ",
                    request.knowledgeBaseId(),
                    request.collectionName(),
                    ex
            );
        }
    }
}
