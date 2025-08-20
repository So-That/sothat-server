package com.example.kafka_es.model;

import lombok.Data;


@Data
public class VideoModel {


            private String video_id;

            private String video_title;

            private String channelTitle;

            private String thumbnailUrl;

            private int view_count;

            private int comment_count;

            private int subscriber_count;

            private int like_count;

            private String published_at;

}
