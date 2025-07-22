package com.example.kafka_es.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.util.Map;
import java.util.List;

@Getter
@Setter
@Document(collection="analyzed_summaries")
public class AnalyzedCommentResponse {

    @JsonIgnore
    @Id
    private String id;

    @JsonProperty("target_product")
    private String targetProduct;

    @JsonProperty("meta_info")
    private MetaInfo metaInfo;

    @JsonProperty("category_reviews")
    private Map<String, List<String>> categoryReviews;
}
