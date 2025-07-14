package com.example.kafka_es.controller;

import com.example.kafka_es.dto.AnalyzeRequest;
import com.example.kafka_es.dto.AnalyzedCommentResponse;
import com.example.kafka_es.model.CommentModel;
import com.example.kafka_es.service.KafkaConsumerService;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/comments")
public class AnalyzedController {

    private final KafkaConsumerService consumerService;

    @Value("${gpt.server:http://localhost:8000}")
    private String gptServer;

    public AnalyzedController(KafkaConsumerService consumerService) {
        this.consumerService = consumerService;
    }

    @GetMapping("/summary/preview")
    public ResponseEntity<AnalyzedCommentResponse> previewSummary(
            @RequestParam List<String> videoIds,
            @RequestParam String targetProduct
    ) {
        AnalyzedCommentResponse summary = consumerService.createSummary(videoIds, targetProduct);
        return ResponseEntity.ok(summary);
    }

    /**
     * 프론트에서 전달된 videoUrls와 keyword를 기반으로 분석 후
     * GPT 서버로 POST 요청 보내 요약 결과 받아오기
     */
    @PostMapping("/summary")
    public ResponseEntity<Map<String, Object>> analyzeAndSummarize(@RequestBody AnalyzeRequest request) {
        List<String> videoIds = request.getUrls();
        String targetProduct = request.getKeyword();

        // Step 1. 분석
        AnalyzedCommentResponse summary = consumerService.createSummary(videoIds, targetProduct);
        System.out.println("파이썬 서버 전송: " + summary);

        // Step 2. GPT 서버로 POST 요청
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<AnalyzedCommentResponse> entity = new HttpEntity<>(summary, headers);

        ResponseEntity<Map> gptResponse = restTemplate.postForEntity(
                gptServer + "/comments/summary/",
                entity,
                Map.class
        );

        System.out.println("GPT 응답: " + gptResponse);
        return ResponseEntity.ok(gptResponse.getBody());
    }


    /**
     * Kafka에서 수집된 원본 댓글 조회
     */
    @GetMapping("/visualize")
    public ResponseEntity<List<CommentModel>> getRawComments() {
        return ResponseEntity.ok(consumerService.getVisualizationComments());
    }

    /**
     * KafkaConsumerService에 저장된 댓글 데이터 초기화
     */
    @PostMapping("/clear")
    public ResponseEntity<String> clearComments() {
        consumerService.clearVisualizationComments();
        return ResponseEntity.ok("Cleared");
    }

}
