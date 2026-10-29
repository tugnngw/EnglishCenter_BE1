package com.sba301.englishcenter.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.ser.std.StdSerializer;

/**
 * Spring Boot 4 trả JSON bằng Jackson 3 ({@code tools.jackson}), nhưng một số DTO vẫn khai báo
 * {@link com.fasterxml.jackson.databind.JsonNode} của Jackson 2 (VD {@code CertificatePartResponse.properties},
 * {@code CourseContentResponse.contentData}). Jackson 3 coi kiểu đó là bean thường nên xuất
 * {@code {"array":false,"containerNode":true,...}} thay vì dữ liệu thật.
 * <p>
 * Module này dạy Jackson 3 ghi nguyên nội dung JSON của node Jackson 2. Spring Boot tự đăng ký mọi bean
 * {@link JacksonModule} vào JsonMapper, nên không phải sửa DTO nào.
 */
@Configuration
public class LegacyJsonNodeConfig {

    @Bean
    public JacksonModule legacyJsonNodeModule() {
        return new SimpleModule("legacy-jackson2-json-node")
                .addSerializer(com.fasterxml.jackson.databind.JsonNode.class, new LegacyJsonNodeSerializer());
    }

    static final class LegacyJsonNodeSerializer extends StdSerializer<com.fasterxml.jackson.databind.JsonNode> {

        LegacyJsonNodeSerializer() {
            super(com.fasterxml.jackson.databind.JsonNode.class);
        }

        @Override
        public void serialize(com.fasterxml.jackson.databind.JsonNode value, JsonGenerator gen,
                              SerializationContext context) {
            // toString() của JsonNode Jackson 2 là JSON hợp lệ (đã escape)
            gen.writeRawValue(value.toString());
        }
    }
}
