package com.example.kafka_es.controller;

import com.example.kafka_es.service.YouTubeProducerService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Scanner;

@RestController
@RequestMapping("/youtubeSearch")
public class YouTubeController {

    private final YouTubeProducerService youTubeProducer;

    public YouTubeController(YouTubeProducerService youTubeProducer) {
        this.youTubeProducer = youTubeProducer;
    }

    @GetMapping
    public String searchAndSendToKafka() {

        System.out.println("짜잔");
        Scanner sc=new Scanner(System.in);
        String query=sc.nextLine();
        youTubeProducer.process(query);
        return "Processing query: " + query;
    }

}
