package com.example.kafka_es.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@Configuration
public class WebConfig {
    @Value("${cors.allowd-origin:http://localhost:5173}")
    private String allowedOrigin;
    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();

        config.setAllowCredentials(true); // 인증 정보 포함 허용 (예: 쿠키)
        config.addAllowedOrigin(allowedOrigin); // EC2 퍼블릭 IP 주소로 수정
        config.addAllowedHeader("*"); // 모든 헤더 허용
        config.addAllowedMethod("*"); // GET, POST, PUT, DELETE 등 모든 메서드 허용

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config); // 모든 경로에 대해 적용

        return new CorsFilter(source);
    }
}
