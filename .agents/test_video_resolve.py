import urllib.request
import json

clients = [
    ('IOS', {
        'clientName': 'IOS', 'clientVersion': '21.03.1', 'deviceMake': 'Apple', 'deviceModel': 'iPhone16,2',
        'userAgent': 'com.google.ios.youtube/21.03.1 (iPhone16,2; U; CPU iOS 18_2 like Mac OS X;)',
        'osName': 'iOS', 'osVersion': '18.2.22C152'
    }, '5', '21.03.1'),
    ('IPADOS', {
        'clientName': 'IOS', 'clientVersion': '21.03.1', 'deviceMake': 'Apple', 'deviceModel': 'iPad14,1',
        'userAgent': 'com.google.ios.youtube/21.03.1 (iPad14,1; U; CPU OS 18_2 like Mac OS X;)',
        'osName': 'iOS', 'osVersion': '18.2.22C152'
    }, '5', '21.03.1'),
    ('VISIONOS', {
        'clientName': 'VISIONOS', 'clientVersion': '1.02', 'deviceMake': 'Apple', 'deviceModel': 'RealityDevice14,1',
        'userAgent': 'com.google.visionos.youtube/1.02 (RealityDevice14,1; U; CPU OS 2_0 like Mac OS X;)',
        'osName': 'visionOS', 'osVersion': '2.0.0'
    }, '107', '1.02')
]

videoId = 'hX9S94XsySk'
print(f"Resolving video stream for {videoId}")

for name, ctx, cnum, cver in clients:
    print(f"\n--- Trying client {name} ---")
    body = {
        'context': {'client': ctx},
        'videoId': videoId
    }
    req = urllib.request.Request(
        'https://www.youtube.com/youtubei/v1/player?prettyPrint=false',
        data=json.dumps(body).encode('utf-8'),
        headers={
            'User-Agent': ctx['userAgent'],
            'Content-Type': 'application/json',
            'X-YouTube-Client-Name': cnum,
            'X-YouTube-Client-Version': cver
        }
    )
    try:
        with urllib.request.urlopen(req) as resp:
            data = json.loads(resp.read().decode('utf-8'))
        formats = data.get('streamingData', {}).get('adaptiveFormats', [])
        video_formats = [f for f in formats if f.get('height') or 'video' in f.get('mimeType', '')]
        print(f"Video formats count: {len(video_formats)}")
        if not video_formats:
            print("No video formats, continue")
            continue
        # Sort/rank video formats
        target = next((f for f in video_formats if f.get('height') == 720), video_formats[0])
        print(f"Picked candidate: itag={target.get('itag')} height={target.get('height')}")
        url = target.get('url')
        if not url:
            print("No URL on format!")
            continue
        
        # Test the probe used in StreamResolver.verifyStreamUrl:
        # Range: bytes=1048576-1179647 (AUTH_BOUNDARY_BYTES probe)
        req_probe = urllib.request.Request(
            url,
            headers={
                'User-Agent': ctx['userAgent'],
                'Range': 'bytes=1048576-1179647'
            }
        )
        try:
            with urllib.request.urlopen(req_probe) as rp:
                print(f"1 MiB Range Probe -> HTTP {rp.status}")
                if rp.status in [200, 206]:
                    print("PROBE PASSED! Video stream resolved successfully!")
                    break
        except Exception as pe:
            print(f"1 MiB Range Probe -> FAILED: {pe}")
    except Exception as e:
        print(f"Player request failed: {e}")
