package com.example.kafka_es.service;

import com.example.kafka_es.model.CommentModel;
import com.example.kafka_es.dto.AnalyzedCommentResponse;
import com.example.kafka_es.dto.MetaInfo;
import com.example.kafka_es.repository.AnalyzedCommentRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    public KafkaConsumerService(ObjectMapper objectMapper,
                                KafkaTemplate<String, String> kafkaTemplate, AnalyzedCommentRepository analyzedCommentRepository) {
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

            System.out.println("Received comment for videoId=" + videoId + ": " + comment.getText());

        } catch (Exception e) {
            System.err.println("Failed to process message: " + e.getMessage());
        }
    }


    // 통계/요약 생성
    public AnalyzedCommentResponse createSummary(List<String> inputVideoIds, String targetProduct) {
        // 1. 이미 저장된 videoId 목록 조회
        List<AnalyzedCommentResponse> existing = analyzedCommentRepository.findByMetaInfo_VideoIdsIn(inputVideoIds);
        Set<String> existingVideoIds = existing.stream()
                .flatMap(doc -> doc.getMetaInfo().getVideoIds().stream())
                .collect(Collectors.toSet());

        // 2. 새로 처리할 videoIds
        List<String> newVideoIds = inputVideoIds.stream()
                .filter(id -> !existingVideoIds.contains(id))
                .collect(Collectors.toList());

        // 3. 저장할 게 없다면 종료
        if (newVideoIds.isEmpty()) {
            System.out.println("저장할 새로운 videoId가 없습니다.");
            return null;
        }

        // 4. 새로운 videoIds만으로 댓글 필터링 및 summary 생성
        List<CommentModel> comments = newVideoIds.stream()
                .filter(videoCommentsMap::containsKey)
                .flatMap(id -> videoCommentsMap.get(id).stream())
                .collect(Collectors.toList());

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

        meta.setVideoIds(newVideoIds);
        meta.setTotalReviewCount(comments.size());
        meta.setCategoryReviewCount(categoryCount);
        meta.setTotalSentimentCount(sentimentCount);
        meta.setCategorySentimentCount(categorySentiment);

        AnalyzedCommentResponse response = new AnalyzedCommentResponse();
        response.setTargetProduct(targetProduct);
        response.setMetaInfo(meta);
        response.setCategoryReviews(categoryReviews);

        System.out.println("===========MongoDB 저장 내용=======" + response);
        try {
            analyzedCommentRepository.save(response);
            System.out.println("저장 성공");
        } catch (Exception e) {
            System.out.println("저장 실패: " + e.getMessage());
            e.printStackTrace();
        }

        return response;
    }

    // 정렬 점수 계산
    private double computeScore(CommentModel c,
                                int minLike, int maxLike,
                                double minConf, double maxConf,
                                double minSent, double maxSent) {

        double likeNorm = (double)(c.getLikeCount() - minLike) / Math.max((maxLike - minLike), 1);
        double confNorm = (c.getCategoryConfidence() - minConf) / Math.max((maxConf - minConf), 0.0001);
        double sentNorm = Math.abs(c.getSentimentScore() - 0.5) * 2;

        return 0.5 * likeNorm + 0.25 * confNorm + 0.25 * sentNorm;
    }

}
