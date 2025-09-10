package com.example.kafka_es.service;

import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class DoneWaiter {
    private final ConcurrentHashMap<String, CompletableFuture<Boolean>> futures = new ConcurrentHashMap<>();

    /** 컨트롤러에서 대기용 Future 획득 */
    public CompletableFuture<Boolean> waitFor(String videoId) {
        return futures.computeIfAbsent(videoId, k -> new CompletableFuture<>());
    }

    /** DONE 수신 시 완료 신호 */
    public void signal(String videoId) {
        futures.computeIfPresent(videoId, (k, f) -> {
            if (!f.isDone()) f.complete(true);
            return f;
        });
    }
}
