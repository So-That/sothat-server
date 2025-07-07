package com.example.kafka_es.controller;

import com.example.kafka_es.dto.AnalyzedCommentResponse;
import com.example.kafka_es.model.CommentModel;
import com.example.kafka_es.service.KafkaConsumerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/comments")
public class AnalyzedController {

    private final KafkaConsumerService consumerService;

    public AnalyzedController(KafkaConsumerService consumerService) {
        this.consumerService = consumerService;
    }

    @GetMapping("/summary")
    public ResponseEntity<AnalyzedCommentResponse> getSummary() {
        AnalyzedCommentResponse response = consumerService.createSummary();
        return ResponseEntity.ok(response);
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

