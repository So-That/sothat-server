package com.example.kafka_es.controller;

import com.example.kafka_es.service.YouTubeProducerService;
import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/youtube")
@Tag(name = "YouTube API", description = "YouTube 데이터를 가져오는 API")
public class YouTubeController {

    private final YouTubeProducerService youTubeProducerService;

    public YouTubeController(YouTubeProducerService youTubeProducerService) {
        this.youTubeProducerService = youTubeProducerService;
    }

    @Operation(summary = "검색어로 동영상 정보 조회", description = "검색어를 이용하여 유튜브 동영상 정보를 가져옵니다.")
    @GetMapping("/search")
    public List<Map<String, Object>> searchVideos(@RequestParam String query) {
        return youTubeProducerService.searchMainVideos(query);
    }

    @Operation(summary = "선택된 비디오 댓글 분석", description = " 선택된 영상의 동영상 댓글 정보를 가져옵니다.")
    @GetMapping("/word")
    public List<JsonNode>searchCommentsById(@RequestParam List<String> videoIds) {
        return youTubeProducerService.fetchCommentByWord(videoIds);
    }
    @Operation(summary = "URL을 통해 댓글 분석", description = "유튜브 URL 리스트를 이용하여 동영상 댓글 정보를 가져옵니다.")
    @GetMapping("/url")
    public List<JsonNode>searchCommentsByUrl(@RequestParam List<String> urls) {
        return youTubeProducerService.fetchCommentByUrl(urls);
    }

}
