package com.example.kafka_es.controller;
import com.example.kafka_es.dto.AnalyzedCommentResponse;
import com.example.kafka_es.model.CommentModel;
import com.example.kafka_es.service.KafkaConsumerService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.MediaType;

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

    @GetMapping("/summary")
    public ResponseEntity<AnalyzedCommentResponse> getSummary() {
        AnalyzedCommentResponse response = consumerService.createSummary();
        return ResponseEntity.ok(response);
    }
    @PostMapping("/summary")
    public ResponseEntity<Map<String, Object>> summarizeComments() {

        AnalyzedCommentResponse request = consumerService.createSummary(); // JSON 가공

        System.out.println("파이썬 서버 전송:"+request);
        // GPT 요약 API 서버로 POST
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<AnalyzedCommentResponse> entity = new HttpEntity<>(request, headers);

        ResponseEntity<Map> gptResponse = restTemplate.postForEntity(
                gptServer + "/comments/summary/",  // ✅ 문자열 연결
                entity,
                Map.class
        );
        System.out.println("gptResponse = " + gptResponse);

        return ResponseEntity.ok(gptResponse.getBody());
    }


    @GetMapping("/visualize")
    public ResponseEntity<List<CommentModel>> getRawComments() {
        return ResponseEntity.ok(consumerService.getVisualizationComments());
    }

    @PostMapping("/clear")
    public ResponseEntity<String> clearComments() {
        consumerService.clearVisualizationComments();
        return ResponseEntity.ok("Cleared");
    }
}

