from transformers import pipeline
import torch
from elasticsearch import Elasticsearch, helpers
import json
from datetime import datetime
import re

class YouTubeDataProcessor:
    def __init__(self):
        # 감성분석 모델 초기화
        self.sentiment_analyzer = pipeline(
            "sentiment-analysis",
            model="beomi/KcELECTRA-base-v2022",
            tokenizer="beomi/KcELECTRA-base-v2022"
        )
        
        # Elasticsearch 클라이언트 초기화
        self.es = Elasticsearch("http://localhost:9200")
        
    def clean_text(self, text):
        """텍스트 전처리"""
        # HTML 태그 제거
        text = re.sub(r'<[^>]+>', '', text)
        # URL 제거
        text = re.sub(r'http\S+', '', text)
        # 중복 공백 제거
        text = re.sub(r'\s+', ' ', text)
        # 특수문자 제거 (이모지 등 포함)
        text = re.sub(r'[^\w\s]', '', text)
        return text.strip()

    def analyze_sentiment(self, text):
        """감성분석 수행"""
        try:
            result = self.sentiment_analyzer(text)[0]
            # 결과를 정규화된 형태로 변환
            return {
                "label": result["label"],
                "score": float(result["score"]),
                "processed_at": datetime.now().isoformat()
            }
        except Exception as e:
            print(f"감성분석 오류: {str(e)}")
            return {
                "label": "NEUTRAL",
                "score": 0.5,
                "processed_at": datetime.now().isoformat()
            }

    def process_video_data(self, video_data):
        """비디오 데이터 처리"""
        processed_videos = []
        
        for video in video_data:
            processed_video = {
                "video_id": video["id"],
                "video_title": self.clean_text(video["title"]),
                "published_at": video["published_at"],
                "view_count": video.get("view_count", 0),
                "like_count": video.get("like_count", 0),
                "comment_count": len(video["comments"]),
                "comments": [],
                "overall_sentiment": {
                    "positive": 0,
                    "negative": 0,
                    "neutral": 0
                }
            }
            
            # 댓글 처리
            for comment in video["comments"]:
                cleaned_content = self.clean_text(comment)
                if not cleaned_content:
                    continue
                
                sentiment = self.analyze_sentiment(cleaned_content)
                
                processed_comment = {
                    "content": cleaned_content,
                    "sentiment": sentiment,
                    "video_id": video["id"],
                    "processed_at": datetime.now().isoformat()
                }
                
                # 전체 감정 통계 업데이트
                processed_video["overall_sentiment"][sentiment["label"].lower()] += 1
                processed_video["comments"].append(processed_comment)
            
            processed_videos.append(processed_video)
            
        return processed_videos

    def prepare_elasticsearch_data(self, processed_data):
        """Elasticsearch 벌크 데이터 준비"""
        bulk_data = []
        
        for video in processed_data:
            # 비디오 문서
            video_doc = {
                "_index": "youtube_videos",
                "_id": video["video_id"],
                "_source": {
                    "video_id": video["video_id"],
                    "title": video["video_title"],
                    "published_at": video["published_at"],
                    "stats": {
                        "view_count": video["view_count"],
                        "like_count": video["like_count"],
                        "comment_count": video["comment_count"]
                    },
                    "sentiment_stats": video["overall_sentiment"]
                }
            }
            bulk_data.append(video_doc)
            
            # 댓글 문서들
            for comment in video["comments"]:
                comment_doc = {
                    "_index": "youtube_comments",
                    "_id": f"{video['video_id']}_{hash(comment['content'])}",
                    "_source": {
                        "video_id": video["video_id"],
                        "content": comment["content"],
                        "sentiment": comment["sentiment"],
                        "processed_at": comment["processed_at"]
                    }
                }
                bulk_data.append(comment_doc)
        
        return bulk_data

    def index_to_elasticsearch(self, bulk_data):
        """Elasticsearch에 데이터 적재"""
        try:
            helpers.bulk(self.es, bulk_data)
            print(f"성공적으로 {len(bulk_data)}개의 문서를 인덱싱했습니다.")
        except Exception as e:
            print(f"Elasticsearch 인덱싱 오류: {str(e)}")

# 사용 예시
if __name__ == "__main__":
    processor = YouTubeDataProcessor()
    
    # 데이터 파일 읽기
    with open('youtube_data.json', 'r', encoding='utf-8') as f:
        raw_data = json.load(f)
    
    # 데이터 처리
    processed_data = processor.process_video_data(raw_data)
    
    # Elasticsearch 데이터 준비
    bulk_data = processor.prepare_elasticsearch_data(processed_data)
    
    # Elasticsearch에 인덱싱
    processor.index_to_elasticsearch(bulk_data)