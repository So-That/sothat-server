package com.example.kafka_es.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Document(indexName = "analyzed")
@Data
public class CommentModel {

    @JsonProperty("video_id")
    @Field(type = FieldType.Keyword, name = "video_id")
    private String videoId;

    @JsonProperty("text")
    @Field(type = FieldType.Text, name = "text")
    private String text;

    @JsonProperty("binary_label")
    @Field(type = FieldType.Integer, name = "binary_label")
    private int binaryLabel;

    @JsonProperty("like_count")
    @Field(type = FieldType.Integer, name = "like_count")
    private int likeCount;

    @JsonProperty("published_at")
    @Field(type = FieldType.Date, name = "published_at")
    private String publishedAt;

    @JsonProperty("category_label")
    @Field(type = FieldType.Keyword, name = "category_label")
    private String categoryLabel;

    @JsonProperty("category_confidence")
    @Field(type = FieldType.Double, name = "category_confidence")
    private double categoryConfidence;

    @JsonProperty("sentiment")
    @Field(type = FieldType.Keyword, name = "sentiment")
    private String sentiment;

    @JsonProperty("sentiment_score")
    @Field(type = FieldType.Double, name = "sentiment_score")
    private double sentimentScore;
}
