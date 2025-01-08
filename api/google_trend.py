from pytrends.request import TrendReq
import pandas as pd
import json
from datetime import datetime
import time

pytrends = TrendReq(hl='ko', tz=540, retries=3, backoff_factor=0.1)

search_query = input("Enter your search query: ")
kw_list = [search_query]
timeframe = 'today 1-m'
geo = 'KR'

def fetch_trends_data(pytrends, kw_list, timeframe, geo):
    while True:
        try:
            pytrends.build_payload(kw_list, cat=0, timeframe=timeframe, geo=geo)
            interest_over_time_df = pytrends.interest_over_time()
            interest_by_region_df = pytrends.interest_by_region(resolution='COUNTRY', inc_low_vol=True, inc_geo_code=False)
            return interest_over_time_df, interest_by_region_df
        except Exception as e:
            if '429' in str(e):
                print("429 Error: Too many requests. Retrying after 60 seconds...")
                time.sleep(60)
            else:
                print(f"An error occurred: {e}")
                raise

def korean_region_to_iso(region):
    region_map = {
        "서울특별시": "KR-11", "부산광역시": "KR-26", "대구광역시": "KR-27",
        "인천광역시": "KR-28", "광주광역시": "KR-29", "대전광역시": "KR-30",
        "울산광역시": "KR-31", "세종특별자치시": "KR-50", "경기도": "KR-41",
        "강원도": "KR-42", "충청북도": "KR-43", "충청남도": "KR-44",
        "전라북도": "KR-45", "전라남도": "KR-46", "경상북도": "KR-47",
        "경상남도": "KR-48", "제주특별자치도": "KR-49"
    }
    return region_map.get(region, region)

interest_over_time_df, interest_by_region_df = fetch_trends_data(pytrends, kw_list, timeframe, geo)

result = {
    "interest_over_time_df": [],
    "interest_by_region_df": []
}

for index, row in interest_over_time_df.iterrows():
    action = {
        "index": {
            "_index": "google_trend",
            "_id": f"time{index.strftime('%Y%m%d%H%M%S')}"
        }
    }
    source = {
        "timestamp": index.strftime('%Y-%m-%dT%H:%M:%S'),
        "keyword": kw_list[0],
        "interest": int(row[kw_list[0]]),
        "type": "time_series"
    }
    result['interest_over_time_df'].append(action)
    result['interest_over_time_df'].append(source)

for index, row in interest_by_region_df.iterrows():
    iso_code = korean_region_to_iso(index)
    action = {
        "index": {
            "_index": "google_trend",
            "_id": f"region{iso_code}"
        }
    }
    source = {
        "timestamp": datetime.now().strftime('%Y-%m-%dT%H:%M:%S'),
        "keyword": kw_list[0],
        "region": iso_code,
        "interest": int(row[kw_list[0]]),
        "type": "region"
    }
    result['interest_by_region_df'].append(action)
    result['interest_by_region_df'].append(source)

print(json.dumps(result, ensure_ascii=False, indent=2))