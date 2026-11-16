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

# Binary search maximum end byte starting from 0
# Let's test single bytes: bytes=X-X
low = 1000000
high = 1200000
last_good = low

for test_byte in [1048576, 1050000, 1060000, 1070000, 1080000, 1100000, 1075000, 1073741, 1048576 + 25000]:
    r = f'bytes={test_byte}-{test_byte}'
    try:
        req_r = urllib.request.Request(url, headers={'User-Agent': ua, 'Range': r})
        with urllib.request.urlopen(req_r) as resp_r:
            print(f"Byte {test_byte} -> {resp_r.status}")
    except Exception as e:
        print(f"Byte {test_byte} -> {e}")
