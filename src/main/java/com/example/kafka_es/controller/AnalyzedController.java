package com.example.kafka_es.controller;

import com.example.kafka_es.dto.AnalyzeRequest;
import com.example.kafka_es.dto.AnalyzedCommentResponse;
import com.example.kafka_es.service.KafkaConsumerService;

import lombok.RequiredArgsConstructor;

import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/comments")
@RequiredArgsConstructor
public class AnalyzedController {

    private final KafkaConsumerService consumerService;
    private final WebClient gptClient; // GptClientConfig에서 baseUrl/timeout 설정

    @PostMapping("/summary")
    public ResponseEntity<Map<String, Object>> previewThenSummarize(@RequestBody AnalyzeRequest request) {
        // 1) 미리보기 생성 (DB 병합 포함)
        AnalyzedCommentResponse preview =
                consumerService.summarizeWithMergeIfNeeded(request.getUrls(), request.getKeyword());

        if (preview == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("error", "미리보기(preview) 생성 실패"));
        }

        try {
            // 2) GPT 서버로 POST
            Map<String, Object> resultBody = gptClient.post()
                    .uri("/comments/summary/") // baseUrl은 GptClientConfig에서 주입
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(preview)
                    .retrieve()
                    .onStatus(
                            HttpStatusCode::isError,
                            resp -> resp.bodyToMono(String.class).defaultIfEmpty("")
                                    .map(msg -> new ResponseStatusException(resp.statusCode(), msg))
                    )
                    .bodyToMono(new ParameterizedTypeReference<Map<String, Object>>() {})
                    .block(); // 컨트롤러에서 블로킹 허용(타임아웃은 커넥터에서 설정)

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("preview", preview);
            body.put("gpt_reviews", resultBody != null ? resultBody : Map.of());
            body.put("gptStatus", 200);
            return ResponseEntity.ok(body);

        } catch (ResponseStatusException ex) {
            // HTTP 에러 응답(4xx/5xx)
            Map<String, Object> errorBody = new LinkedHashMap<>();
            errorBody.put("error", "GPT 서버 요청 실패");
            if (ex.getReason() != null && !ex.getReason().isBlank()) {
                errorBody.put("message", ex.getReason());
            } else {
                errorBody.put("message", ex.getStatusCode().toString());
            }
            errorBody.put("status", ex.getStatusCode().value());
            errorBody.put("preview", preview);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(errorBody);

        } catch (Exception e) {
            // 네트워크/타임아웃 등 일반 예외
            Map<String, Object> errorBody = new LinkedHashMap<>();
            errorBody.put("error", "GPT 서버 요청 실패");
            if (e.getMessage() != null && !e.getMessage().isBlank()) {
                errorBody.put("message", e.getMessage());
            }
            errorBody.put("preview", preview);
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(errorBody);
        }
    }
}
