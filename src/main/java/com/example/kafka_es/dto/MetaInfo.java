package com.example.kafka_es.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class MetaInfo {

    private int totalReviewCount;

    private String videoId;

    private Map<String, Integer> categoryReviewCount;

    private Map<String, Integer> totalSentimentCount;

    private Map<String, Map<String, Integer>> categorySentimentCount;
}
