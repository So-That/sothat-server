package com.example.kafka_es.service;

import com.example.kafka_es.dto.AnalyzedCommentResponse;
import com.example.kafka_es.dto.MetaInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnalyzedCommentService {

    private final DynamoDbClient dynamoDbClient;

    @Value("${dynamodb.table.name:AnalyzedComments}")
    private String tableName;

    // ✅ snake_case 매핑을 위한 ObjectMapper 설정
    private final ObjectMapper objectMapper =
            new ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);


    // ==============================================
    // SaveOne
    // ==============================================
    public void save(AnalyzedCommentResponse response) {
        if (response.getVideoId() == null || response.getVideoId().isBlank()) {
            response.setVideoId("unknown");
        }
        if (response.getCreatedAt() == null || response.getCreatedAt().isBlank()) {
            response.setCreatedAt(Instant.now().toString());
        }

        try {
            Map<String, AttributeValue> item = new HashMap<>();

            item.put("video_id", AttributeValue.builder().s(response.getVideoId()).build());
            item.put("created_at", AttributeValue.builder().s(response.getCreatedAt()).build());
            item.put("target_product", AttributeValue.builder()
                    .s(Optional.ofNullable(response.getTargetProduct()).orElse(""))
                    .build());

            // ✅ meta_info: JSON 객체로 직렬화 (이중 인코딩 방지)
            if (response.getMetaInfo() != null) {
                String metaJson = objectMapper.writeValueAsString(response.getMetaInfo());
                item.put("meta_info", AttributeValue.builder().s(metaJson).build());
            }

            // ✅ category_reviews: JSON 객체로 직렬화 (이중 인코딩 방지)
            if (response.getCategoryReviews() != null) {
                String reviewsJson = objectMapper.writeValueAsString(response.getCategoryReviews());
                item.put("category_reviews", AttributeValue.builder().s(reviewsJson).build());
            }

            // ✅ DynamoDB 저장
            dynamoDbClient.putItem(PutItemRequest.builder()
                    .tableName(tableName)
                    .item(item)
                    .build());

            log.info("✅ DynamoDB 저장 성공: videoId={}, createdAt={}",
                    response.getVideoId(), response.getCreatedAt());

        } catch (Exception e) {
            log.error("❌ DynamoDB 저장 실패", e);
            throw new RuntimeException(e);
        }
    }

    // ==============================================
    // SaveAll
    // ==============================================
    public void saveAll(List<AnalyzedCommentResponse> responses) {
        for (AnalyzedCommentResponse r : responses) {
            save(r);
        }
    }

    // ==============================================
    // Exists
    // ==============================================
    public boolean existsByVideoIdContaining(String videoId) {
        try {
            List<AnalyzedCommentResponse> found = findByVideoIdsIn(Collections.singletonList(videoId));
            return found != null && !found.isEmpty();
        } catch (Exception e) {
            log.error("❌ existsByVideoIdContaining 실패: videoId={}", videoId, e);
            return false;
        }
    }

    public boolean existsByVideoIdsIn(List<String> videoIds) {
        for (String videoId : videoIds) {
            List<AnalyzedCommentResponse> found = findByVideoIdsIn(Collections.singletonList(videoId));
            if (!found.isEmpty()) return true;
        }
        return false;
    }

    // ==============================================
    // Find
    // ==============================================
    public List<AnalyzedCommentResponse> findByVideoIdsIn(List<String> videoIds) {
        List<AnalyzedCommentResponse> results = new ArrayList<>();

        for (String videoId : videoIds) {
            try {
                QueryRequest query = QueryRequest.builder()
                        .tableName(tableName)
                        .keyConditionExpression("video_id = :v")
                        .expressionAttributeValues(Map.of(":v", AttributeValue.builder().s(videoId).build()))
                        .build();

                QueryResponse resp = dynamoDbClient.query(query);

                if (resp.hasItems()) {
                    for (Map<String, AttributeValue> item : resp.items()) {
                        AnalyzedCommentResponse r = new AnalyzedCommentResponse();

                        r.setVideoId(item.get("video_id").s());
                        r.setCreatedAt(item.get("created_at").s());
                        r.setTargetProduct(item.getOrDefault("target_product",
                                AttributeValue.builder().s("").build()).s());

                        // ✅ meta_info 역직렬화
                        if (item.containsKey("meta_info")) {
                            String json = item.get("meta_info").s();
                            MetaInfo meta = objectMapper.readValue(json, MetaInfo.class);
                            r.setMetaInfo(meta);
                        }

                        // ✅ category_reviews 역직렬화
                        if (item.containsKey("category_reviews")) {
                            String json = item.get("category_reviews").s();
                            Map<String, List<String>> reviews =
                                    objectMapper.readValue(json, Map.class);
                            r.setCategoryReviews(reviews);
                        }

                        results.add(r);
                    }
                }
            } catch (Exception e) {
                log.error("❌ DynamoDB 조회 실패: videoId={}", videoId, e);
            }
        }

        return results;
    }
}
