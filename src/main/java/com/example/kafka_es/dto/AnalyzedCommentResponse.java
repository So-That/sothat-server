package com.example.kafka_es.dto;

import com.example.kafka_es.dto.MetaInfo;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.Map;
import java.util.List;

@Getter
@Setter
public class AnalyzedCommentResponse {

    @JsonProperty("target_product")
    private String targetProduct;

    @JsonProperty("meta_info")
    private MetaInfo metaInfo;

    @JsonProperty("category_reviews")
    private Map<String, List<String>> categoryReviews;
}
