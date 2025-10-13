package com.example.kafka_es.service;

import com.example.kafka_es.dto.AnalyzedCommentResponse;
import com.example.kafka_es.dto.MetaInfo;
import com.example.kafka_es.kafka.Topics;
import com.example.kafka_es.model.CommentModel;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class KafkaConsumerService {

    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final AnalyzedCommentService analyzedCommentService;
    private final DoneWaiter doneWaiter;

    private final Map<String, CopyOnWriteArrayList<CommentModel>> videoCommentsMap = new ConcurrentHashMap<>();
    private final Map<String, String> pendingTargetProductMap = new ConcurrentHashMap<>();

    // =====================================================
    // DynamoDB 조회
    // =====================================================
    public List<AnalyzedCommentResponse> fetchSummariesFromDB(List<String> videoIds) {
        log.info("📚 DB 조회 시작: videoIds={}", videoIds);
        List<AnalyzedCommentResponse> results = new ArrayList<>();
        int hit = 0;

        for (String videoId : videoIds) {
            List<AnalyzedCommentResponse> found =
                    analyzedCommentService.findByVideoIdsIn(Collections.singletonList(videoId));

            int size = (found == null) ? 0 : found.size();
            log.info("📗 DB 조회 결과: videoId={}, foundCount={}", videoId, size);

            if (found != null && !found.isEmpty()) {
                hit += size;
                results.addAll(found);
            }
        }

        log.info("📖 DB 조회 종료: 총획득개수(hit)={}, 반환개수={}", hit, results.size());
        return results;
    }

    // =====================================================
    // DB + 신규 요약 병합
    // =====================================================
    public AnalyzedCommentResponse summarizeWithMergeIfNeeded(List<String> requestedVideoIds, String targetProduct) {
        log.info("🧩 summarizeWithMergeIfNeeded 시작: 요청개수={}, requestedVideoIds={}",
                (requestedVideoIds == null ? 0 : requestedVideoIds.size()), requestedVideoIds);

        List<AnalyzedCommentResponse> existingSummaries = fetchSummariesFromDB(requestedVideoIds);
        if (existingSummaries != null && !existingSummaries.isEmpty()) {
            // ✅ 이미 DB에 있으면 그대로 첫 번째 결과 리턴
            AnalyzedCommentResponse existing = existingSummaries.get(0);
            log.info("✅ 기존 DB 데이터 사용: videoId={}, totalReviews={}",
                    existing.getVideoId(),
                    existing.getMetaInfo() != null ? existing.getMetaInfo().getTotalReviewCount() : 0);
            return existing;
        }

        // ✅ 없으면 새로 생성 후 병합
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

    // =====================================================
    // 분석 대상 등록
    // =====================================================
    public void registerTargetProduct(String videoId, String targetProduct) {
        if (videoId != null && !videoId.isBlank()) {
            pendingTargetProductMap.put(videoId, targetProduct == null ? "" : targetProduct);
            log.info("📝 registerTargetProduct: videoId={}, targetProduct={}", videoId, targetProduct);
        }
    }

    // =====================================================
    // Kafka 수신
    // =====================================================
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
        } catch (Exception e) {
            log.error("❌ Kafka 메시지 파싱 실패. raw={}", trim(message), e);
        }
    }


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
        }

        return categoryReviews;
    }

    private double computeScore(CommentModel c, int minLike, int maxLike,
                                double minConf, double maxConf,
                                double minSent, double maxSent) {
        double likeNorm = (double) (c.getLikeCount() - minLike) / Math.max((maxLike - minLike), 1);
        double confNorm = (c.getCategoryConfidence() - minConf) / Math.max((maxConf - minConf), 0.0001);
        double sentNorm = Math.abs(c.getSentimentScore() - 0.5) * 2;
        return 0.5 * likeNorm + 0.25 * confNorm + 0.25 * sentNorm;
    }

    // =====================================================
    // 요약 생성
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

            boolean exists = analyzedCommentService.existsByVideoIdsIn(Collections.singletonList(videoId));
            if (exists) continue;

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
            response.setVideoId(videoId);
            response.setCreatedAt(Instant.now().toString());
            response.setTargetProduct(targetProduct);
            response.setMetaInfo(meta);
            response.setCategoryReviews(categoryReviews);

            results.add(response);
        }

        if (!results.isEmpty()) {
            analyzedCommentService.saveAll(results);
            log.info("🗄️ saveAll 완료: 저장개수={}", results.size());
        } else {
            log.warn("🗄️ 저장 스킵: 저장할 결과가 없습니다(results empty).");
        }

        log.info("✅ createSummary 종료: durationMs={}", System.currentTimeMillis() - t0);
        return results;
    }

    // =====================================================
    // 병합 로직 (MetaInfo 필드 전부 병합)
    // =====================================================
    public AnalyzedCommentResponse mergeSummaries(List<AnalyzedCommentResponse> summaries, String targetProduct) {
        if (summaries == null || summaries.isEmpty()) {
            log.warn("⚠️ mergeSummaries: summaries 비어있음");
            return null;
        }

        AnalyzedCommentResponse merged = new AnalyzedCommentResponse();
        MetaInfo mergedMeta = new MetaInfo();

        // videoId 모으기
        Set<String> videoIdSet = summaries.stream()
                .filter(s -> s.getMetaInfo() != null && s.getMetaInfo().getVideoIds() != null)
                .flatMap(s -> s.getMetaInfo().getVideoIds().stream())
                .collect(Collectors.toSet());

        merged.setVideoId(videoIdSet.isEmpty() ? "unknown" : videoIdSet.iterator().next());
        merged.setCreatedAt(Instant.now().toString());
        merged.setTargetProduct(targetProduct);

        // 전체 리뷰 개수 합
        int totalReviewCount = summaries.stream()
                .filter(s -> s.getMetaInfo() != null)
                .mapToInt(s -> s.getMetaInfo().getTotalReviewCount())
                .sum();

        // 카테고리별 리뷰 수 합
        Map<String, Integer> categoryReviewCount = new HashMap<>();
        Map<String, Integer> totalSentimentCount = new HashMap<>();
        Map<String, Map<String, Integer>> categorySentimentCount = new HashMap<>();
        Map<String, List<String>> categoryReviews = new HashMap<>();

        for (AnalyzedCommentResponse s : summaries) {
            MetaInfo m = s.getMetaInfo();
            if (m == null) continue;

            // category_review_count
            if (m.getCategoryReviewCount() != null) {
                m.getCategoryReviewCount().forEach((k, v) -> categoryReviewCount.merge(k, v, Integer::sum));
            }

            // total_sentiment_count
            if (m.getTotalSentimentCount() != null) {
                m.getTotalSentimentCount().forEach((k, v) -> totalSentimentCount.merge(k, v, Integer::sum));
            }

            // category_sentiment_count
            if (m.getCategorySentimentCount() != null) {
                m.getCategorySentimentCount().forEach((cat, inner) -> {
                    categorySentimentCount.computeIfAbsent(cat, k -> new HashMap<>());
                    inner.forEach((sent, val) ->
                            categorySentimentCount.get(cat).merge(sent, val, Integer::sum));
                });
            }

            // category_reviews
            if (s.getCategoryReviews() != null) {
                s.getCategoryReviews().forEach((cat, list) -> {
                    categoryReviews.computeIfAbsent(cat, k -> new ArrayList<>()).addAll(list);
                });
            }
        }

        mergedMeta.setVideoIds(new ArrayList<>(videoIdSet));
        mergedMeta.setTotalReviewCount(totalReviewCount);
        mergedMeta.setCategoryReviewCount(categoryReviewCount);
        mergedMeta.setTotalSentimentCount(totalSentimentCount);
        mergedMeta.setCategorySentimentCount(categorySentimentCount);

        merged.setMetaInfo(mergedMeta);
        merged.setCategoryReviews(categoryReviews);

        return merged;
    }

    // =====================================================
    // 유틸
    // =====================================================
    private String nullToUnknown(String s) {
        return (s == null || s.isBlank()) ? "UNKNOWN" : s;
    }

    private String trim(String s) {
        if (s == null) return null;
        return (s.length() > 500) ? s.substring(0, 500) + "...(truncated)" : s;
    }
}
