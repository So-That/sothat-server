package com.example.kafka_es.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.bulk.UpdateOperation;
import co.elastic.clients.json.JsonData;
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
     * WordModel 데이터를 Elasticsearch "word" 인덱스에 저장하거나 frequency 증가.
     *
     * @param words 처리할 WordModel 리스트
     */
    public void updateToES(List<WordModel> words) {
        List<BulkOperation> bulkOperations = new ArrayList<>();

        for (WordModel word : words) {
            bulkOperations.add(
                    BulkOperation.of(b -> b
                            .update(UpdateOperation.of(u -> u
                                    .index("word")
                                    .id(word.getWord())  // 단어 자체를 ID로 사용
                                    .action(a -> a
                                            .script(s -> s
                                                    .source("ctx._source.frequency += params.increment")
                                                    .params("increment", JsonData.of(1))
                                                    .lang("painless")  // 스크립트 언어 설정
                                            )
                                            .upsert(word) // 문서가 없으면 새로 생성 (frequency = 1 포함)
                                    )
                            ))
                    )
            );
        }

        try {
            // BulkRequest 실행
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
