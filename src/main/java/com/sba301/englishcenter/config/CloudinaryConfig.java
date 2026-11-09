package com.sba301.englishcenter.config;

import com.cloudinary.Cloudinary;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.LinkedHashMap;
import java.util.Map;


@Configuration
@Slf4j
public class CloudinaryConfig {

   
    @Bean
    public Cloudinary cloudinary(
            @Value("${cloudinary.cloud-name:}") String cloudName,
            @Value("${cloudinary.api-key:}") String apiKey,
            @Value("${cloudinary.api-secret:}") String apiSecret) {

        Map<String, Object> config = new LinkedHashMap<>();
        config.put("cloud_name", cloudName);
        config.put("api_key", apiKey);
        config.put("api_secret", apiSecret);
        config.put("secure", Boolean.TRUE);

        try {
            return new Cloudinary(config);
        } catch (Throwable ex) {
            // Không có HTTP adapter trên classpath (thiếu cloudinary-http44)
            // → tắt việc dò strategy. URL có thời hạn vẫn dựng được bình thường.
            log.warn("Không tìm thấy Cloudinary HTTP adapter ({}). "
                    + "Chuyển sang chế độ load_strategies=false — vẫn dựng URL xem file, "
                    + "nhưng không gọi được API upload/destroy của SDK.", ex.getMessage());
            config.put("load_strategies", "false");
            return new Cloudinary(config);
        }
    }
}
