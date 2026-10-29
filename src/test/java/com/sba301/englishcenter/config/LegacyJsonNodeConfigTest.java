package com.sba301.englishcenter.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sba301.englishcenter.dto.response.CertificatePartResponse;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * JsonNode của Jackson 2 trong DTO phải ra đúng nội dung JSON khi Spring Boot 4 xuất bằng Jackson 3.
 */
class LegacyJsonNodeConfigTest {

    private final JsonMapper mapper = JsonMapper.builder()
            .addModule(new LegacyJsonNodeConfig().legacyJsonNodeModule())
            .build();

    @Test
    void writesJackson2NodeAsItsJson() throws Exception {
        CertificatePartResponse part = CertificatePartResponse.builder()
                .partName("Listening")
                .properties(new ObjectMapper().readTree(
                        "{\"minScore\":0,\"maxScore\":9,\"step\":0.5,\"excludeFromTotal\":false,\"unit\":\"Band \\\"9\\\"\"}"))
                .build();

        String json = mapper.writeValueAsString(part);

        assertThat(json).contains("\"properties\":{\"minScore\":0,\"maxScore\":9,\"step\":0.5,"
                + "\"excludeFromTotal\":false,\"unit\":\"Band \\\"9\\\"\"}");
        assertThat(json).doesNotContain("nodeType", "containerNode");
    }

    @Test
    void writesArrayAndNullNodes() throws Exception {
        CertificatePartResponse empty = CertificatePartResponse.builder()
                .properties(new ObjectMapper().readTree("[]"))
                .build();
        assertThat(mapper.writeValueAsString(empty)).contains("\"properties\":[]");

        CertificatePartResponse missing = CertificatePartResponse.builder().build();
        assertThat(mapper.writeValueAsString(missing)).contains("\"properties\":null");
    }
}
