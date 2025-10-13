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
public class AnalyzedCommentResponse {

    @JsonProperty("video_id")
    private String videoId;

    @JsonProperty("created_at")
    private String createdAt;

    @JsonProperty("target_product")
    private String targetProduct;

    @JsonProperty("meta_info")
    private MetaInfo metaInfo;

    @JsonProperty("category_reviews")
    private Map<String, List<String>> categoryReviews;
}
