package com.example.kafka_es.repository;

import com.example.kafka_es.dto.AnalyzedCommentResponse;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AnalyzedCommentRepository extends MongoRepository<AnalyzedCommentResponse, String> {
}
