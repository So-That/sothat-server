```python
import json
import pandas as pd
from konlpy.tag import Okt
import re
from datetime import datetime

class KnuSentimentAnalyzer:
    def __init__(self):
        self.okt = Okt()
        self.sentiment_dict = self._load_sentiment_dict()

    def _load_sentiment_dict(self):
        sentiment_dict = pd.read_csv('KNU_sentiment_lexicon.tsv', sep='\t', 
            names=['word', 'score'], encoding='utf-8')
        return dict(zip(sentiment_dict['word'], sentiment_dict['score']))

    def preprocess_text(self, text):
        text = re.sub(r'<[^>]+>', '', str(text))
        text = re.sub(r'http\S+', '', text)
        text = re.sub(r'[^\w\s]', ' ', text)
        text = re.sub(r'\s+', ' ', text)
        return text.strip()

    def analyze_sentiment(self, text):
        text = self.preprocess_text(text)
        words = self.okt.morphs(text)
        
        word_scores = []
        for word in words:
            score = self.sentiment_dict.get(word, 0)
            if score != 0:
                word_scores.append({
                    'word': word,
                    'score': score
                })
        
        total_score = sum(item['score'] for item in word_scores)
        
        if total_score > 0:
            sentiment = 'positive'
            confidence = min(total_score / 2, 1.0)
        elif total_score < 0:
            sentiment = 'negative'
            confidence = min(abs(total_score) / 2, 1.0)
        else:
            sentiment = 'neutral'
            confidence = 0.5
        
        return {
            'sentiment': sentiment,
            'confidence': round(confidence, 3),
            'score': round(total_score, 3),
            'sentiment_words': word_scores
        }

def process_youtube_data(input_file, output_file):
    analyzer = KnuSentimentAnalyzer()
    
    # 입력 JSON 파일 읽기
    with open(input_file, 'r', encoding='utf-8') as f:
        data = json.load(f)
    
    processed_data = []
    
    # 각 비디오 데이터 처리
    for video in data:
        video_data = {
            'video_id': video['id'],
            'title': video['title'],
            'published_at': video['published_at'],
            'stats': {
                'view_count': video.get('view_count', 0),
                'like_count': video.get('like_count', 0),
                'comment_count': len(video['comments'])
            },
            'comments_analysis': [],
            'sentiment_stats': {
                'positive': 0,
                'negative': 0,
                'neutral': 0
            }
        }
        
        # 댓글 분석
        for comment in video['comments']:
            sentiment_result = analyzer.analyze_sentiment(comment)
            
            # 통계 업데이트
            video_data['sentiment_stats'][sentiment_result['sentiment']] += 1
            
            # 댓글 분석 결과 저장
            comment_data = {
                'content': comment,
                'sentiment_analysis': sentiment_result,
                'analyzed_at': datetime.now().isoformat()
            }
            video_data['comments_analysis'].append(comment_data)
        
        processed_data.append(video_data)
    
    # 출력 JSON 파일 생성
    output_data = {
        'processed_at': datetime.now().isoformat(),
        'total_videos': len(processed_data),
        'videos': processed_data
    }
    
    with open(output_file, 'w', encoding='utf-8') as f:
        json.dump(output_data, f, ensure_ascii=False, indent=2)
    
    print(f"처리 완료: {output_file}에 결과가 저장되었습니다.")

if __name__ == "__main__":
    input_file = 'youtube_data.json'  # 입력 JSON 파일
    output_file = 'sentiment_analysis_results.json'  # 출력 JSON 파일
    
    process_youtube_data(input_file, output_file)
```