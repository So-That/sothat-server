from kafka import KafkaProducer
import json
import requests
import os


class YouTubeProducer:
    def __init__(self, kafka_topic, kafka_server, api_key):
        self.kafka_topic = kafka_topic
        self.api_key = api_key
        self.producer = KafkaProducer(
            bootstrap_servers=kafka_server,
            value_serializer=lambda v: json.dumps(v).encode('utf-8')
        )

    def search_videos(self, search_query, max_results=3):
        url = f'https://www.googleapis.com/youtube/v3/search?part=snippet&q={search_query}&maxResults={max_results}&type=video&key={self.api_key}'
        response = requests.get(url)

        if response.status_code != 200:
            raise Exception(f"Error: Received status code {response.status_code}", response.text)

        data = response.json()
        if 'items' not in data:
            raise Exception("Error: 'items' key not found in response.", data)

        return data['items']

    def fetch_comments(self, video_id, max_results=5):
        url = f'https://www.googleapis.com/youtube/v3/commentThreads?part=snippet&videoId={video_id}&maxResults={max_results}&key={self.api_key}'
        response = requests.get(url)

        if response.status_code != 200:
            raise Exception(f"Error: Failed to get comments for video ID {video_id}.", response.text)

        return response.json().get('items', [])

    def send_to_kafka(self, data):
        try:
            self.producer.send(self.kafka_topic, data)
            print(f"Sent to Kafka: {data}")
        except Exception as e:
            print(f"Error sending to Kafka: {e}")

    def process(self, search_query):
        try:
            videos = self.search_videos(search_query)

            for item in videos:
                if item['id']['kind'] != 'youtube#video':
                    continue

                video_id = item['id']['videoId']
                comments = self.fetch_comments(video_id)

                for comment in comments:
                    snippet = comment['snippet']['topLevelComment']['snippet']
                    comment_model = {
                        "id": comment['id'],
                        "video_id": video_id,
                        "reply": snippet['textDisplay'].replace('\n', ' '),
                        "like_count": int(snippet.get('likeCount', 0)),
                        "published_at": snippet['publishedAt']
                    }
                    self.send_to_kafka(comment_model)

        except Exception as e:
            print(f"Exception occurred: {e}")

    def close(self):
        self.producer.flush()
        self.producer.close()


if __name__ == "__main__":
    KAFKA_TOPIC = "youtube_comment"
    KAFKA_SERVER = "localhost:9092"
    API_KEY = os.getenv('API_KEY')

    search_query = input("Enter your search query: ")

    producer = YouTubeProducer(kafka_topic=KAFKA_TOPIC, kafka_server=KAFKA_SERVER, api_key=API_KEY)
    try:
        producer.process(search_query)
    finally:
        producer.close()
