package com.example.kafka_es.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CommentModel {

    @JsonProperty("video_id")
    private String videoId;

    @JsonProperty("text")
    private String text;

    @JsonProperty("like_count")
    private int likeCount;

    @JsonProperty("published_at")
    private String publishedAt;

    @JsonProperty("category_label")
    private String categoryLabel;

    @JsonProperty("category_confidence")
    private double categoryConfidence;

    @JsonProperty("sentiment")
    private String sentiment;

    @JsonProperty("sentiment_score")
    private double sentimentScore;
}
