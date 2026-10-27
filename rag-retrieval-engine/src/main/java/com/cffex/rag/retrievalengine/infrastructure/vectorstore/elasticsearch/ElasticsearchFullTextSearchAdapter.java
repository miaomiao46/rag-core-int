package com.cffex.rag.retrievalengine.infrastructure.vectorstore.elasticsearch;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.cffex.rag.common.exception.RagServiceException;
import com.cffex.rag.retrievalengine.application.debug.RetrievalDebugTraceWriter;
import com.cffex.rag.retrievalengine.domain.FullTextSearchRequest;
import com.cffex.rag.retrievalengine.domain.RetrievalCandidate;
import com.cffex.rag.retrievalengine.domain.port.FullTextSearchPort;

/**
 * 基于 Elasticsearch BM25 的全文检索适配器。
 *
 * <p>中文分词由索引 mapping 决定（elasticsearch-zh 索引检索时使用 ik_smart），这里只发 match 查询。
 * 与 Dify 一致：不应用 score_threshold，BM25 原始分写入 sparseScore。
 */
@Component
public class ElasticsearchFullTextSearchAdapter implements FullTextSearchPort {

    static final String ENGINE_ID = "elasticsearch";

    private final ElasticsearchSearchClient searchClient;
    private final ElasticsearchSearchSupport searchSupport;
    private final ElasticsearchSearchRequestFactory searchRequestFactory;
    private final RetrievalDebugTraceWriter debugTraceWriter;

    public ElasticsearchFullTextSearchAdapter(
            ElasticsearchSearchClient searchClient,
            ElasticsearchSearchSupport searchSupport,
            ElasticsearchSearchRequestFactory searchRequestFactory,
            RetrievalDebugTraceWriter debugTraceWriter
    ) {
        this.searchClient = Objects.requireNonNull(searchClient, "searchClient must not be null");
        this.searchSupport = Objects.requireNonNull(searchSupport, "searchSupport must not be null");
        this.searchRequestFactory = Objects.requireNonNull(searchRequestFactory, "searchRequestFactory must not be null");
        this.debugTraceWriter = Objects.requireNonNull(debugTraceWriter, "debugTraceWriter must not be null");
    }

    @Override
    public String engineId() {
        return ENGINE_ID;
    }

    @Override
    public List<RetrievalCandidate> search(FullTextSearchRequest request) {
        try {
            Map<String, Object> body = searchRequestFactory.buildFullTextSearchBody(
                    request.docIds(),
                    request.candidateK(),
                    request.queryText()
            );
            if (debugTraceWriter.enabled()) {
                debugTraceWriter.record("elasticsearch.sparse.request", Map.of(
                        "knowledgeBaseId", request.knowledgeBaseId(),
                        "engineId", request.engineId(),
                        "request", searchRequestFactory.describeFullTextSearchRequest(
                                request.collectionName(),
                                request.docIds(),
                                request.candidateK(),
                                request.queryText()
                        )
                ));
            }
            List<RetrievalCandidate> candidates = searchSupport.toFullTextCandidates(
                    searchClient.search(request.collectionName(), body),
                    request.knowledgeBaseId()
            );
            if (debugTraceWriter.enabled()) {
                debugTraceWriter.record("elasticsearch.sparse.response", Map.of(
                        "knowledgeBaseId", request.knowledgeBaseId(),
                        "collectionName", request.collectionName(),
                        "rawCount", candidates.size(),
                        "count", candidates.size(),
                        "candidates", debugTraceWriter.candidates(candidates)
                ));
            }
            return candidates;
        } catch (RagServiceException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            if (debugTraceWriter.enabled()) {
                debugTraceWriter.record("elasticsearch.sparse.failed", Map.of(
                        "knowledgeBaseId", request.knowledgeBaseId(),
                        "collectionName", request.collectionName(),
                        "error", Objects.toString(ex.getMessage(), ex.getClass().getSimpleName())
                ));
            }
            throw ElasticsearchSearchSupport.retrievalFailed(
                    "sparse ",
                    request.knowledgeBaseId(),
                    request.collectionName(),
                    ex
            );
        }
    }
}
