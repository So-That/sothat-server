package com.example.kafka_es.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class MetaInfo {

    @JsonProperty("total_review_count")
    private int totalReviewCount;

    @JsonProperty("video_id")
    private String videoId;

    @JsonProperty("category_review_count")
    private Map<String, Integer> categoryReviewCount;

    @JsonProperty("total_sentiment_count")
    private Map<String, Integer> totalSentimentCount;

    @JsonProperty("category_sentiment_count")
    private Map<String, Map<String, Integer>> categorySentimentCount;
}
