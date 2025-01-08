package com.example.kafka_es;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;

@EnableKafka
@SpringBootApplication
public class KafkaEsApplication {

    public static void main(String[] args) {
        SpringApplication.run(KafkaEsApplication.class, args);
    }
}

