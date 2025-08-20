package com.example.kafka_es.service;

import com.example.kafka_es.dto.AnalyzedCommentResponse;
import com.example.kafka_es.dto.MetaInfo;
import com.example.kafka_es.model.CommentModel;
import com.example.kafka_es.repository.AnalyzedCommentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class KafkaConsumerService {

    private final ObjectMapper objectMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final AnalyzedCommentRepository analyzedCommentRepository;

    private final Map<String, List<CommentModel>> videoCommentsMap = new HashMap<>();

    public KafkaConsumerService(ObjectMapper objectMapper,
                                KafkaTemplate<String, String> kafkaTemplate,
                                AnalyzedCommentRepository analyzedCommentRepository) {
        this.objectMapper = objectMapper;
        this.kafkaTemplate = kafkaTemplate;
        this.analyzedCommentRepository = analyzedCommentRepository;
    }

    // Kafka에서 analyzed_comments 수신
    @KafkaListener(topics = "analyzed_comments", groupId = "analyzed_group")
    public void consume(String message) {
        try {
            CommentModel comment = objectMapper.readValue(message, CommentModel.class);
            String videoId = comment.getVideoId();
            if (videoId == null) return;

            videoCommentsMap.putIfAbsent(videoId, new ArrayList<>());
            videoCommentsMap.get(videoId).add(comment);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public List<AnalyzedCommentResponse> createSummary(List<String> inputVideoIds, String targetProduct) {
        List<AnalyzedCommentResponse> results = new ArrayList<>();

        for (String videoId : inputVideoIds) {
            if (!videoCommentsMap.containsKey(videoId)) continue;
            if (analyzedCommentRepository.existsByMetaInfo_VideoIdsContaining(videoId)) continue;

            List<CommentModel> comments = videoCommentsMap.get(videoId);
            if (comments.isEmpty()) continue;

            MetaInfo meta = new MetaInfo();
            Map<String, Integer> categoryCount = new HashMap<>();
            Map<String, Integer> sentimentCount = new HashMap<>();
            Map<String, Map<String, Integer>> categorySentiment = new HashMap<>();
            Map<String, List<CommentModel>> grouped = new HashMap<>();

            for (CommentModel comment : comments) {
                String category = comment.getCategoryLabel();
                String sentiment = comment.getSentiment();

                categoryCount.merge(category, 1, Integer::sum);
                sentimentCount.merge(sentiment, 1, Integer::sum);

                categorySentiment.putIfAbsent(category, new HashMap<>());
                categorySentiment.get(category).merge(sentiment, 1, Integer::sum);

                grouped.putIfAbsent(category, new ArrayList<>());
                grouped.get(category).add(comment);
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
        }

        analyzedCommentRepository.saveAll(results);
        return results;
    }

    // 카테고리별 대표 리뷰 추출
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

    //정렬 기준 점수 계산
    private double computeScore(CommentModel c, int minLike, int maxLike, double minConf, double maxConf, double minSent, double maxSent) {
        double likeNorm = (double) (c.getLikeCount() - minLike) / Math.max((maxLike - minLike), 1);
        double confNorm = (c.getCategoryConfidence() - minConf) / Math.max((maxConf - minConf), 0.0001);
        double sentNorm = Math.abs(c.getSentimentScore() - 0.5) * 2;
        return 0.5 * likeNorm + 0.25 * confNorm + 0.25 * sentNorm;
    }

    //DB에서 videoId 목록에 해당하는 요약 가져오기
    public List<AnalyzedCommentResponse> fetchSummariesFromDB(List<String> videoIds) {
        List<AnalyzedCommentResponse> results = new ArrayList<>();

        for (String videoId : videoIds) {
            List<AnalyzedCommentResponse> found =
                    analyzedCommentRepository.findByMetaInfo_VideoIdsIn(Collections.singletonList(videoId));

            if (found != null && !found.isEmpty()) {
                results.addAll(found);
            }
        }

        return results;
    }

    // 기존 + 새 요약 병합
    public AnalyzedCommentResponse mergeSummaries(List<AnalyzedCommentResponse> summaries, String targetProduct) {
        Map<String, List<String>> mergedCategoryReviews = new HashMap<>();
        MetaInfo mergedMeta = new MetaInfo();

        int totalReviewCount = 0;
        Map<String, Integer> categoryCount = new HashMap<>();
        Map<String, Integer> sentimentCount = new HashMap<>();
        Map<String, Map<String, Integer>> categorySentiment = new HashMap<>();
        Set<String> videoIdSet = new HashSet<>();

        for (AnalyzedCommentResponse summary : summaries) {
            MetaInfo meta = summary.getMetaInfo();

            videoIdSet.addAll(meta.getVideoIds());
            totalReviewCount += meta.getTotalReviewCount();
            meta.getCategoryReviewCount().forEach((k, v) -> categoryCount.merge(k, v, Integer::sum));
            meta.getTotalSentimentCount().forEach((k, v) -> sentimentCount.merge(k, v, Integer::sum));

            meta.getCategorySentimentCount().forEach((category, sentMap) -> {
                categorySentiment.putIfAbsent(category, new HashMap<>());
                sentMap.forEach((sent, cnt) -> {
                    categorySentiment.get(category).merge(sent, cnt, Integer::sum);
                });
            });

            summary.getCategoryReviews().forEach((category, texts) -> {
                mergedCategoryReviews.putIfAbsent(category, new ArrayList<>());
                List<String> currentList = mergedCategoryReviews.get(category);
                for (String text : texts) {
                    if (currentList.size() < 20) currentList.add(text);
                    else break;
                }
            });
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

        return merged;
    }

    // 존재하지 않는 것만 분석 + 모두 병합
    public AnalyzedCommentResponse summarizeWithMergeIfNeeded(List<String> requestedVideoIds, String targetProduct) {
        List<AnalyzedCommentResponse> existingSummaries = fetchSummariesFromDB(requestedVideoIds);
        Set<String> existingVideoIds = existingSummaries.stream()
                .flatMap(r -> r.getMetaInfo().getVideoIds().stream())
                .collect(Collectors.toSet());

        List<String> missingVideoIds = requestedVideoIds.stream()
                .filter(id -> !existingVideoIds.contains(id))
                .collect(Collectors.toList());

        List<AnalyzedCommentResponse> newSummaries = createSummary(missingVideoIds, targetProduct);

        List<AnalyzedCommentResponse> allSummaries = new ArrayList<>();
        allSummaries.addAll(existingSummaries);
        allSummaries.addAll(newSummaries);

        return mergeSummaries(allSummaries, targetProduct);
    }
}
