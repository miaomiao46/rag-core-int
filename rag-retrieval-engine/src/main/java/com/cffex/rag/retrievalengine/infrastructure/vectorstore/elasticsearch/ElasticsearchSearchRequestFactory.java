package com.cffex.rag.retrievalengine.infrastructure.vectorstore.elasticsearch;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.cffex.rag.retrievalengine.config.ElasticsearchProperties;

/**
 * Elasticsearch 查询请求工厂：统一构造 dense/full-text 两条路由的 {@code _search} 请求体。
 *
 * <p>请求结构与 Dify ElasticSearchVector 对齐：
 * dense 走 knn（k=size=topK，num_candidates=ceil(topK*1.5)，文档过滤放在 knn.filter 前置过滤）；
 * full-text 走 bool.must.match(page_content) + bool.filter.terms。
 */
@Component
public class ElasticsearchSearchRequestFactory {

    private static final double DIFY_NUM_CANDIDATES_FACTOR = 1.5d;

    private final ElasticsearchProperties properties;

    public ElasticsearchSearchRequestFactory(ElasticsearchProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    public Map<String, Object> buildDenseSearchBody(List<String> docIds, int topK, float[] queryVector) {
        Map<String, Object> knn = new LinkedHashMap<>();
        knn.put("field", properties.embeddingFieldName());
        knn.put("query_vector", toList(queryVector));
        knn.put("k", topK);
        knn.put("num_candidates", numCandidates(topK));
        if (!docIds.isEmpty()) {
            knn.put("filter", docIdFilter(docIds));
        }
        Map<String, Object> body = baseBody(topK);
        body.put("knn", knn);
        return body;
    }

    public Map<String, Object> buildFullTextSearchBody(List<String> docIds, int topK, String queryText) {
        Map<String, Object> bool = new LinkedHashMap<>();
        bool.put("must", Map.of("match", Map.of(properties.contentFieldName(), queryText)));
        if (!docIds.isEmpty()) {
            bool.put("filter", docIdFilter(docIds));
        }
        Map<String, Object> body = baseBody(topK);
        body.put("query", Map.of("bool", bool));
        return body;
    }

    public Map<String, Object> describeDenseSearchRequest(
            String collectionName,
            List<String> docIds,
            int topK,
            float[] queryVector
    ) {
        Map<String, Object> description = baseDescription(collectionName, docIds, topK, "knn", "cosine");
        description.put("numCandidates", numCandidates(topK));
        description.put("queryVectorDim", queryVector.length);
        return description;
    }

    public Map<String, Object> describeFullTextSearchRequest(
            String collectionName,
            List<String> docIds,
            int topK,
            String queryText
    ) {
        Map<String, Object> description = baseDescription(collectionName, docIds, topK, "match", "BM25");
        description.put("queryText", queryText);
        description.put("queryLength", queryText.length());
        return description;
    }

    public List<String> sourceFields() {
        // 不回传 vector 字段，避免每条命中都带上上千维的向量。
        return List.of(properties.contentFieldName(), properties.metadataFieldName());
    }

    static int numCandidates(int topK) {
        return (int) Math.ceil(topK * DIFY_NUM_CANDIDATES_FACTOR);
    }

    private Map<String, Object> baseBody(int topK) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("size", topK);
        body.put("_source", sourceFields());
        return body;
    }

    private Map<String, Object> docIdFilter(List<String> docIds) {
        return Map.of("terms", Map.of(properties.metadataFieldName() + ".document_id", List.copyOf(docIds)));
    }

    private Map<String, Object> baseDescription(
            String collectionName,
            List<String> docIds,
            int topK,
            String queryType,
            String metricType
    ) {
        Map<String, Object> description = new LinkedHashMap<>();
        description.put("collectionName", collectionName);
        description.put("indexName", ElasticsearchSearchClient.toIndexName(collectionName));
        description.put("topK", topK);
        description.put("docFilterCount", docIds.size());
        description.put("docIds", docIds);
        description.put("filter", docIds.isEmpty() ? null : docIdFilter(docIds));
        description.put("outputFields", sourceFields());
        description.put("queryType", queryType);
        description.put("metricType", metricType);
        return description;
    }

    private static List<Float> toList(float[] vector) {
        List<Float> values = new java.util.ArrayList<>(vector.length);
        for (float value : vector) {
            values.add(value);
        }
        return values;
    }
}
