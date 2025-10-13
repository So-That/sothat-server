package com.example.kafka_es.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class GptClientService {

    private final WebClient gptClient;

    @Value("${gpt.path:/comments/summary/}")
    private String path;

    @Value("${gpt.timeout-seconds:30}")
    private long timeoutSeconds;

    private static final ParameterizedTypeReference<Map<String,Object>> MAP_TYPE =
            new ParameterizedTypeReference<>() {};

    public Map<String, Object> requestGptSummary(Object previewPayload) {
        String p = normalize(path);
        try {
            return gptClient.post()
                    .uri(p)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(previewPayload) // 이미 snake_case Map 전달
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, resp ->
                            resp.bodyToMono(String.class).defaultIfEmpty("")
                                    .map(msg -> new ResponseStatusException(resp.statusCode(), msg)))
                    .bodyToMono(MAP_TYPE)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .block();
        } catch (Exception e) {
            log.error("❌ GPT 서버 호출 실패: {}", e.toString());
            return Map.of("error","gpt_call_failed","message", e.getMessage()==null?"":e.getMessage());
        }
    }

    private String normalize(String raw) {
        String s = (raw==null || raw.isBlank()) ? "/comments/summary/" : raw.trim();
        if (!s.startsWith("/")) s = "/" + s;
        if (!s.endsWith("/")) s = s + "/";
        return s;
    }
}
