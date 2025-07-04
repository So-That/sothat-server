package com.example.kafka_es.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import lombok.RequiredArgsConstructor;
import com.example.kafka_es.model.CommentModel;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final ElasticsearchClient elasticsearchClient;

    public void saveCommentsToES(List<CommentModel> comments) {
        List<BulkOperation> bulkOperations = new ArrayList<>();

        // 댓글 데이터를 BulkOperation으로 변환
        for (CommentModel comment : comments) {
            bulkOperations.add(
                    BulkOperation.of(b -> b
                            .index(idx -> idx
                                    .index("analyzed")
                                    .document(comment)
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

            // 실패 여부 확인
            if (bulkResponse.errors()) {
                System.err.println("Bulk operation had errors: " + bulkResponse.toString());
            } else {
                System.out.println("Bulk operation completed successfully.");
            }
        } catch (Exception e) {
            System.err.println("Failed to save comments to Elasticsearch: " + e.getMessage());
        }
    }
}
