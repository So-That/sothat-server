package com.example.kafka_es.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.kafka_es.config.KafkaProducerConfig;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Value;
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
    @Value("${youtube.api.key}")
    private String apiKey;

    public YouTubeProducerService(KafkaTemplate<String, String> kafkaTemplate, KafkaProducerConfig kafkaProducerConfig) {
        this.kafkaTemplate = kafkaTemplate;
        this.kafkaTopic = kafkaProducerConfig.getTopicName();
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
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
        String channelDetailsUrl = "https://www.googleapis.com/youtube/v3/channels?part=snippet,statistics&id=" + channelId + "&key=" + apiKey;
        JsonNode channelDetailsResponse = restTemplate.getForObject(channelDetailsUrl, JsonNode.class);

        Map<String, Object> channelStats = new HashMap<>();
        if (channelDetailsResponse != null && channelDetailsResponse.has("items")) {
            JsonNode channelItem = channelDetailsResponse.get("items").get(0);
            JsonNode statistics=channelItem.get("statistics");
            JsonNode snippet=channelItem.get("snippet");
            channelStats.put("subscriberCount", statistics.get("subscriberCount").asText());
            channelStats.put("profileImageUrl",snippet.get("thumbnails").get("high").get("url").asText());
        }
        return channelStats;
    }

    /**
     * 비디오 ID 리스트로 댓글 가져오기
     */
    private List<JsonNode> fetchComments(List<String> videoIds) {
        Set<String> seenCommentIds = new HashSet<>(); // 중복 방지
        List<JsonNode> comments = new ArrayList<>();

        int max_cnt=600;

        for (String videoId : videoIds) {
            String nextPageToken = "";
            int count = 0;

            while (nextPageToken != null && count < max_cnt) {
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
                                if (count >= max_cnt) break;
                            }

                            // ✅ 답글이 있으면 추가 수집
                            if (item.has("replies") && item.get("replies").has("comments")) {
                                for (JsonNode reply : item.get("replies").get("comments")) {
                                    String replyId = reply.get("id").asText();
                                    JsonNode replySnippet = reply.get("snippet");

                                    if (seenCommentIds.add(replyId)) {
                                        comments.add(createCommentModel(replyId, videoId, replySnippet));
                                        count++;
                                        if (count >= max_cnt) break;
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

            if (comments.size() >= max_cnt) break;
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


    /**
     * URL 리스트를 통해 동영상 댓글을 가져와 Kafka에 전송하고 JSON 반환
     */
    public List<JsonNode> fetchCommentByUrl(List<String> urls) {
        List<String> videoIds = new ArrayList<>();

        for (String url : urls) {
            String videoId = extractVideoId(url);
            if (videoId != null) {
                videoIds.add(videoId);
            }
        }
        return fetchAndSendComments(videoIds);
    }

    /**
     * 비디오 ID 리스트를 통해 동영상 댓글을 가져와 Kafka에 전송하고 JSON 반환
     */
    public List<JsonNode> fetchCommentByWord(List<String> videoIds) {
        return fetchAndSendComments(videoIds);
    }

    /**
     * 댓글을 가져와 Kafka에 전송하고 JSON 반환
     */
    private List<JsonNode> fetchAndSendComments(List<String> videoIds) {
        if (videoIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<JsonNode> comments = fetchComments(videoIds);
        for (JsonNode comment : comments) {
            sendToKafka(comment.toString());
        }
        return comments;
    }

}
