package com.example.kafka_es.controller;

import com.example.kafka_es.dto.AnalyzeRequest;
import com.example.kafka_es.dto.AnalyzedCommentResponse;
import com.example.kafka_es.dto.MetaInfo;
import com.example.kafka_es.service.DoneWaiter;
import com.example.kafka_es.service.GptClientService;
import com.example.kafka_es.service.KafkaConsumerService;
import com.example.kafka_es.service.YouTubeProducerService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@RestController
@RequestMapping("/comments")
@RequiredArgsConstructor
public class AnalyzedController {

    private final KafkaConsumerService consumerService;
    private final YouTubeProducerService youTubeProducerService;
    private final GptClientService gptClientService;
    private final DoneWaiter doneWaiter;

    @Value("${controller.wait-timeout-seconds:60}")
    private long waitTimeoutSeconds;

    // ✅ JSON pretty 로그 출력을 위한 ObjectMapper
    private final ObjectMapper logMapper =
            new ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).findAndRegisterModules();

    // ======================================
    // 🔹 댓글 분석 + GPT 요약 종합 API
    // ======================================
    @PostMapping("/summary")
    public ResponseEntity<Map<String, Object>> summarizeSync(@RequestBody AnalyzeRequest request) {
        List<String> videoIds = Optional.ofNullable(request.getUrls()).orElseGet(List::of);
        String targetProduct = Optional.ofNullable(request.getKeyword()).orElse("");

        if (videoIds.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "empty_video_ids"));
        }

        // 0️⃣ targetProduct 등록
        for (String vid : videoIds) {
            consumerService.registerTargetProduct(vid, targetProduct);
        }

        // 1️⃣ 이미 DynamoDB에 존재하는 videoId는 대기 제외
        var existingSummaries = consumerService.fetchSummariesFromDB(videoIds);

        // ✅ 로그 추가 (DynamoDB에서 읽어온 내용)
        try {
            String jsonLog = logMapper.writerWithDefaultPrettyPrinter().writeValueAsString(existingSummaries);
            log.info("📦 [DynamoDB Fetch Result] existingSummaries={}", jsonLog);
        } catch (Exception e) {
            log.warn("⚠️ existingSummaries 로그 변환 실패", e);
        }

        Set<String> existingIds = existingSummaries.stream()
                .filter(Objects::nonNull)
                .filter(s -> s.getMetaInfo() != null && s.getMetaInfo().getVideoIds() != null)
                .flatMap(s -> s.getMetaInfo().getVideoIds().stream())
                .collect(Collectors.toSet());

        List<String> missingIds = videoIds.stream()
                .filter(v -> !existingIds.contains(v))
                .toList();

        // 2️⃣ Kafka로 분석 트리거 전송
        youTubeProducerService.fetchCommentByWord(videoIds);

        // 3️⃣ 누락된 videoId에 대해 DONE 신호 대기
        if (!missingIds.isEmpty()) {
            CompletableFuture<?> all = CompletableFuture.allOf(
                    missingIds.stream().map(doneWaiter::waitFor).toArray(CompletableFuture[]::new)
            );
            try {
                all.get(Duration.ofSeconds(waitTimeoutSeconds).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            } catch (Exception timeout) {
                log.warn("⏰ DONE wait timeout. Proceeding with available results. missingIds={}", missingIds);
            }
        }

        // 4️⃣ DynamoDB 결과 합쳐서 요약
        AnalyzedCommentResponse preview = consumerService.summarizeWithMergeIfNeeded(videoIds, targetProduct);
        if (preview == null || preview.getMetaInfo() == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "no_summary_found"));
        }

        // ✅ DynamoDB에서 병합된 최종 preview 로그 출력
        try {
            String jsonLog = logMapper.writerWithDefaultPrettyPrinter().writeValueAsString(preview);
            log.info("🧾 [Merged Preview Result] preview={}", jsonLog);
        } catch (Exception e) {
            log.warn("⚠️ preview 로그 변환 실패", e);
        }

        // 5️⃣ GPT 서버 호출 (snake_case 포맷으로 전송)
        Map<String, Object> bodyForGpt = buildSnakeCaseResponse(preview, Map.of());
        Map<String, Object> gptReviews = gptClientService.requestGptSummary(bodyForGpt);

        // 6️⃣ 프론트 요청 형식으로 최종 응답 반환
        Map<String, Object> body = buildSnakeCaseResponse(preview, gptReviews);
        return ResponseEntity.ok(body);
    }

    // ======================================
    // 🔹 공용 응답 포맷 (snake_case 변환)
    // ======================================
    private Map<String, Object> buildSnakeCaseResponse(AnalyzedCommentResponse preview, Map<String, Object> gptReviews) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("target_product", nullSafe(preview.getTargetProduct()));

        MetaInfo m = preview.getMetaInfo();
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("total_review_count", m.getTotalReviewCount());
        meta.put("video_id", m.getVideoIds() == null ? List.of() : m.getVideoIds());
        meta.put("category_review_count", nz(m.getCategoryReviewCount()));
        meta.put("total_sentiment_count", nz(m.getTotalSentimentCount()));
        meta.put("category_sentiment_count", nz(m.getCategorySentimentCount()));
        out.put("meta_info", meta);

        out.put("category_reviews", nz(preview.getCategoryReviews()));

        // ✅ gpt_reviews는 DB에 저장하지 않음
        out.put("gpt_reviews", gptReviews == null ? Map.of() : gptReviews);
        return out;
    }

    // ======================================
    // 🔹 null-safe 유틸
    // ======================================
    private Object nullSafe(Object v) {
        return v == null ? "" : v;
    }

    private <T> T nz(T v) {
        if (v == null) {
            if (v instanceof Map) return (T) Map.of();
            if (v instanceof List) return (T) List.of();
        }
        return v;
    }
}
