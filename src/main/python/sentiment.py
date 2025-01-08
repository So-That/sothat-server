import boto3
import logging
import json
from botocore.exceptions import ClientError
from kafka import KafkaConsumer, KafkaProducer

# Logging 설정
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)


class ComprehendDetect:
    """Encapsulates Comprehend detection functions."""

    def __init__(self, comprehend_client):
        """
        :param comprehend_client: A Boto3 Comprehend client.
        """
        self.comprehend_client = comprehend_client

    def detect_sentiment(self, text, language_code):
        """
        Detects the overall sentiment expressed in a document.
        :param text: The document to inspect.
        :param language_code: The language of the document.
        :return: The sentiments along with their confidence scores.
        """
        try:
            response = self.comprehend_client.detect_sentiment(
                Text=text, LanguageCode=language_code
            )
            logger.info("Detected primary sentiment: %s", response["Sentiment"])
        except ClientError as error:
            logger.exception("Couldn't detect sentiment.")
            raise error
        else:
            return response


def consume_and_process_kafka_data(input_topic, output_topic, bootstrap_servers, comprehend_client):
    consumer = KafkaConsumer(
        input_topic,
        bootstrap_servers=bootstrap_servers,
        auto_offset_reset='earliest',
        enable_auto_commit=True,
        group_id='comprehend-consumer-group',
        value_deserializer=lambda x: json.loads(x.decode('utf-8'))  # JSON 메시지 디코딩
    )

    producer = KafkaProducer(
        bootstrap_servers=bootstrap_servers,
        value_serializer=lambda x: json.dumps(x).encode('utf-8')  # JSON 메시지 인코딩
    )

    comprehend_detector = ComprehendDetect(comprehend_client)

    logger.info("Listening to Kafka topic: %s", input_topic)

    for message in consumer:
        data = message.value  # JSON 메시지
        logger.info("Received message: %s", data)

        if "reply" in data:  # reply 필드가 있는지 확인
            reply_text = data["reply"]
            logger.info("Processing reply field: %s", reply_text)

            try:
                # Process the `reply` field using AWS Comprehend
                sentiment_result = comprehend_client.detect_sentiment(
                    Text=reply_text,
                    LanguageCode="ko"
                )

                result = {
                    "sentiment_analysis": {
                        "Sentiment": sentiment_result["Sentiment"],  # Sentiment 값 추가
                        "SentimentScore": sentiment_result["SentimentScore"]
                    }
                }

                # Kafka에 결과 전송
                producer.send(output_topic, value=result)
                logger.info("Sentiment analysis result sent to topic '%s': %s", output_topic, result)
            except Exception as e:
                logger.error("Error processing reply field: %s", e)
        else:
            logger.warning("Message does not contain a 'reply' field: %s", data)

if __name__ == "__main__":
    # Kafka 설정
    input_topic = "youtube_comment"
    output_topic = "sentiment"
    bootstrap_servers = ["localhost:9092"]  # Kafka 브로커 주소

    # AWS Comprehend 클라이언트 설정
    comprehend_client = boto3.client(
        "comprehend",
        region_name="ap-northeast-2"  # 적절한 리전으로 변경
    )

    # Kafka 데이터를 소비하고 처리
    consume_and_process_kafka_data(input_topic, output_topic, bootstrap_servers, comprehend_client)
