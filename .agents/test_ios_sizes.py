import urllib.request
import json

body = {
    'context': {
        'client': {
            'clientName': 'IOS',
            'clientVersion': '21.03.1',
            'deviceMake': 'Apple',
            'deviceModel': 'iPhone16,2',
            'userAgent': 'com.google.ios.youtube/21.03.1 (iPhone16,2; U; CPU iOS 18_2 like Mac OS X;)',
            'osName': 'iOS',
            'osVersion': '18.2.22C152',
            'hl': 'en',
            'gl': 'US',
            'utcOffsetMinutes': 0
        }
    },
    'videoId': 'hX9S94XsySk'
}
req = urllib.request.Request(
    'https://www.youtube.com/youtubei/v1/player?prettyPrint=false',
    data=json.dumps(body).encode('utf-8'),
    headers={
        'User-Agent': 'com.google.ios.youtube/21.03.1 (iPhone16,2; U; CPU iOS 18_2 like Mac OS X;)',
        'Content-Type': 'application/json',
        'X-YouTube-Client-Name': '5',
        'X-YouTube-Client-Version': '21.03.1'
    }
)
with urllib.request.urlopen(req) as resp:
    data = json.loads(resp.read().decode('utf-8'))

formats = data.get('streamingData', {}).get('adaptiveFormats', [])
audio140 = next(f for f in formats if f.get('itag') == 140)
url = audio140['url']
ua = 'com.google.ios.youtube/21.03.1 (iPhone16,2; U; CPU iOS 18_2 like Mac OS X;)'

# Test lengths from 1048576
for size in [1024, 4096, 8192, 16384, 32768, 49152, 65536]:
    start = 1048576
    end = start + size - 1
    r = f'bytes={start}-{end}'
    try:
        req_r = urllib.request.Request(url, headers={'User-Agent': ua, 'Range': r})
        with urllib.request.urlopen(req_r) as resp_r:
            print(f"Size {size} ({r}) -> {resp_r.status}")
    except Exception as e:
        print(f"Size {size} ({r}) -> {e}")

# Also test from 2000000
for size in [1024, 4096, 8192, 16384, 32768, 65536]:
    start = 2000000
    end = start + size - 1
    r = f'bytes={start}-{end}'
    try:
        req_r = urllib.request.Request(url, headers={'User-Agent': ua, 'Range': r})
        with urllib.request.urlopen(req_r) as resp_r:
            print(f"At 2MB, Size {size} ({r}) -> {resp_r.status}")
    except Exception as e:
        print(f"At 2MB, Size {size} ({r}) -> {e}")
