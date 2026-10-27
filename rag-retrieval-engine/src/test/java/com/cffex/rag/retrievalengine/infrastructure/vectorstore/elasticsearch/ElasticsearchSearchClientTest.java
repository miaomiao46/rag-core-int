package com.cffex.rag.retrievalengine.infrastructure.vectorstore.elasticsearch;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.cffex.rag.retrievalengine.config.ElasticsearchProperties;
import com.fasterxml.jackson.databind.JsonNode;

class ElasticsearchSearchClientTest {

    @Test
    void search_postsToLowercaseIndexWithBasicAuth() {
        ElasticsearchProperties properties = new ElasticsearchProperties(
                "http://es:9200", "elastic", "elastic", null, null, null, false);
        RestClient.Builder builder = RestClient.builder()
                .baseUrl(properties.uri())
                .defaultHeaders(headers -> ElasticsearchSearchClient.applyBasicAuth(headers, properties));
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://es:9200/vector_index_kb_1_node/_search"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Basic ZWxhc3RpYzplbGFzdGlj"))
                .andExpect(content().json("{\"size\": 3}"))
                .andRespond(withSuccess("{\"hits\": {\"hits\": []}}", MediaType.APPLICATION_JSON));

        JsonNode response = new ElasticsearchSearchClient(builder.build())
                .search("Vector_index_kb_1_Node", Map.of("size", 3));

        server.verify();
        assertThat(response.path("hits").path("hits").isArray()).isTrue();
    }

    @Test
    void applyBasicAuth_skipsHeaderWhenUsernameBlank() {
        HttpHeaders headers = new HttpHeaders();
        ElasticsearchSearchClient.applyBasicAuth(
                headers,
                new ElasticsearchProperties("http://es:9200", null, null, null, null, null, false)
        );

        assertThat(headers.containsKey(HttpHeaders.AUTHORIZATION)).isFalse();
    }
}
