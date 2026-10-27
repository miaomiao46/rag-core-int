package com.cffex.rag.retrievalengine.infrastructure.vectorstore.elasticsearch;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import com.cffex.rag.retrievalengine.application.debug.RetrievalDebugTraceWriter;
import com.cffex.rag.retrievalengine.config.ElasticsearchProperties;
import com.cffex.rag.retrievalengine.config.RetrievalHttpClientProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

/** 校验 ES 相关组件能被 Spring 正常装配（构造器选择、配置绑定）。 */
class ElasticsearchBeanWiringTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfiguration.class)
            .withBean(ElasticsearchSearchClient.class)
            .withBean(ElasticsearchSearchRequestFactory.class)
            .withBean(ElasticsearchSearchSupport.class)
            .withBean(ElasticsearchVectorSearchAdapter.class)
            .withBean(ElasticsearchFullTextSearchAdapter.class)
            .withPropertyValues(
                    "elasticsearch.uri=http://127.0.0.1:9200",
                    "elasticsearch.username=elastic",
                    "elasticsearch.password=elastic"
            );

    @Test
    void contextCreatesElasticsearchBeans() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(ElasticsearchSearchClient.class);
            assertThat(context.getBean(ElasticsearchVectorSearchAdapter.class).engineId()).isEqualTo("elasticsearch");
            assertThat(context.getBean(ElasticsearchFullTextSearchAdapter.class).engineId()).isEqualTo("elasticsearch");
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({ElasticsearchProperties.class, RetrievalHttpClientProperties.class})
    static class TestConfiguration {

        @Bean
        RestClient.Builder restClientBuilder() {
            return RestClient.builder();
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        RetrievalDebugTraceWriter retrievalDebugTraceWriter() {
            return mock(RetrievalDebugTraceWriter.class);
        }
    }
}
