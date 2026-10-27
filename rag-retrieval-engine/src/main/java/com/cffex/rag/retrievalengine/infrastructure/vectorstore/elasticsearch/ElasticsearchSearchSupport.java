package com.cffex.rag.retrievalengine.infrastructure.vectorstore.elasticsearch;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.cffex.rag.common.exception.RagErrorCode;
import com.cffex.rag.common.exception.RagServiceException;
import com.cffex.rag.retrievalengine.config.ElasticsearchProperties;
import com.cffex.rag.retrievalengine.domain.RetrievalCandidate;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Elasticsearch 检索共享支持：响应映射与异常封装。
 *
 * <p>候选的 metadata 结构与 Milvus 适配器保持一致：顶层为 {@code page_content}/{@code metadata}，
 * 再补一个 {@code knowledge_base_id}，下游无需区分引擎。
 */
@Component
public class ElasticsearchSearchSupport {

    private static final String DOCUMENT_ID_METADATA_KEY = "document_id";
    private static final String KNOWLEDGE_BASE_ID_METADATA_KEY = "knowledge_base_id";
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ElasticsearchProperties properties;
    private final ObjectMapper objectMapper;

    public ElasticsearchSearchSupport(ElasticsearchProperties properties, ObjectMapper objectMapper) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    public List<RetrievalCandidate> toDenseCandidates(JsonNode response, String knowledgeBaseId) {
        return toRetrievalCandidates(response, knowledgeBaseId, true);
    }

    public List<RetrievalCandidate> toFullTextCandidates(JsonNode response, String knowledgeBaseId) {
        return toRetrievalCandidates(response, knowledgeBaseId, false);
    }

    public static RagServiceException retrievalFailed(
            String routeLabel,
            String knowledgeBaseId,
            String collectionName,
            RuntimeException cause
    ) {
        return new RagServiceException(
                RagErrorCode.RETRIEVAL_FAILED,
                "Elasticsearch %s检索失败，knowledgeBaseId=%s，index=%s，原因=%s"
                        .formatted(
                                routeLabel,
                                knowledgeBaseId,
                                ElasticsearchSearchClient.toIndexName(collectionName),
                                summarize(cause)
                        ),
                cause
        );
    }

    public static String summarize(Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.isBlank() ? throwable.getClass().getSimpleName() : message;
    }

    private List<RetrievalCandidate> toRetrievalCandidates(
            JsonNode response,
            String knowledgeBaseId,
            boolean dense
    ) {
        JsonNode hits = response == null ? null : response.path("hits").path("hits");
        if (hits == null || !hits.isArray()) {
            return List.of();
        }
        List<RetrievalCandidate> candidates = new ArrayList<>(hits.size());
        for (JsonNode hit : hits) {
            candidates.add(toCandidate(hit, knowledgeBaseId, dense));
        }
        return List.copyOf(candidates);
    }

    private RetrievalCandidate toCandidate(JsonNode hit, String knowledgeBaseId, boolean dense) {
        Map<String, Object> entity = new LinkedHashMap<>();
        JsonNode source = hit.path("_source");
        if (source.isObject()) {
            entity.putAll(objectMapper.convertValue(source, MAP_TYPE));
        }
        entity.putIfAbsent(KNOWLEDGE_BASE_ID_METADATA_KEY, knowledgeBaseId);
        double score = hit.path("_score").asDouble(0.0d);
        return new RetrievalCandidate(
                hit.path("_id").asText(""),
                extractDocumentId(entity.get(properties.metadataFieldName())),
                knowledgeBaseId,
                Objects.toString(entity.get(properties.contentFieldName()), ""),
                dense ? score : 0.0d,
                dense ? null : score,
                entity
        );
    }

    private static String extractDocumentId(Object metadata) {
        if (metadata instanceof Map<?, ?> map) {
            Object documentId = map.get(DOCUMENT_ID_METADATA_KEY);
            return documentId == null ? "" : documentId.toString();
        }
        return "";
    }
}
