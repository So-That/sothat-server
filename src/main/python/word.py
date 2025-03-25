import json
import re
import pandas as pd
from kafka import KafkaConsumer, KafkaProducer
from konlpy.tag import Okt

class TextProcessor:
    def __init__(self, stopwords_file):
        """텍스트 전처리 클래스 초기화"""
        self.okt = Okt()
        self.stopwords = self.load_stopwords(stopwords_file)

    def load_stopwords(self, file_path):
        """불용어 리스트 불러오기"""
        try:
            with open(file_path, 'r', encoding='utf-8-sig') as f:
                return set(f.read().split(","))
        except Exception as e:
            print(f"불용어 파일 로드 오류: {e}")
            return set()

    def extract_word(self, text):
        """한글만 남기기"""
        hangul = re.compile('[^ㄱ-ㅎㅏ-ㅣ가-힣 ]')
        return hangul.sub('', text)

    def process_text(self, text):
        """형태소 분석 (모든 단어 처리) & 불용어 제거"""
        words = self.okt.morphs(text, stem=True)
        words = [w for w in words if len(w) > 1 and w not in self.stopwords]
        return words

    def process_dataframe(self, df):
        """데이터프레임 전처리"""
        df['reply'] = df['reply'].apply(self.extract_word)
        df['reply'] = df['reply'].apply(self.process_text)
        return df

# Kafka 소비자 설정
consumer = KafkaConsumer(
    "RawComments",
    bootstrap_servers=["localhost:9092"],
    auto_offset_reset="earliest",
    enable_auto_commit=True,
    group_id="makeClean",
    value_deserializer=lambda x: json.loads(x.decode("utf-8"))
)

# Kafka 생산자 설정
producer = KafkaProducer(
    bootstrap_servers=["localhost:9092"],
    value_serializer=lambda x: json.dumps(x).encode("utf-8")
)

# 텍스트 전처리기 초기화
processor = TextProcessor('./stopwords.txt')

# Kafka로 전송하는 함수
def send_to_kafka(processed_words):
    """Kafka word 토픽으로 단어 데이터 전송"""
    try:
        producer.send("CleanedComments", value={"CleanedComments": processed_words})
    except Exception as e:
        print(f"Failed to send words to Kafka: {e}")

# Kafka 메시지 소비 및 처리
for message in consumer:
    data = message.value
    print(f"Received message: {data}")

    if "reply" in data:
        reply_text = data["reply"]

        # 데이터프레임으로 변환하여 처리
        df = pd.DataFrame({'reply': [reply_text]})
        processed_df = processor.process_dataframe(df)

        # Kafka로 전송 (리스트 형태)
        processed_words = processed_df['reply'].tolist()[0]
        print(processed_words)
        send_to_kafka(processed_words)
