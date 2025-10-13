package com.example.kafka_es.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class MetaInfo {

    @JsonProperty("video_ids")
    private List<String> videoIds;

    @JsonProperty("total_review_count")
    private Integer totalReviewCount;

    @JsonProperty("category_review_count")
    private Map<String, Integer> categoryReviewCount;

    @JsonProperty("total_sentiment_count")
    private Map<String, Integer> totalSentimentCount;

    @JsonProperty("category_sentiment_count")
    private Map<String, Map<String, Integer>> categorySentimentCount;
}
