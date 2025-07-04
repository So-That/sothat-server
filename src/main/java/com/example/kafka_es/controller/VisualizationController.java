package com.example.kafka_es.controller;

import com.example.kafka_es.model.CommentModel;
import com.example.kafka_es.service.KafkaConsumerService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/comments")
public class VisualizationController {

    private final KafkaConsumerService kafkaConsumerService;

    public VisualizationController(KafkaConsumerService kafkaConsumerService) {
        this.kafkaConsumerService = kafkaConsumerService;
    }

    @GetMapping("/visualization")
    public List<CommentModel> getCommentsForVisualization() {
        return kafkaConsumerService.getVisualizationComments();
    }
}

