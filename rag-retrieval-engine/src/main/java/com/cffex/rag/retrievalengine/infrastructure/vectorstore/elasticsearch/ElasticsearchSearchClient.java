package com.cffex.rag.retrievalengine.infrastructure.vectorstore.elasticsearch;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.cffex.rag.retrievalengine.config.ElasticsearchProperties;
import com.cffex.rag.retrievalengine.config.RetrievalHttpClientProperties;
import com.cffex.rag.retrievalengine.infrastructure.http.PoolingRestClientFactory;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Elasticsearch {@code _search} REST 调用封装。
 *
 * <p>直接走 REST 而不引入 elasticsearch-java：Spring Boot 管理的客户端版本高于服务端 8.14，
 * 且请求体用 Map 构造后可以原样写入调试 trace，便于与 Dify 请求逐字段对比。
 */
@Component
public class ElasticsearchSearchClient {

    private final RestClient restClient;

    public ElasticsearchSearchClient(
            RestClient.Builder restClientBuilder,
            RetrievalHttpClientProperties httpClientProperties,
            ElasticsearchProperties properties
    ) {
        this(PoolingRestClientFactory.create(
                restClientBuilder.clone()
                        .baseUrl(properties.uri())
                        .defaultHeaders(headers -> applyBasicAuth(headers, properties)),
                httpClientProperties
        ));
    }

    ElasticsearchSearchClient(RestClient restClient) {
        this.restClient = Objects.requireNonNull(restClient, "restClient must not be null");
    }

    /**
     * 对单个索引执行检索。索引名按 Dify 规则统一转小写（class_prefix 为 {@code Vector_index_xxx_Node}）。
     */
    public JsonNode search(String indexName, Map<String, Object> body) {
        return restClient.post()
                .uri("/{index}/_search", toIndexName(indexName))
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
    }

    public static String toIndexName(String collectionName) {
        return collectionName.toLowerCase(Locale.ROOT);
    }

    static void applyBasicAuth(HttpHeaders headers, ElasticsearchProperties properties) {
        if (properties.username().isBlank()) {
            return;
        }
        String credentials = properties.username() + ":" + properties.password();
        headers.set(
                HttpHeaders.AUTHORIZATION,
                "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8))
        );
    }
}
