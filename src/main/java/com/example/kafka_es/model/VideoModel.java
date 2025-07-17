package com.example.kafka_es.model;

import lombok.Data;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;


@Document(indexName = "youtube_video")
@Data
public class VideoModel {


            @Field(type = FieldType.Keyword,name="video_id")
            private String video_id;

            @Field(type = FieldType.Keyword,name="video_title")
            private String video_title;

            @Field(type = FieldType.Keyword,name="channelTitle")
            private String channelTitle;

            @Field(type = FieldType.Keyword,name="thumbnailUrl")
            private String thumbnailUrl;

            @Field(type = FieldType.Integer,name="view_count")
            private int view_count;

            @Field(type = FieldType.Integer,name="comment_count")
            private int comment_count;

            @Field(type = FieldType.Integer,name="subscriber_count")
            private int subscriber_count;

            @Field(type = FieldType.Integer,name="like_count")
                private int like_count;

            @Field(type = FieldType.Date,name="published_at")
            private String published_at;

}
