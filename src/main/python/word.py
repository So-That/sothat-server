import json
from kafka import KafkaConsumer
from elasticsearch import Elasticsearch
from collections import defaultdict
from konlpy.tag import Okt
from datetime import datetime
from main import logger

# Elasticsearch 클라이언트 설정
es = Elasticsearch(["http://localhost:9200"])

# Kafka 소비자 설정
consumer = KafkaConsumer(
    "youtube_comment",
    bootstrap_servers=["localhost:9092"],
    auto_offset_reset="earliest",
    enable_auto_commit=True,
    group_id="word-consumer-group",
    value_deserializer=lambda x: json.loads(x.decode("utf-8"))
)

# 단어 빈도 누적을 위한 메모리 저장소
word_count = defaultdict(int)
okt = Okt()

def update_es(word_counts):
    """Elasticsearch에 빈도 데이터 업데이트"""
    for word, count in word_counts.items():
        try:
            # ES 문서 업데이트 (upsert)
            es.update(
                index="word",
                id=word,
                body={
                    "doc": {
                        "frequency": count,
                        "last_updated": datetime.utcnow()
                    },
                    "doc_as_upsert": True
                }
            )
            logger.info("Updated word: %s, frequency: %d", word, count)
        except Exception as e:
            logger.error("Failed to update Elasticsearch for word '%s': %s", word, e)

# Kafka 메시지 소비 및 처리
for message in consumer:
    data = message.value
    logger.info("Received message: %s", data)

    if "reply" in data:
        reply_text = data["reply"]
        logger.info("Processing reply field: %s", reply_text)

        # 명사 및 형용사 추출
        words = okt.nouns(reply_text) + [
            word for word, tag in okt.pos(reply_text) if tag == "Adjective"
        ]
        for word in words:
            if len(word) >= 2:  # 두 글자 이상만 처리
                word_count[word] += 1

        # ES로 업데이트
        update_es(word_count)
        word_count.clear()  # 매번 비워주어 다음 메시지 처리 준비
