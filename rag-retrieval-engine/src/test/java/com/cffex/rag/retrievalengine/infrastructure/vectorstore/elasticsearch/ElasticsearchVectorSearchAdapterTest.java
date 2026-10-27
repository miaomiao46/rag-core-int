package com.cffex.rag.retrievalengine.infrastructure.vectorstore.elasticsearch;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.cffex.rag.common.exception.RagServiceException;
import com.cffex.rag.retrievalengine.application.debug.RetrievalDebugTraceWriter;
import com.cffex.rag.retrievalengine.config.ElasticsearchProperties;
import com.cffex.rag.retrievalengine.domain.DenseVectorSearchRequest;
import com.cffex.rag.retrievalengine.domain.RetrievalCandidate;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class ElasticsearchVectorSearchAdapterTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Mock
    private ElasticsearchSearchClient searchClient;

    @Mock
    private RetrievalDebugTraceWriter debugTraceWriter;

    @Test
    @SuppressWarnings("unchecked")
    void search_buildsDifyAlignedKnnRequestAndMapsHits() throws Exception {
        when(searchClient.search(eq("Vector_index_kb_1_Node"), anyMap())).thenReturn(hits("""
                [
                  {"_id": "chunk-1", "_score": 0.91,
                   "_source": {"page_content": "content-1", "metadata": {"document_id": "doc-1", "doc_id": "chunk-1"}}}
                ]
                """));

        List<RetrievalCandidate> candidates = adapter(false).search(new DenseVectorSearchRequest(
                "kb-1",
                "elasticsearch",
                "Vector_index_kb_1_Node",
                List.of("doc-1", "doc-2"),
                new float[]{0.1f, 0.2f},
                5,
                false,
                0.0d
        ));

        ArgumentCaptor<Map<String, Object>> bodyCaptor = ArgumentCaptor.forClass(Map.class);
        verify(searchClient).search(eq("Vector_index_kb_1_Node"), bodyCaptor.capture());
        Map<String, Object> body = bodyCaptor.getValue();
        Map<String, Object> knn = (Map<String, Object>) body.get("knn");

        assertThat(body).containsEntry("size", 5)
                .containsEntry("_source", List.of("page_content", "metadata"));
        assertThat(knn).containsEntry("field", "vector")
                .containsEntry("query_vector", List.of(0.1f, 0.2f))
                .containsEntry("k", 5)
                .containsEntry("num_candidates", 8)
                .containsEntry("filter", Map.of("terms", Map.of("metadata.document_id", List.of("doc-1", "doc-2"))));

        assertThat(candidates)
                .extracting(RetrievalCandidate::chunkId, RetrievalCandidate::documentId, RetrievalCandidate::content,
                        RetrievalCandidate::finalScore, RetrievalCandidate::sparseScore)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("chunk-1", "doc-1", "content-1", 0.91d, null));
        assertThat(candidates.getFirst().metadata())
                .containsEntry("knowledge_base_id", "kb-1")
                .containsEntry("metadata", Map.of("document_id", "doc-1", "doc_id", "chunk-1"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void search_omitsKnnFilterWhenNoDocIds() throws Exception {
        when(searchClient.search(any(), anyMap())).thenReturn(hits("[]"));

        adapter(false).search(new DenseVectorSearchRequest("kb-1", "elasticsearch", "idx", List.of(),
                new float[]{0.1f}, 4, false, 0.0d));

        ArgumentCaptor<Map<String, Object>> bodyCaptor = ArgumentCaptor.forClass(Map.class);
        verify(searchClient).search(eq("idx"), bodyCaptor.capture());
        assertThat((Map<String, Object>) bodyCaptor.getValue().get("knn"))
                .doesNotContainKey("filter")
                .containsEntry("num_candidates", 6);
    }

    @Test
    void search_keepsResultsBelowThresholdLikeDifyByDefault() throws Exception {
        when(searchClient.search(any(), anyMap())).thenReturn(twoScoredHits());

        List<RetrievalCandidate> candidates = adapter(false).search(thresholdRequest());

        assertThat(candidates).extracting(RetrievalCandidate::chunkId).containsExactly("chunk-high", "chunk-low");
    }

    @Test
    void search_filtersByThresholdWhenEnabledInProperties() throws Exception {
        when(searchClient.search(any(), anyMap())).thenReturn(twoScoredHits());

        List<RetrievalCandidate> candidates = adapter(true).search(thresholdRequest());

        assertThat(candidates).extracting(RetrievalCandidate::chunkId).containsExactly("chunk-high");
    }

    @Test
    void search_wrapsClientFailureWithLowercaseIndexName() {
        when(searchClient.search(any(), anyMap())).thenThrow(new IllegalStateException("connection refused"));

        assertThatThrownBy(() -> adapter(false).search(new DenseVectorSearchRequest("kb-1", "elasticsearch",
                "Vector_index_kb_1_Node", List.of(), new float[]{0.1f}, 4, false, 0.0d)))
                .isInstanceOf(RagServiceException.class)
                .hasMessageContaining("index=vector_index_kb_1_node")
                .hasMessageContaining("connection refused");
    }

    private ElasticsearchVectorSearchAdapter adapter(boolean denseScoreThresholdEnabled) {
        ElasticsearchProperties properties = new ElasticsearchProperties(
                "http://127.0.0.1:9200", "elastic", "elastic", null, null, null, denseScoreThresholdEnabled);
        return new ElasticsearchVectorSearchAdapter(
                searchClient,
                new ElasticsearchSearchSupport(properties, OBJECT_MAPPER),
                new ElasticsearchSearchRequestFactory(properties),
                debugTraceWriter,
                properties
        );
    }

    private static DenseVectorSearchRequest thresholdRequest() {
        return new DenseVectorSearchRequest("kb-1", "elasticsearch", "idx", List.of(),
                new float[]{0.1f}, 5, true, 0.7d);
    }

    private static JsonNode twoScoredHits() throws Exception {
        return hits("""
                [
                  {"_id": "chunk-high", "_score": 0.8, "_source": {"page_content": "high", "metadata": {"document_id": "d1"}}},
                  {"_id": "chunk-low", "_score": 0.6, "_source": {"page_content": "low", "metadata": {"document_id": "d2"}}}
                ]
                """);
    }

    static JsonNode hits(String hitsArray) throws Exception {
        return OBJECT_MAPPER.readTree("{\"hits\": {\"hits\": " + hitsArray + "}}");
    }
}
