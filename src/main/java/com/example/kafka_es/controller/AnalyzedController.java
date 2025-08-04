package com.example.kafka_es.controller;

import com.example.kafka_es.dto.AnalyzeRequest;
import com.example.kafka_es.dto.AnalyzedCommentResponse;
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
    public ResponseEntity<AnalyzedCommentResponse> previewMergedSummary(
            @RequestParam List<String> videoIds,
            @RequestParam String targetProduct
    ) {
        // 존재하는 요약은 DB에서 불러오고, 없는 건 새로 요약 후 합치기
        AnalyzedCommentResponse merged = consumerService.summarizeWithMergeIfNeeded(videoIds, targetProduct);

        return ResponseEntity.ok(merged);
    }


    /**
     * 프론트에서 전달된 videoUrls와 keyword를 기반으로 분석 후
     * GPT 서버로 POST 요청 보내 요약 결과 받아오기
     */
    @PostMapping("/summary")
    public ResponseEntity<Map<String, Object>> analyzeAndSummarize(@RequestBody AnalyzeRequest request) {
        List<String> videoIds = request.getUrls();
        String targetProduct = request.getKeyword();

        // Step 1. DB에서 분석 결과 조회
        List<AnalyzedCommentResponse> summaries = consumerService.fetchSummariesFromDB(videoIds);
        if (summaries.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "분석된 댓글이 없습니다."));
        }

        // Step 2. 하나로 병합
        AnalyzedCommentResponse merged = consumerService.mergeSummaries(summaries, targetProduct);
        System.out.println("GPT 서버 전송 데이터: " + merged);

        // Step 3. GPT 서버로 POST
        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<AnalyzedCommentResponse> entity = new HttpEntity<>(merged, headers);

        ResponseEntity<Map> gptResponse = restTemplate.postForEntity(
                gptServer + "/comments/summary/",
                entity,
                Map.class
        );

        System.out.println("GPT 응답: " + gptResponse);
        return ResponseEntity.ok(gptResponse.getBody());
    }

}
