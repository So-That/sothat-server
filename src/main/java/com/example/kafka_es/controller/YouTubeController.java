package com.example.kafka_es.controller;

import com.example.kafka_es.service.YouTubeProducerService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/youtubeSearch")
public class YouTubeController {

    private final YouTubeProducerService youTubeProducer;

    public YouTubeController(YouTubeProducerService youTubeProducer) {
        this.youTubeProducer = youTubeProducer;
    }

    @GetMapping("/comments")
    public String searchAndSendToKafka() {

        List<String>videoIds=new ArrayList<>();
        videoIds.add("tYmNlVdwVIw");
        youTubeProducer.process(videoIds);
        return "Processing videoComment : " + videoIds;
    }

    @GetMapping("/words")
    public String searchVideoByWords(){

        String query="애플";
        System.out.println(youTubeProducer.searchMainVideos(query));

        return "Processing query: " + query;

    }

    @GetMapping("/urls")
    public String searchVideoByUrls(){
        List<String> urls = new ArrayList<>();
        urls.add("https://www.youtube.com/watch?v=tYmNlVdwVIw");
        System.out.println(youTubeProducer.searchMainVideosByUrl(urls));

        return "Processing urls: " + urls;
    }

}
