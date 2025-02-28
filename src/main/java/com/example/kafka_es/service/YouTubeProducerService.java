package com.example.kafka_es.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.kafka_es.config.KafkaProducerConfig;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;

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

    public void process(String searchQuery) {
        try {
            List<JsonNode> videos = searchVideos(searchQuery);
            for (JsonNode video : videos) {
                String videoId = video.get("videoId").asText();
                List<JsonNode> comments = fetchComments(videoId);
                for (JsonNode comment : comments) {
                    String json = objectMapper.writeValueAsString(comment);
                    sendToKafka(json);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private List<JsonNode> searchVideos(String query) {
        String url = "https://www.googleapis.com/youtube/v3/search?part=snippet&q=" + query + "&maxResults=3&type=video&key="+apiKey;
        JsonNode response = restTemplate.getForObject(url, JsonNode.class);
        return response.get("items").findValues("id");
    }

    private List<JsonNode> fetchComments(String videoId) {
        String url = "https://www.googleapis.com/youtube/v3/commentThreads?part=snippet&videoId=" + videoId + "&maxResults=5&key=" + apiKey;
        JsonNode response = restTemplate.getForObject(url, JsonNode.class);

        List<JsonNode> comments = new ArrayList<>();

        if (response != null && response.has("items")) {
            for (JsonNode item : response.get("items")) {
                if (item.has("snippet") && item.get("snippet").has("topLevelComment")) {
                    JsonNode commentSnippet = item.get("snippet").get("topLevelComment").get("snippet");


                    ObjectNode commentModel = objectMapper.createObjectNode();
                    commentModel.put("id", item.get("id").asText());
                    commentModel.put("video_id", videoId);
                    commentModel.put("reply", commentSnippet.get("textDisplay").asText().replace("\n", " "));
                    commentModel.put("like_count", commentSnippet.has("likeCount") ? commentSnippet.get("likeCount").asInt() : 0);
                    commentModel.put("published_at", commentSnippet.get("publishedAt").asText());

                    comments.add(commentModel);
                }
            }
        }

        return comments;
    }


    private void sendToKafka(String message) {
        kafkaTemplate.send(kafkaTopic, message);
       // System.out.println("Sent to Kafka: " + message);
    }
}
