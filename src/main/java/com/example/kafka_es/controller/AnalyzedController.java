package com.example.kafka_es.controller;

import com.example.kafka_es.dto.AnalyzeRequest;
import com.example.kafka_es.dto.AnalyzedCommentResponse;
import com.example.kafka_es.dto.MetaInfo;
import com.example.kafka_es.service.DoneWaiter;
import com.example.kafka_es.service.GptClientService;
import com.example.kafka_es.service.KafkaConsumerService;
import com.example.kafka_es.service.YouTubeProducerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

    @Value("${controller.wait-timeout-seconds:25}")
    private long waitTimeoutSeconds;

    @PostMapping("/summary")
    public ResponseEntity<Map<String, Object>> summarizeSync(@RequestBody AnalyzeRequest request) {
        List<String> videoIds = Optional.ofNullable(request.getUrls()).orElseGet(List::of);
        String targetProduct = Optional.ofNullable(request.getKeyword()).orElse("");

        if (videoIds.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "empty_video_ids"));
        }

        // 0) targetProduct 등록
        for (String vid : videoIds) {
            consumerService.registerTargetProduct(vid, targetProduct);
        }

        // 1) 이미 DB에 있는 videoId는 대기 대상에서 제외
        var existingSummaries = consumerService.fetchSummariesFromDB(videoIds);
        Set<String> existingIds = existingSummaries.stream()
                .filter(Objects::nonNull)
                .filter(s -> s.getMetaInfo() != null && s.getMetaInfo().getVideoIds() != null)
                .flatMap(s -> s.getMetaInfo().getVideoIds().stream())
                .collect(Collectors.toSet());

        List<String> missingIds = videoIds.stream().filter(v -> !existingIds.contains(v)).toList();

        // 2) 분석 트리거 (START/RAW/END 발행)
        youTubeProducerService.fetchCommentByWord(videoIds);

        // 3) 누락분에 대해서만 DONE 대기 (최대 waitTimeoutSeconds)
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

        // 4) 최종 요약 만들기(기존 + 이번에 생성된 것 병합)
        AnalyzedCommentResponse preview = consumerService.summarizeWithMergeIfNeeded(videoIds, targetProduct);

        // 5) GPT 서버 호출 (preview를 그대로 전송)
        Map<String, Object> gptReviews = gptClientService.requestGptSummary(preview);

        // 6) 요구 포맷으로 응답 변환 (snake_case)
        Map<String, Object> body = buildSnakeCaseResponse(preview, gptReviews);

        return ResponseEntity.ok(body);
    }

    // ==========================
    // 응답 포맷(Snake Case) 빌더
    // ==========================
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
        out.put("gpt_reviews", gptReviews == null ? Map.of() : gptReviews);
        return out;
    }

    private Object nullSafe(Object v) { return v == null ? "" : v; }

    private <T> T nz(T v) {
        if (v == null) {
            if (v instanceof Map) return (T) Map.of();
            if (v instanceof List) return (T) List.of();
        }
        return v;
    }
}
