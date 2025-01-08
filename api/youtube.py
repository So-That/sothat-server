import json
import requests
from dotenv import load_dotenv
import os

load_dotenv()


API_KEY = os.getenv("API_KEY")
search_query = input("Enter your search query: ")
URL = f'https://www.googleapis.com/youtube/v3/search?part=snippet&q={search_query}&maxResults=5&type=video&key={API_KEY}'

response = requests.get(URL)

# 응답 상태 코드 확인
if response.status_code != 200:
    print(f"Error: Received status code {response.status_code}")
    print(response.text)
else:
    data = response.json()

    # 'items' 키가 있는지 확인
    if 'items' not in data:
        print("Error: 'items' key not found in response.")
        print(data)
    else:
        result_list = []

        for item in data['items']:
            # 'videoId'가 없는 경우를 무시
            if item['id']['kind'] != 'youtube#video':
                continue

            video_id = item['id']['videoId']
            video_url = f'https://www.googleapis.com/youtube/v3/videos?part=snippet,statistics&id={video_id}&key={API_KEY}'
            video_response = requests.get(video_url)

            if video_response.status_code != 200:
                print(f"Error: Failed to get video details for video ID {video_id}.")
                continue

            video_data = video_response.json()
            if 'items' not in video_data or len(video_data['items']) == 0:
                print(f"Error: No details found for video ID {video_id}.")
                continue

            video_info = video_data['items'][0]

            title = video_info['snippet']['title']
            channel_title = video_info['snippet']['channelTitle']
            published_at = video_info['snippet']['publishedAt']
            description = video_info['snippet']['description']
            like_count = int(video_info['statistics'].get('likeCount', 0))
            dislike_count = video_info['statistics'].get('dislikeCount', 'N/A')
            view_count = int(video_info['statistics'].get('viewCount', 0))
            comment_count = int(video_info['statistics'].get('commentCount', 0))

            # Fetch comments
            comments = []
            try:
                comments_url = f'https://www.googleapis.com/youtube/v3/commentThreads?part=snippet&videoId={video_id}&maxResults=100&key={API_KEY}'
                comments_response = requests.get(comments_url)
                if comments_response.status_code == 200:
                    comments_data = comments_response.json()
                    comments = [
                        comment['snippet']['topLevelComment']['snippet']['textDisplay'].replace('\n', ' ')
                        for comment in comments_data.get('items', [])
                    ]
                else:
                    print(f"Error: Failed to get comments for video ID {video_id}.")
            except Exception as e:
                print(f"Exception occurred while fetching comments for video ID {video_id}: {e}")

            video_result = {
                "id": video_id,
                "title": title,
                "channel_title": channel_title,
                "published_at": published_at,
                "description": description,
                "like_count": like_count,
                "comments": comments,
                "view_count": view_count,
                "comment_count": comment_count
            }

            result_list.append(video_result)

        # Convert result list to JSON and print or save
        result_json = json.dumps(result_list, ensure_ascii=False, indent=4)
        print(result_json)
