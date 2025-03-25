package com.example.kafka_es.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.kafka_es.config.KafkaProducerConfig;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;

@Service
public class YouTubeProducerService {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final String kafkaTopic;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String apiKey = System.getenv("API_KEY");

    public YouTubeProducerService(KafkaTemplate<String, String> kafkaTemplate, KafkaProducerConfig kafkaProducerConfig) {
        this.kafkaTemplate = kafkaTemplate;
        this.kafkaTopic = kafkaProducerConfig.getTopicName();
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
    }

    // 검색어를 이용해 동영상을 검색하고, 댓글을 가져와 Kafka로 전송
    public void process(List<String> videoIds) {
        try {

            List<JsonNode> comments = fetchComments(videoIds);
            for (JsonNode comment : comments) {
                sendToKafka(objectMapper.writeValueAsString(comment));
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    //검색어로 동영상 ID 조회
    private List<String> searchVideos(String query) {
        String url = "https://www.googleapis.com/youtube/v3/search?part=snippet&q=" + query + "&maxResults=3&type=video&key=" + apiKey;
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
    /**
     *  검색어로 동영상 정보 조회
     */
    public List<Map<String, Object>> searchMainVideos(String query) {
        List<String> videoIds = searchVideos(query);
        List<Map<String, Object>> videoDetailsList = new ArrayList<>();

        for (String videoId : videoIds) {
            videoDetailsList.add(getVideoDetails(videoId));
        }
        return videoDetailsList;
    }

    /**
      *유튜브 URL에서 비디오 ID 추출
     */
    private String extractVideoId(String url) {
        if (url.contains("youtube.com/watch?v=")) {
            return url.split("v=")[1].split("&")[0];
        } else if (url.contains("youtu.be/")) {
            return url.split("youtu.be/")[1].split("\\?")[0];
        }
        return null;
    }

    /**
     * URL 리스트를 통해 동영상 정보 조회
     */
    public List<Map<String, Object>> searchMainVideosByUrl(List<String> urls) {
        List<Map<String, Object>> videoDetailsList = new ArrayList<>();

        for (String url : urls) {
            String videoId = extractVideoId(url);
            if (videoId != null) {
                videoDetailsList.add(getVideoDetails(videoId));
            }
        }
        return videoDetailsList;
    }

    /**
     *  YouTube 동영상 ID로 상세 정보 가져오기
     */
    private Map<String, Object> getVideoDetails(String videoId) {
        String videoDetailsUrl = "https://www.googleapis.com/youtube/v3/videos?part=snippet,statistics&id=" + videoId + "&key=" + apiKey;
        JsonNode videoDetailsResponse = restTemplate.getForObject(videoDetailsUrl, JsonNode.class);

        Map<String, Object> videoData = new HashMap<>();

        if (videoDetailsResponse != null && videoDetailsResponse.has("items")) {
            JsonNode item = videoDetailsResponse.get("items").get(0);
            JsonNode snippet = item.get("snippet");
            JsonNode statistics = item.get("statistics");

            String channelId = snippet.get("channelId").asText();
            videoData.put("videoId", videoId);
            videoData.put("title", snippet.get("title").asText());
            videoData.put("channelId", channelId);
            videoData.put("channelTitle", snippet.get("channelTitle").asText());
            videoData.put("publishedAt", snippet.get("publishedAt").asText());
            videoData.put("thumbnailUrl", snippet.get("thumbnails").get("high").get("url").asText());
            videoData.put("viewCount", statistics.get("viewCount").asText());
            videoData.put("likeCount", statistics.get("likeCount").asText());
            videoData.put("commentCount", statistics.get("commentCount").asText());

            // 채널 정보 가져오기 (구독자 수)
            videoData.putAll(getChannelDetails(channelId));
        }

        return videoData;
    }

    /**
     * 채널 ID로 구독자 수 가져오기
     */
    private Map<String, Object> getChannelDetails(String channelId) {
        String channelDetailsUrl = "https://www.googleapis.com/youtube/v3/channels?part=statistics&id=" + channelId + "&key=" + apiKey;
        JsonNode channelDetailsResponse = restTemplate.getForObject(channelDetailsUrl, JsonNode.class);

        Map<String, Object> channelStats = new HashMap<>();
        if (channelDetailsResponse != null && channelDetailsResponse.has("items")) {
            JsonNode channelData = channelDetailsResponse.get("items").get(0).get("statistics");
            channelStats.put("subscriberCount", channelData.get("subscriberCount").asText());
        }
        return channelStats;
    }

    /**
     * 비디오 ID 리스트로 댓글 가져오기
     */
    private List<JsonNode> fetchComments(List<String> videoIds) {
        List<JsonNode> comments = new ArrayList<>();

        for (String videoId : videoIds) {
            String url = "https://www.googleapis.com/youtube/v3/commentThreads?part=snippet&videoId=" + videoId + "&maxResults=5&key=" + apiKey;
            JsonNode response = restTemplate.getForObject(url, JsonNode.class);

            if (response != null && response.has("items")) {
                for (JsonNode item : response.get("items")) {
                    if (item.has("snippet") && item.get("snippet").has("topLevelComment")) {
                        JsonNode commentSnippet = item.get("snippet").get("topLevelComment").get("snippet");
                        comments.add(createCommentModel(item.get("id").asText(), videoId,commentSnippet));
                    }
                }
            }
        }
        return comments;
    }

    /**
     *  댓글 JSON 데이터 생성
     */
    private ObjectNode createCommentModel(String id, String videoId,JsonNode commentSnippet) {
        ObjectNode commentModel = objectMapper.createObjectNode();
        commentModel.put("id", id);
        commentModel.put("video_id", videoId);
       // commentModel.put("query", query);
        commentModel.put("reply", commentSnippet.get("textDisplay").asText().replace("\n", " "));
        commentModel.put("like_count", commentSnippet.has("likeCount") ? commentSnippet.get("likeCount").asInt() : 0);
        commentModel.put("published_at", commentSnippet.get("publishedAt").asText());
        return commentModel;
    }

    /**
     *  Kafka 전송
     */
    private void sendToKafka(String message) {
        kafkaTemplate.send(kafkaTopic, message);
    }
}
