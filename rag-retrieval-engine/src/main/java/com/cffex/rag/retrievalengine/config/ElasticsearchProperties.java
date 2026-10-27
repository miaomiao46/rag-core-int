package com.cffex.rag.retrievalengine.config;

import java.util.Objects;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Elasticsearch 连接配置（绑定 {@code elasticsearch.*}）。
 *
 * <pre>
 * elasticsearch:
 *   uri:      http://127.0.0.1:9200
 *   username: elastic
 *   password: elastic
 * </pre>
 *
 * <p>字段映射默认值与 Dify ElasticSearchVector 建索引时的 mapping 保持一致。
 */
@ConfigurationProperties(prefix = "elasticsearch")
public record ElasticsearchProperties(
        String uri,
        String username,
        String password,
        String contentFieldName,
        String metadataFieldName,
        String embeddingFieldName,
        boolean denseScoreThresholdEnabled
) {

    public ElasticsearchProperties {
        Objects.requireNonNull(uri, "elasticsearch.uri must not be null");
        username = username != null ? username : "";
        password = password != null ? password : "";
        contentFieldName = contentFieldName != null && !contentFieldName.isBlank() ? contentFieldName : "page_content";
        metadataFieldName = metadataFieldName != null && !metadataFieldName.isBlank() ? metadataFieldName : "metadata";
        embeddingFieldName = embeddingFieldName != null && !embeddingFieldName.isBlank() ? embeddingFieldName : "vector";
    }
}
