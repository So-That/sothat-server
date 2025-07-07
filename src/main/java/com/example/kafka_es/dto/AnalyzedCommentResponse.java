package com.example.kafka_es.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

@Getter
@Setter
public class AnalyzedCommentResponse {

    private MetaInfo metaInfo;

    private Map<String, java.util.List<String>> categoryReviews;
}
