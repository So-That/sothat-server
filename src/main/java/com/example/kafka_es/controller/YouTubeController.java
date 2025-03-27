package com.example.kafka_es.controller;

import com.example.kafka_es.service.YouTubeProducerService;
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

    @Operation(summary = "URL을 통해 동영상 정보 조회", description = "유튜브 URL 리스트를 이용하여 동영상 정보를 가져옵니다.")
    @GetMapping("/searchByUrl")
    public List<Map<String, Object>> searchVideosByUrl(@RequestParam List<String> urls) {
        return youTubeProducerService.searchMainVideosByUrl(urls);
    }

    
}
