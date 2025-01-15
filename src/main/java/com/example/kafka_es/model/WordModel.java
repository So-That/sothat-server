package com.example.kafka_es.model;

import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Document(indexName = "word")
@Getter
@Setter
@Data
public class WordModel {

    @Field(type = FieldType.Keyword,name="word")
    private String word;

    @Field(type = FieldType.Integer,name="frequency")
    private int frequency;

}
