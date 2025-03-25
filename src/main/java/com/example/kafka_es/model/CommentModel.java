package com.example.kafka_es.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

@Document(indexName = "youtube_comment")
@Data
public class CommentModel {


    @Field(type = FieldType.Keyword,name="video_id")
    private String video_id;

    /*
    @Field(type = FieldType.Keyword,name="query")
    private String query;
*/
    @Field(type = FieldType.Text,name="reply")
    private String reply;

    @Field(type = FieldType.Integer,name="like_count")
    private int like_count;

    @Field(type = FieldType.Date,name="published_at")
    private String published_at;

}
