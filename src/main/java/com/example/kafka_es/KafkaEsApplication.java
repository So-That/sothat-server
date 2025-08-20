package com.example.kafka_es;

import com.example.kafka_es.service.YouTubeProducerService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

import java.util.Scanner;

@EnableKafka
@SpringBootApplication
public class KafkaEsApplication {

    public static void main(String[] args) {
        SpringApplication.run(KafkaEsApplication.class, args);
    }
}

