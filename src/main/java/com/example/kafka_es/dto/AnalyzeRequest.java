package com.example.kafka_es.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class AnalyzeRequest {
    private String keyword;
    private List<String> urls;
}
