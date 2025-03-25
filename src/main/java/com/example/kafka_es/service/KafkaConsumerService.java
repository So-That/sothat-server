package com.example.kafka_es.service;

import com.example.kafka_es.model.SentimentModel;
import com.example.kafka_es.model.WordModel;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.kafka_es.model.CommentModel;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class KafkaConsumerService {
    private final CommentService commentService;
    private final SentimentService sentimentService;
    private final WordService wordService;
    private final ObjectMapper objectMapper;
    private final List<CommentModel> commentBuffer = new ArrayList<>();
    private final List<SentimentModel> sentimentBuffer = new ArrayList<>();

    private final List<WordModel> wordBuffer = new ArrayList<>();

    private static final int BULK_SIZE = 15; // Bulk 저장 크기
    public KafkaConsumerService(CommentService commentService, SentimentService sentimentService, WordService wordService,ObjectMapper objectMapper) {
        this.commentService = commentService;
        this.sentimentService=sentimentService;
        this.wordService=wordService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "CleanedComments", groupId = "ES-group")
    public void consumeYoutube(String message) {
        try {

            // JSON 메시지를 Comment 객체로 변환
            CommentModel comment = objectMapper.readValue(message, CommentModel.class);

            // 버퍼에 댓글 추가
            commentBuffer.add(comment);

            // 버퍼가 BULK_SIZE에 도달하면 Elasticsearch에 저장
            if (commentBuffer.size() >= BULK_SIZE) {
                commentService.saveCommentsToES(commentBuffer);
                commentBuffer.clear(); // 버퍼 초기화
            }
            System.out.println("Consumed comment: " + comment);
        } catch (Exception e) {
            System.err.println("Failed to consume message: " + e.getMessage());
        }
    }
    @KafkaListener(topics = "sentiment", groupId = "ES-group")
    public void consumeSentiment(String message) {
        try {
            // JSON 메시지를 Tree 형태로 읽기
            JsonNode rootNode = objectMapper.readTree(message);
            JsonNode sentimentAnalysis = rootNode.get("sentiment_analysis");
            JsonNode original = rootNode.get("original_message");

            if (sentimentAnalysis != null && original != null) {
                // SentimentModel 객체 생성 및 데이터 매핑
                SentimentModel sentiment = new SentimentModel();

                // original_message에서 id 값 추출
                sentiment.setReply(original.get("id").asText());
                sentiment.setSentiment(sentimentAnalysis.get("Sentiment").asText());
                sentiment.setPositive(sentimentAnalysis.get("SentimentScore").get("Positive").asDouble());
                sentiment.setNegative(sentimentAnalysis.get("SentimentScore").get("Negative").asDouble());
                sentiment.setNeutral(sentimentAnalysis.get("SentimentScore").get("Neutral").asDouble());
                sentiment.setMixed(sentimentAnalysis.get("SentimentScore").get("Mixed").asDouble());

                // 버퍼에 추가
                sentimentBuffer.add(sentiment);

                // 버퍼가 BULK_SIZE에 도달하면 Elasticsearch에 저장
                if (sentimentBuffer.size() >= BULK_SIZE) {
                    sentimentService.saveSentimentsToES(sentimentBuffer);
                    sentimentBuffer.clear();
                }

                System.out.println("Consumed sentiment: " + sentiment);
            } else {
                System.err.println("Message does not contain required fields: " + message);
            }
        } catch (Exception e) {
            System.err.println("Failed to consume message: " + e.getMessage());
        }
    }

    @KafkaListener(topics = "word", groupId = "word-consumer-group")
    public void consumeWord(String message) {
        try {
            // JSON 메시지에서 WordModel 객체로 변환
            WordModel word = objectMapper.readValue(message, WordModel.class);

            // 버퍼에 단어 추가
            wordBuffer.add(word);

            // 버퍼가 BULK_SIZE에 도달하면 Elasticsearch에 저장
            if (wordBuffer.size() >= BULK_SIZE) {
                wordService.updateToES(wordBuffer);
                wordBuffer.clear(); // 버퍼 초기화
            }
            System.out.println("Consumed word: " + word);
        } catch (JsonProcessingException e) {
            System.err.println("Failed to parse message: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("Unexpected error: " + e.getMessage());
        }
    }


}
