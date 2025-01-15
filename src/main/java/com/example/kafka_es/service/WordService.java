package com.example.kafka_es.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.bulk.UpdateOperation;
import com.example.kafka_es.model.WordModel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class WordService {

    private final ElasticsearchClient elasticsearchClient;

    /**
     * WordModel 데이터를 Elasticsearch의 "word" 인덱스에 저장하거나 frequency 증가.
     *
     * @param words 처리할 WordModel 리스트
     */
    public void updateToES(List<WordModel> words) {
        List<BulkOperation> bulkOperations = new ArrayList<>();

        // WordModel 데이터를 BulkOperation으로 변환
        for (WordModel word : words) {
            bulkOperations.add(
                    BulkOperation.of(b -> b
                            .update(UpdateOperation.of(u -> u
                                    .index("word")
                                    .id(word.getWord()) // 단어를 문서 ID로 사용
                                    .action(a -> a
                                            .script(s -> s
                                                    .source("if (ctx._source.frequency != null) { ctx._source.frequency += 1; } else { ctx._source.frequency = 1; }")
                                            ) // 기존 frequency 값 증가
                                            .upsert(word) // 문서가 없으면 새로 생성
                                    )
                            ))
                    )
            );
        }

        try {
            // BulkRequest 생성 및 실행
            BulkRequest bulkRequest = new BulkRequest.Builder()
                    .operations(bulkOperations)
                    .build();

            BulkResponse bulkResponse = elasticsearchClient.bulk(bulkRequest);

            // 실패 여부 확인
            if (bulkResponse.errors()) {
                System.err.println("Bulk operation had errors: " + bulkResponse.toString());
            } else {
                System.out.println("Bulk operation completed successfully.");
            }
        } catch (Exception e) {
            System.err.println("Failed to update words in Elasticsearch: " + e.getMessage());
        }
    }
}
