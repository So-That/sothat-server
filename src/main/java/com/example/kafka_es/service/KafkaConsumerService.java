package com.example.kafka_es.service;

import com.example.kafka_es.dto.AnalyzedCommentResponse;
import com.example.kafka_es.dto.MetaInfo;
import com.example.kafka_es.kafka.Topics;
import com.example.kafka_es.model.CommentModel;
import com.example.kafka_es.repository.AnalyzedCommentRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaConsumerService {

    // ---- DI ----
    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, String> kafkaTemplate; // 현재는 사용 안 해도 보존
    private final AnalyzedCommentRepository analyzedCommentRepository;
    private final DoneWaiter doneWaiter;

    // ---- In-memory buffers (thread-safe) ----
    // 문장 단위 분석결과를 비디오별로 모아두는 버퍼
    private final Map<String, CopyOnWriteArrayList<CommentModel>> videoCommentsMap = new ConcurrentHashMap<>();
    // 요청 시 등록해둔 타깃 제품명(키워드)
    private final Map<String, String> pendingTargetProductMap = new ConcurrentHashMap<>();

    // =====================================================
    // 외부에서 분석 대상 videoId와 targetProduct(키워드) 등록
    // =====================================================
    public void registerTargetProduct(String videoId, String targetProduct) {
        if (videoId != null && !videoId.isBlank()) {
            pendingTargetProductMap.put(videoId, targetProduct == null ? "" : targetProduct);
            log.info("📝 registerTargetProduct: videoId={}, targetProduct={}", videoId, targetProduct);
        }
    }

    // ======================================
    // analyzed_comments 수신 (문장 단위 결과)
    // ======================================
    @KafkaListener(topics = Topics.ANALYZED_COMMENTS, groupId = "analyzed_group")
    public void consume(String message) {
        try {
            CommentModel comment = objectMapper.readValue(message, CommentModel.class);
            String videoId = comment.getVideoId();

            if (videoId == null || videoId.isBlank()) {
                log.warn("🚫 videoId가 없는 메시지 수신. payload={}", trim(message));
                return;
            }

            videoCommentsMap
                    .computeIfAbsent(videoId, k -> new CopyOnWriteArrayList<>())
                    .add(comment);

            log.info("📥 Kafka 수신: videoId={}, 전체VideoKey수={}, 해당Video댓글수={}",
                    videoId, videoCommentsMap.size(), videoCommentsMap.get(videoId).size());
            log.debug("🧾 수신 CommentModel: {}", toJsonSafe(comment));

        } catch (Exception e) {
            log.error("❌ Kafka 메시지 파싱 실패. raw={}", trim(message), e);
        }
    }

    // =====================================================
    // 요약 생성: 아직 DB에 없는 videoId들만 대상으로 생성/저장
    // =====================================================
    public List<AnalyzedCommentResponse> createSummary(List<String> inputVideoIds, String targetProduct) {
        long t0 = System.currentTimeMillis();
        List<AnalyzedCommentResponse> results = new ArrayList<>();

        log.info("🧮 createSummary 시작: 요청 videoIds={}, targetProduct={}", inputVideoIds, targetProduct);

        for (String videoId : inputVideoIds) {
            List<CommentModel> comments = videoCommentsMap.getOrDefault(videoId, new CopyOnWriteArrayList<>());
            if (comments.isEmpty()) {
                log.warn("⚠️ videoId={} 의 메모리 댓글 개수=0. 건너뜀.", videoId);
                continue;
            }

            boolean exists = analyzedCommentRepository.existsByMetaInfo_VideoIdsContaining(videoId);
            log.info("🔎 DB 존재 여부: videoId={} -> {}", videoId, exists);
            if (exists) {
                // 이미 DB에 있으면 새로 만들 필요 없음
                continue;
            }

            // ===== 집계 =====
            MetaInfo meta = new MetaInfo();
            Map<String, Integer> categoryCount = new HashMap<>();
            Map<String, Integer> sentimentCount = new HashMap<>();
            Map<String, Map<String, Integer>> categorySentiment = new HashMap<>();
            Map<String, List<CommentModel>> grouped = new HashMap<>();

            for (CommentModel comment : comments) {
                String category = nullToUnknown(comment.getCategoryLabel());
                String sentiment = nullToUnknown(comment.getSentiment());

                categoryCount.merge(category, 1, Integer::sum);
                sentimentCount.merge(sentiment, 1, Integer::sum);
                categorySentiment.computeIfAbsent(category, k -> new HashMap<>())
                        .merge(sentiment, 1, Integer::sum);
                grouped.computeIfAbsent(category, k -> new ArrayList<>()).add(comment);
            }

            Map<String, List<String>> categoryReviews = extractTopCategoryReviews(grouped);

            meta.setVideoIds(Collections.singletonList(videoId));
            meta.setTotalReviewCount(comments.size());
            meta.setCategoryReviewCount(categoryCount);
            meta.setTotalSentimentCount(sentimentCount);
            meta.setCategorySentimentCount(categorySentiment);

            AnalyzedCommentResponse response = new AnalyzedCommentResponse();
            response.setTargetProduct(targetProduct);
            response.setMetaInfo(meta);
            response.setCategoryReviews(categoryReviews);

            results.add(response);

            log.info("🧱 요약 생성 완료: videoId={}, 댓글수={}, 카테고리수={}, 감정종류수={}",
                    videoId, comments.size(), categoryCount.size(), sentimentCount.size());
            log.debug("🧾 생성된 요약(videoId={}): {}", videoId, toJsonSafe(response));
        }

        if (!results.isEmpty()) {
            analyzedCommentRepository.saveAll(results);
            log.info("🗄️ saveAll 완료: 저장개수={}", results.size());
        } else {
            log.warn("🗄️ 저장 스킵: 저장할 결과가 없다(results empty).");
        }

        log.info("✅ createSummary 종료: durationMs={}", System.currentTimeMillis() - t0);
        return results;
    }

    // ======================================
    // 카테고리별 대표 리뷰 선별
    // ======================================
    private Map<String, List<String>> extractTopCategoryReviews(Map<String, List<CommentModel>> grouped) {
        Map<String, List<String>> categoryReviews = new HashMap<>();

        for (String category : grouped.keySet()) {
            List<CommentModel> commentsInCategory = grouped.get(category);

            int minLike = commentsInCategory.stream().mapToInt(CommentModel::getLikeCount).min().orElse(0);
            int maxLike = commentsInCategory.stream().mapToInt(CommentModel::getLikeCount).max().orElse(1);
            double minConf = commentsInCategory.stream().mapToDouble(CommentModel::getCategoryConfidence).min().orElse(0.5);
            double maxConf = commentsInCategory.stream().mapToDouble(CommentModel::getCategoryConfidence).max().orElse(1.0);
            double minSent = commentsInCategory.stream().mapToDouble(CommentModel::getSentimentScore).min().orElse(0.5);
            double maxSent = commentsInCategory.stream().mapToDouble(CommentModel::getSentimentScore).max().orElse(1.0);

            List<String> topTexts = commentsInCategory.stream()
                    .sorted((a, b) -> Double.compare(
                            computeScore(b, minLike, maxLike, minConf, maxConf, minSent, maxSent),
                            computeScore(a, minLike, maxLike, minConf, maxConf, minSent, maxSent)
                    ))
                    .limit(20)
                    .map(CommentModel::getText)
                    .collect(Collectors.toList());

            categoryReviews.put(category, topTexts);
            log.debug("🏷️ 카테고리='{}' 선별 텍스트 수={} (원본풀={})", category, topTexts.size(), commentsInCategory.size());
        }

        return categoryReviews;
    }

    // ======================================
    // 정렬 기준 점수
    // ======================================
    private double computeScore(CommentModel c, int minLike, int maxLike, double minConf, double maxConf, double minSent, double maxSent) {
        double likeNorm = (double) (c.getLikeCount() - minLike) / Math.max((maxLike - minLike), 1);
        double confNorm = (c.getCategoryConfidence() - minConf) / Math.max((maxConf - minConf), 0.0001);
        double sentNorm = Math.abs(c.getSentimentScore() - 0.5) * 2;
        return 0.5 * likeNorm + 0.25 * confNorm + 0.25 * sentNorm;
    }

    // ======================================
    // DB에서 videoId 목록에 해당하는 요약 가져오기
    // ======================================
    public List<AnalyzedCommentResponse> fetchSummariesFromDB(List<String> videoIds) {
        log.info("📚 DB 조회 시작: videoIds={}", videoIds);
        List<AnalyzedCommentResponse> results = new ArrayList<>();
        int hit = 0;

        for (String videoId : videoIds) {
            List<AnalyzedCommentResponse> found =
                    analyzedCommentRepository.findByMetaInfo_VideoIdsIn(Collections.singletonList(videoId));

            int size = (found == null) ? 0 : found.size();
            log.info("📗 DB 조회 결과: videoId={}, foundCount={}", videoId, size);

            if (found != null && !found.isEmpty()) {
                hit += size;
                results.addAll(found);
            }
        }

        log.info("📖 DB 조회 종료: 총획득개수(hit)={}, 반환개수={}", hit, results.size());
        log.debug("🧾 DB조회 결과 샘플: {}", results.isEmpty() ? "[]" : toJsonSafe(results.get(0)));
        return results;
    }

    // ======================================
    // 기존 + 새 요약 병합
    // ======================================
    public AnalyzedCommentResponse mergeSummaries(List<AnalyzedCommentResponse> summaries, String targetProduct) {
        log.info("🔗 병합 시작: 입력요약개수={}, targetProduct={}", summaries.size(), targetProduct);

        Map<String, List<String>> mergedCategoryReviews = new HashMap<>();
        MetaInfo mergedMeta = new MetaInfo();

        int totalReviewCount = 0;
        Map<String, Integer> categoryCount = new HashMap<>();
        Map<String, Integer> sentimentCount = new HashMap<>();
        Map<String, Map<String, Integer>> categorySentiment = new HashMap<>();
        Set<String> videoIdSet = new HashSet<>();

        for (AnalyzedCommentResponse summary : summaries) {
            if (summary == null || summary.getMetaInfo() == null) {
                log.warn("⚠️ 병합 중 null 요약 또는 meta 발견. skip. summary={}", summary);
                continue;
            }

            MetaInfo meta = summary.getMetaInfo();

            videoIdSet.addAll(meta.getVideoIds());
            totalReviewCount += meta.getTotalReviewCount();

            if (meta.getCategoryReviewCount() != null) {
                meta.getCategoryReviewCount().forEach((k, v) -> categoryCount.merge(k, v, Integer::sum));
            }
            if (meta.getTotalSentimentCount() != null) {
                meta.getTotalSentimentCount().forEach((k, v) -> sentimentCount.merge(k, v, Integer::sum));
            }
            if (meta.getCategorySentimentCount() != null) {
                meta.getCategorySentimentCount().forEach((category, sentMap) -> {
                    categorySentiment.computeIfAbsent(category, x -> new HashMap<>());
                    sentMap.forEach((sent, cnt) -> categorySentiment.get(category).merge(sent, cnt, Integer::sum));
                });
            }

            if (summary.getCategoryReviews() != null) {
                summary.getCategoryReviews().forEach((category, texts) -> {
                    mergedCategoryReviews.putIfAbsent(category, new ArrayList<>());
                    List<String> currentList = mergedCategoryReviews.get(category);
                    for (String text : texts) {
                        if (currentList.size() < 20) currentList.add(text);
                        else break; // 각 카테고리 최대 20개
                    }
                });
            }
        }

        mergedMeta.setVideoIds(new ArrayList<>(videoIdSet));
        mergedMeta.setTotalReviewCount(totalReviewCount);
        mergedMeta.setCategoryReviewCount(categoryCount);
        mergedMeta.setTotalSentimentCount(sentimentCount);
        mergedMeta.setCategorySentimentCount(categorySentiment);

        AnalyzedCommentResponse merged = new AnalyzedCommentResponse();
        merged.setTargetProduct(targetProduct);
        merged.setMetaInfo(mergedMeta);
        merged.setCategoryReviews(mergedCategoryReviews);

        log.info("🔗 병합 완료: videoIds수={}, totalReviewCount={}", videoIdSet.size(), totalReviewCount);
        log.debug("🧾 병합 결과: {}", toJsonSafe(merged));
        return merged;
    }

    // ======================================
    // 존재하지 않는 것만 분석 + 모두 병합
    // ======================================
    public AnalyzedCommentResponse summarizeWithMergeIfNeeded(List<String> requestedVideoIds, String targetProduct) {
        log.info("🧩 summarizeWithMergeIfNeeded 시작: 요청개수={}, requestedVideoIds={}",
                (requestedVideoIds == null ? 0 : requestedVideoIds.size()), requestedVideoIds);

        List<AnalyzedCommentResponse> existingSummaries = fetchSummariesFromDB(requestedVideoIds);
        Set<String> existingVideoIds = existingSummaries.stream()
                .filter(Objects::nonNull)
                .filter(s -> s.getMetaInfo() != null && s.getMetaInfo().getVideoIds() != null)
                .flatMap(r -> r.getMetaInfo().getVideoIds().stream())
                .collect(Collectors.toSet());

        List<String> missingVideoIds = requestedVideoIds.stream()
                .filter(id -> !existingVideoIds.contains(id))
                .collect(Collectors.toList());

        log.info("🧭 분기: DB존재개수={}, 누락개수={}, missingVideoIds={}",
                existingVideoIds.size(), missingVideoIds.size(), missingVideoIds);

        List<AnalyzedCommentResponse> newSummaries = createSummary(missingVideoIds, targetProduct);

        List<AnalyzedCommentResponse> allSummaries = new ArrayList<>();
        allSummaries.addAll(existingSummaries);
        allSummaries.addAll(newSummaries);

        AnalyzedCommentResponse merged = mergeSummaries(allSummaries, targetProduct);
        log.info("🏁 summarizeWithMergeIfNeeded 종료: 최종 videoIds수={}, 전체리뷰수={}",
                merged.getMetaInfo().getVideoIds().size(), merged.getMetaInfo().getTotalReviewCount());
        return merged;
    }

    // ======================================
    // DONE 수신 → DB 저장만 보장 + 컨트롤러 대기 해제
    // (컨트롤러가 동기 응답에 gpt_reviews를 포함하기 위함)
    // ======================================
    @KafkaListener(topics = Topics.ANALYSIS_DONE, groupId = "sothat-server")
    public void onDone(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            String videoId  = root.path("video_id").asText(null);
            int processed   = root.path("processed_count").asInt(-1); // 문장 단위(참고용)
            int expected    = root.path("expected_count").asInt(-1);  // 댓글 기대 수(참고용)
            String status   = root.path("status").asText("");

            log.info("✅ DONE 수신: videoId={}, processed={}, expected={}, status={}", videoId, processed, expected, status);
            if (videoId == null || videoId.isBlank()) {
                log.warn("✅ DONE 수신 but videoId null. payload={}", payload);
                return;
            }

            // analyzed_comments가 살짝 늦게 들어올 수 있어 잠깐 대기 (최대 5초)
            waitForAnalyzedBuffer(videoId, processed, 5000L);

            // 요청 시 등록해둔 타깃 제품명(키워드)
            String targetProduct = pendingTargetProductMap.getOrDefault(videoId, "");

            // 1) DB에 없으면 요약 생성/저장 (있으면 내부에서 skip)
            createSummary(Collections.singletonList(videoId), targetProduct);

            // 2) 동기 컨트롤러 대기 해제 (POST /comments/summary에서 기다리는 중)
            doneWaiter.signal(videoId);

            // 3) 메모리 정리
            pendingTargetProductMap.remove(videoId);
            videoCommentsMap.remove(videoId);

        } catch (Exception e) {
            log.error("❌ DONE 처리 실패. payload={}", payload, e);
        }
    }

    // ======================================
    // 유틸
    // ======================================
    /** analyzed_comments 수신이 DONE보다 늦을 수 있어 잠깐 버퍼가 채워질 때까지 대기 */
    private void waitForAnalyzedBuffer(String videoId, int needProcessed, long maxWaitMs) {
        if (needProcessed <= 0) return;
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < maxWaitMs) {
            int have = videoCommentsMap.getOrDefault(videoId, new CopyOnWriteArrayList<>()).size();
            if (have >= needProcessed) {
                log.info("⏳ 버퍼 채움 OK: videoId={}, have={}, need={}", videoId, have, needProcessed);
                return;
            }
            try { Thread.sleep(100); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); return; }
        }
        int have = videoCommentsMap.getOrDefault(videoId, new CopyOnWriteArrayList<>()).size();
        log.warn("⏰ 버퍼 대기 타임아웃: videoId={}, have={}, need={}, waitedMs={}", videoId, have, needProcessed, maxWaitMs);
    }

    private String nullToUnknown(String s) {
        return (s == null || s.isBlank()) ? "UNKNOWN" : s;
    }

    private String toJsonSafe(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (JsonProcessingException e) {
            return String.valueOf(o);
        }
    }

    private String trim(String s) {
        if (s == null) return null;
        return (s.length() > 500) ? s.substring(0, 500) + "...(truncated)" : s;
    }
}
