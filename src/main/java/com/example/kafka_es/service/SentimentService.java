package com.example.kafka_es.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import com.example.kafka_es.model.SentimentModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SentimentService {

    private final ElasticsearchClient elasticsearchClient;


    public void saveSentimentsToES(List<SentimentModel> sentiments) {
        List<BulkOperation> bulkOperations = new ArrayList<>();

        // BulkOperation 리스트 생성
        for (SentimentModel sentiment : sentiments) {
            bulkOperations.add(
                    BulkOperation.of(b -> b
                            .index(idx -> idx
                                    .index("sentiment") // Elasticsearch 인덱스 이름
                                    .document(sentiment) // 저장할 문서
                            )
                    )
            );
        }

        try {
            // BulkRequest 생성 및 실행
            BulkRequest bulkRequest = new BulkRequest.Builder()
                    .operations(bulkOperations)
                    .build();

            BulkResponse bulkResponse = elasticsearchClient.bulk(bulkRequest);

            // Bulk 처리 결과 확인
            if (bulkResponse.errors()) {
                System.err.println("Bulk operation had errors: " + bulkResponse.toString());
            } else {
                System.out.println("Bulk operation completed successfully. Saved " + sentiments.size() + " documents.");
            }
        } catch (Exception e) {
            System.err.println("Failed to save sentiments to Elasticsearch: " + e.getMessage());
        }
    }
}
