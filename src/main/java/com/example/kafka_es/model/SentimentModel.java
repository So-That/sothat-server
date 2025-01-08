package com.example.kafka_es.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Document(indexName = "sentiment")
@Data
public class SentimentModel {
    @Id
    private String id;

    @Field(type = FieldType.Keyword, name = "sentiment")
    private String sentiment;

    @Field(type = FieldType.Double, name = "positive")
    private Double positive;

    @Field(type = FieldType.Double,name="negative")
    private Double negative;

    @Field(type = FieldType.Double,name="neutral")
    private Double neutral;

    @Field(type = FieldType.Double,name="mixed")
    private double mixed;


}
