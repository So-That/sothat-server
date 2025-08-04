package com.example.kafka_es.repository;

import com.example.kafka_es.dto.AnalyzedCommentResponse;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AnalyzedCommentRepository extends MongoRepository<AnalyzedCommentResponse, String> {
    boolean existsByMetaInfo_VideoIdsIn(List<String> videoIds);
    List<AnalyzedCommentResponse> findByMetaInfo_VideoIdsIn(List<String> videoIds);

    boolean existsByMetaInfo_VideoIdsContaining(String videoId);
}

