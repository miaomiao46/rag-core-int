package com.cffex.rag.retrievalengine.infrastructure.vectorstore.elasticsearch;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.cffex.rag.retrievalengine.application.debug.RetrievalDebugTraceWriter;
import com.cffex.rag.retrievalengine.config.ElasticsearchProperties;
import com.cffex.rag.retrievalengine.domain.FullTextSearchRequest;
import com.cffex.rag.retrievalengine.domain.RetrievalCandidate;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class ElasticsearchFullTextSearchAdapterTest {

    @Mock
    private ElasticsearchSearchClient searchClient;

    @Mock
    private RetrievalDebugTraceWriter debugTraceWriter;

    @Test
    @SuppressWarnings("unchecked")
    void search_buildsBoolMatchWithTermsFilterAndMapsBm25ScoreToSparse() throws Exception {
        when(searchClient.search(eq("Vector_index_kb_1_Node"), anyMap()))
                .thenReturn(ElasticsearchVectorSearchAdapterTest.hits("""
                        [
                          {"_id": "chunk-1", "_score": 12.5, "_source": {"page_content": "保证金比例", "metadata": {"document_id": "doc-1"}}},
                          {"_id": "chunk-2", "_score": 0.3, "_source": {"page_content": "其他", "metadata": {"document_id": "doc-1"}}}
                        ]
                        """));

        List<RetrievalCandidate> candidates = adapter().search(new FullTextSearchRequest(
                "kb-1",
                "elasticsearch",
                "Vector_index_kb_1_Node",
                List.of("doc-1"),
                "保证金",
                10,
                true,
                0.5d
        ));

        ArgumentCaptor<Map<String, Object>> bodyCaptor = ArgumentCaptor.forClass(Map.class);
        verify(searchClient).search(eq("Vector_index_kb_1_Node"), bodyCaptor.capture());
        Map<String, Object> body = bodyCaptor.getValue();
        Map<String, Object> bool = (Map<String, Object>) ((Map<String, Object>) body.get("query")).get("bool");

        assertThat(body).containsEntry("size", 10).doesNotContainKey("knn");
        assertThat(bool)
                .containsEntry("must", Map.of("match", Map.of("page_content", "保证金")))
                .containsEntry("filter", Map.of("terms", Map.of("metadata.document_id", List.of("doc-1"))));

        // 与 Dify 一致：全文检索不应用 score_threshold
        assertThat(candidates)
                .extracting(RetrievalCandidate::chunkId, RetrievalCandidate::finalScore, RetrievalCandidate::sparseScore)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("chunk-1", 0.0d, 12.5d),
                        org.assertj.core.groups.Tuple.tuple("chunk-2", 0.0d, 0.3d)
                );
    }

    private ElasticsearchFullTextSearchAdapter adapter() {
        ElasticsearchProperties properties = new ElasticsearchProperties(
                "http://127.0.0.1:9200", "elastic", "elastic", null, null, null, false);
        return new ElasticsearchFullTextSearchAdapter(
                searchClient,
                new ElasticsearchSearchSupport(properties, new ObjectMapper()),
                new ElasticsearchSearchRequestFactory(properties),
                debugTraceWriter
        );
    }
}
