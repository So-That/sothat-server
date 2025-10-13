package com.example.kafka_es.repository;

import com.example.kafka_es.dto.AnalyzedCommentResponse;
import com.example.kafka_es.dto.MetaInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.util.*;

@Slf4j
@Repository
@RequiredArgsConstructor
public class AnalyzedCommentRepository {

    private final DynamoDbClient dynamoDbClient;

    @Value("${dynamodb.table.name:AnalyzedComments}")
    private String tableName;

    // ✅ snake_case 대응 ObjectMapper
    private final ObjectMapper objectMapper =
            new ObjectMapper().setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    // =============================================
    // Save
    // =============================================
    public void save(AnalyzedCommentResponse response) {
        try {
            Map<String, AttributeValue> item = new HashMap<>();
            item.put("video_id", AttributeValue.builder().s(response.getVideoId()).build());
            item.put("created_at", AttributeValue.builder().s(response.getCreatedAt()).build());
            item.put("target_product", AttributeValue.builder()
                    .s(Optional.ofNullable(response.getTargetProduct()).orElse(""))
                    .build());

            // ✅ MetaInfo 직렬화 (JSON 문자열)
            if (response.getMetaInfo() != null) {
                String metaJson = objectMapper.writeValueAsString(response.getMetaInfo());
                item.put("meta_info", AttributeValue.builder().s(metaJson).build());
            }

            // ✅ category_reviews 직렬화 (JSON 문자열)
            if (response.getCategoryReviews() != null) {
                String reviewsJson = objectMapper.writeValueAsString(response.getCategoryReviews());
                item.put("category_reviews", AttributeValue.builder().s(reviewsJson).build());
            }

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

    // =============================================
    // FindById
    // =============================================
    public Optional<AnalyzedCommentResponse> findById(String videoId, String createdAt) {
        try {
            Map<String, AttributeValue> key = new HashMap<>();
            key.put("video_id", AttributeValue.builder().s(videoId).build());
            key.put("created_at", AttributeValue.builder().s(createdAt).build());

            GetItemResponse resp = dynamoDbClient.getItem(GetItemRequest.builder()
                    .tableName(tableName)
                    .key(key)
                    .build());

            if (!resp.hasItem()) return Optional.empty();

            Map<String, AttributeValue> item = resp.item();
            AnalyzedCommentResponse r = new AnalyzedCommentResponse();
            r.setVideoId(item.get("video_id").s());
            r.setCreatedAt(item.get("created_at").s());
            r.setTargetProduct(item.get("target_product").s());

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

            return Optional.of(r);
        } catch (Exception e) {
            log.error("❌ DynamoDB 조회 실패: videoId={}, createdAt={}", videoId, createdAt, e);
            return Optional.empty();
        }
    }

    // =============================================
    // Exists
    // =============================================
    public boolean existsByVideoId(String videoId) {
        try {
            QueryRequest request = QueryRequest.builder()
                    .tableName(tableName)
                    .keyConditionExpression("video_id = :v")
                    .expressionAttributeValues(Map.of(":v",
                            AttributeValue.builder().s(videoId).build()))
                    .limit(1)
                    .build();

            QueryResponse response = dynamoDbClient.query(request);
            return !response.items().isEmpty();
        } catch (Exception e) {
            log.error("❌ existsByVideoId 실패: videoId={}", videoId, e);
            return false;
        }
    }
}
