package com.example.kafka_es.service;

import com.example.kafka_es.model.CommentModel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
public class KafkaConsumerService {

    private final CommentService commentService;
    private final ObjectMapper objectMapper;

    // Elasticsearch 저장용 버퍼
    private final List<CommentModel> commentBuffer = new ArrayList<>();
    private static final int BULK_SIZE = 15;

    // 시각화용 메모리 저장소
    private final List<CommentModel> visualizationComments = new ArrayList<>();

    public KafkaConsumerService(CommentService commentService, ObjectMapper objectMapper) {
        this.commentService = commentService;
        this.objectMapper = objectMapper;
    }

    // ✅ 1. ES 저장용 Kafka Consumer
    @KafkaListener(topics = "analyzed_comments", groupId = "ES-group")
    public void consumeAndSaveToES(String message) {
        try {
            CommentModel comment = objectMapper.readValue(message, CommentModel.class);
            commentBuffer.add(comment);

            if (commentBuffer.size() >= BULK_SIZE) {
                commentService.saveCommentsToES(commentBuffer);
                commentBuffer.clear();
            }

            System.out.println("Consumed for ES: " + comment);
        } catch (Exception e) {
            System.err.println("Failed to consume for ES: " + e.getMessage());
        }
    }

    // ✅ 2. 시각화용 Kafka Consumer
    @KafkaListener(topics = "analyzed_comments", groupId = "visualize")
    public void consumeForVisualization(String message) {
        try {
            CommentModel comment = objectMapper.readValue(message, CommentModel.class);
            visualizationComments.add(comment);
            System.out.println("Consumed for Visualization: " + comment);
        } catch (Exception e) {
            System.err.println("Failed to consume for visualization: " + e.getMessage());
        }
    }


    // ✅ 시각화 데이터 조회용 getter
    public List<CommentModel> getVisualizationComments() {
        return new ArrayList<>(visualizationComments);
    }

    // ✅ (Optional) 초기화 메서드
    public void clearVisualizationComments() {
        visualizationComments.clear();
    }
}
