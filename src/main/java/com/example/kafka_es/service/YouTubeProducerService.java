package com.example.kafka_es.service;

import com.example.kafka_es.kafka.Topics;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class YouTubeProducerService {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final AnalyzedCommentService analyzedCommentService;

    // 토픽명
    private static final String RAW_TOPIC = Topics.RAW_COMMENTS;
    private static final String CONTROL_TOPIC = Topics.ANALYSIS_CONTROL;

    @Value("${youtube.api.key}")
    private String apiKey;

    // 1회 최대 수집 댓글
    private static final int MAX_CNT = 1000;

    public YouTubeProducerService(KafkaTemplate<String, String> kafkaTemplate,
                                  AnalyzedCommentService analyzedCommentService) {
        this.kafkaTemplate = kafkaTemplate;
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
        this.analyzedCommentService = analyzedCommentService;
    }

    // --------------------------------------------
    // 검색어로 동영상 ID 조회
    // --------------------------------------------
    private List<String> searchVideos(String query) {
        String url = "https://www.googleapis.com/youtube/v3/search?part=snippet&q=" + query
                + "&maxResults=10&type=video&key=" + apiKey;
        JsonNode response = restTemplate.getForObject(url, JsonNode.class);

        List<String> videoIds = new ArrayList<>();
        if (response != null && response.has("items")) {
            for (JsonNode item : response.get("items")) {
                if (item.has("id") && item.get("id").has("videoId")) {
                    videoIds.add(item.get("id").get("videoId").asText());
                }
            }
        }
        return videoIds;
    }

    // 검색어로 동영상 정보 조회
    public List<Map<String, Object>> searchMainVideos(String query) {
        List<String> videoIds = searchVideos(query);
        List<Map<String, Object>> videoDetailsList = new ArrayList<>();
        for (String videoId : videoIds) {
            videoDetailsList.add(getVideoDetails(videoId));
        }
        return videoDetailsList;
    }

    // --------------------------------------------
    // 유튜브 URL에서 비디오 ID 추출
    // --------------------------------------------
    private String extractVideoId(String url) {
        if (url == null) return null;
        if (url.contains("youtube.com/watch?v=")) {
            return url.split("v=")[1].split("&")[0];
        } else if (url.contains("youtu.be/")) {
            return url.split("youtu.be/")[1].split("\\?")[0];
        }
        return null;
    }

    // --------------------------------------------
    // 비디오 ID로 상세 정보 조회
    // --------------------------------------------
    private Map<String, Object> getVideoDetails(String videoId) {
        if (videoId == null || videoId.isBlank()) return Map.of();

        String url = UriComponentsBuilder
                .fromHttpUrl("https://www.googleapis.com/youtube/v3/videos")
                .queryParam("part", "snippet,statistics")
                .queryParam("id", videoId)
                .queryParam("key", apiKey)
                .toUriString();

        JsonNode res = restTemplate.getForObject(url, JsonNode.class);
        Map<String, Object> out = new HashMap<>();

        if (res == null || res.has("error")) return out;
        if (!res.has("items") || !res.get("items").isArray() || res.get("items").size() == 0) return out;

        JsonNode item = res.path("items").get(0);
        JsonNode snippet = item.path("snippet");
        JsonNode statistics = item.path("statistics");

        String channelId    = snippet.path("channelId").asText("");
        String title        = snippet.path("title").asText("");
        String channelTitle = snippet.path("channelTitle").asText("");
        String publishedAt  = snippet.path("publishedAt").asText("");

        String thumb = snippet.path("thumbnails").path("high").path("url").asText(
                snippet.path("thumbnails").path("medium").path("url").asText(
                        snippet.path("thumbnails").path("default").path("url").asText("")
                ));

        String viewCount    = statistics.path("viewCount").asText("0");
        String likeCount    = statistics.path("likeCount").asText("unknown");
        String commentCount = statistics.path("commentCount").asText("unknown");

        out.put("videoId", videoId);
        out.put("title", title);
        out.put("channelId", channelId);
        out.put("channelTitle", channelTitle);
        out.put("publishedAt", publishedAt);
        out.put("thumbnailUrl", thumb);
        out.put("viewCount", viewCount);
        out.put("likeCount", likeCount);
        out.put("commentCount", commentCount);

        if (!channelId.isBlank()) out.putAll(getChannelDetails(channelId));
        return out;
    }

    // 채널 ID로 구독자 수/프로필 이미지 조회
    private Map<String, Object> getChannelDetails(String channelId) {
        String channelDetailsUrl = "https://www.googleapis.com/youtube/v3/channels?part=snippet,statistics&id="
                + channelId + "&key=" + apiKey;
        JsonNode channelDetailsResponse = restTemplate.getForObject(channelDetailsUrl, JsonNode.class);

        Map<String, Object> channelStats = new HashMap<>();
        if (channelDetailsResponse != null && channelDetailsResponse.has("items")) {
            JsonNode channelItem = channelDetailsResponse.get("items").get(0);
            JsonNode statistics = channelItem.get("statistics");
            JsonNode snippet = channelItem.get("snippet");
            if (statistics != null && statistics.has("subscriberCount")) {
                channelStats.put("subscriberCount", statistics.get("subscriberCount").asText());
            }
            if (snippet != null && snippet.has("thumbnails")) {
                channelStats.put("profileImageUrl", snippet.get("thumbnails").get("high").get("url").asText());
            }
        }
        return channelStats;
    }

    // --------------------------------------------
    // 비디오 ID 리스트로 댓글 수집 (중복 제거)
    //   - 이미 요약 존재하는 videoId는 스킵
    // --------------------------------------------
    private List<JsonNode> fetchComments(List<String> videoIds) {
        Set<String> seenCommentIds = new HashSet<>();
        List<JsonNode> comments = new ArrayList<>();

        for (String videoId : videoIds) {
            // ✅ DynamoDB 존재 여부 확인
            if (analyzedCommentService.existsByVideoIdContaining(videoId)) {
                log.info("⏭️ 이미 DB에 요약 존재: videoId={} -> 댓글 수집 스킵", videoId);
                continue;
            }

            String nextPageToken = "";
            int count = 0;

            while (nextPageToken != null && count < MAX_CNT) {
                String url = String.format(
                        "https://www.googleapis.com/youtube/v3/commentThreads?part=snippet,replies&videoId=%s&maxResults=100&order=relevance&pageToken=%s&key=%s",
                        videoId, nextPageToken, apiKey
                );

                JsonNode response = restTemplate.getForObject(url, JsonNode.class);
                if (response != null && response.has("items")) {
                    for (JsonNode item : response.get("items")) {
                        if (item.has("snippet") && item.get("snippet").has("topLevelComment")) {
                            // 상위 댓글
                            JsonNode topComment = item.get("snippet").get("topLevelComment");
                            JsonNode commentSnippet = topComment.get("snippet");
                            String commentId = topComment.get("id").asText();

                            if (seenCommentIds.add(commentId)) {
                                comments.add(createCommentModel(commentId, videoId, commentSnippet));
                                count++;
                                if (count >= MAX_CNT) break;
                            }

                            // 답글
                            if (item.has("replies") && item.get("replies").has("comments")) {
                                for (JsonNode reply : item.get("replies").get("comments")) {
                                    String replyId = reply.get("id").asText();
                                    JsonNode replySnippet = reply.get("snippet");

                                    if (seenCommentIds.add(replyId)) {
                                        comments.add(createCommentModel(replyId, videoId, replySnippet));
                                        count++;
                                        if (count >= MAX_CNT) break;
                                    }
                                }
                            }
                        }
                    }
                }

                nextPageToken = (response != null && response.has("nextPageToken"))
                        ? response.get("nextPageToken").asText()
                        : null;
            }
        }
        return comments;
    }

    // 댓글 JSON 생성 (파이썬 컨슈머 스키마와 일치)
    private ObjectNode createCommentModel(String id, String videoId, JsonNode snippet) {
        ObjectNode commentModel = objectMapper.createObjectNode();
        commentModel.put("id", id);
        commentModel.put("video_id", videoId);
        commentModel.put("reply", snippet.path("textDisplay").asText("").replace("\n", " "));
        commentModel.put("like_count", snippet.has("likeCount") ? snippet.get("likeCount").asInt() : 0);
        commentModel.put("published_at", snippet.path("publishedAt").asText(""));
        return commentModel;
    }

    // --------------------------------------------
    // Kafka 전송 유틸
    // --------------------------------------------
    private void sendRaw(String videoId, String payloadJson) {
        kafkaTemplate.send(RAW_TOPIC, videoId, payloadJson);
    }

    private void sendControlStart(String videoId, int expectedCount) {
        Map<String, Object> evt = new HashMap<>();
        evt.put("event_type", "START");
        evt.put("video_id", videoId);
        evt.put("expected_count", expectedCount);
        kafkaTemplate.send(CONTROL_TOPIC, videoId, toJson(evt));
        log.info("🚦 START 전송: videoId={}, expectedCount={}", videoId, expectedCount);
    }

    private void sendControlEnd(String videoId) {
        Map<String, Object> evt = new HashMap<>();
        evt.put("event_type", "END");
        evt.put("video_id", videoId);
        kafkaTemplate.send(CONTROL_TOPIC, videoId, toJson(evt));
        log.info("🏁 END 전송: videoId={}", videoId);
    }

    private String toJson(Object o) {
        try { return objectMapper.writeValueAsString(o); }
        catch (Exception e) { return "{}"; }
    }

    // --------------------------------------------
    // 공개 API
    // --------------------------------------------

    // URL 리스트 → 댓글 가져와 Kafka 전송
    public List<JsonNode> fetchCommentByUrl(List<String> urls) {
        List<String> videoIds = new ArrayList<>();
        for (String url : urls) {
            String videoId = extractVideoId(url);
            if (videoId != null) videoIds.add(videoId);
        }
        return fetchAndSendComments(videoIds);
    }

    // 비디오 ID 리스트 → 댓글 가져와 Kafka 전송
    public List<JsonNode> fetchCommentByWord(List<String> videoIds) {
        return fetchAndSendComments(videoIds);
    }

    // 핵심 로직
    private List<JsonNode> fetchAndSendComments(List<String> videoIds) {
        if (videoIds == null || videoIds.isEmpty()) return Collections.emptyList();

        // ✅ DB에 없는 것만 댓글 수집
        List<JsonNode> comments = fetchComments(videoIds);

        // videoId별 그룹핑
        Map<String, List<JsonNode>> byVideo = comments.stream()
                .filter(c -> c.has("video_id"))
                .collect(Collectors.groupingBy(c -> c.get("video_id").asText(), LinkedHashMap::new, Collectors.toList()));

        // 요청된 videoId 순서에 맞춰 전송
        for (String videoId : videoIds) {
            List<JsonNode> list = byVideo.getOrDefault(videoId, List.of());

            // START
            sendControlStart(videoId, list.size());

            // RawComments 전송
            int sent = 0;
            for (JsonNode c : list) {
                sendRaw(videoId, c.toString());
                sent++;
            }
            log.info("📤 Raw 전송 완료: videoId={}, count={}", videoId, sent);

            // END
            sendControlEnd(videoId);
        }
        return comments;
    }
}
