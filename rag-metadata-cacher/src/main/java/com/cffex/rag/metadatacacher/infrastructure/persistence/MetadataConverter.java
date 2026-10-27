package com.cffex.rag.metadatacacher.infrastructure.persistence;

import com.cffex.rag.common.domain.metadata.KnowledgeBaseMeta;
import com.cffex.rag.common.domain.metadata.ModelType;
import com.cffex.rag.common.domain.metadata.RetrievalMode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 元数据字段转换工具。
 *
 * <p>负责把数据库中的原始字符串编码转换为领域层可直接使用的枚举和值对象字段。
 * 该类仅供持久化适配层内部调用。
 */
final class MetadataConverter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String ELASTICSEARCH_ENGINE_ID = "elasticsearch";

    private MetadataConverter() {
    }

    static ModelType toModelType(String code) {
        if ("rerank".equalsIgnoreCase(code) || "reranking".equalsIgnoreCase(code)) {
            return ModelType.RERANK;
        }
        if ("llm".equalsIgnoreCase(code) || "chat".equalsIgnoreCase(code)
                || "text-generation".equalsIgnoreCase(code)
                || "completion".equalsIgnoreCase(code)) {
            return ModelType.LLM;
        }
        return ModelType.EMBEDDING;
    }

    static RetrievalMode toRetrievalMode(String code) {
        if (code == null || code.isBlank()) {
            return RetrievalMode.SEMANTIC;
        }
        String normalized = code.toLowerCase(java.util.Locale.ROOT);
        if (normalized.contains("hybrid")) {
            return RetrievalMode.HYBRID;
        }
        if (normalized.contains("full_text")
                || normalized.contains("full-text")
                || normalized.contains("full text")
                || normalized.contains("keyword")) {
            return RetrievalMode.FULL_TEXT;
        }
        return RetrievalMode.SEMANTIC;
    }

    /**
     * 把 Dify {@code index_struct.type}（如 milvus、elasticsearch、elasticsearch-zh）映射为检索引擎 ID。
     * 缺省时回退到默认引擎；未识别的类型原样透传，由检索阶段的引擎注册表报出明确错误。
     */
    static String toEngineId(String vectorStoreType) {
        if (vectorStoreType == null || vectorStoreType.isBlank()) {
            return KnowledgeBaseMeta.DEFAULT_VECTOR_ENGINE_ID;
        }
        String normalized = vectorStoreType.trim().toLowerCase(java.util.Locale.ROOT);
        if (normalized.startsWith("elasticsearch")) {
            return ELASTICSEARCH_ENGINE_ID;
        }
        return normalized;
    }

    static String extractUploadFileId(String dataSourceInfo) {
        if (dataSourceInfo == null || dataSourceInfo.isBlank()) {
            return null;
        }
        try {
            JsonNode node = OBJECT_MAPPER.readTree(dataSourceInfo);
            JsonNode uploadFileId = node.get("upload_file_id");
            return uploadFileId == null || uploadFileId.isNull() || uploadFileId.asText().isBlank()
                    ? null
                    : uploadFileId.asText();
        } catch (Exception ex) {
            return null;
        }
    }
}
