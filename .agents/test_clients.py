import urllib.request
import json

clients = [
    ('IOS', {
        'clientName': 'IOS', 'clientVersion': '21.03.1', 'deviceMake': 'Apple', 'deviceModel': 'iPhone16,2',
        'userAgent': 'com.google.ios.youtube/21.03.1 (iPhone16,2; U; CPU iOS 18_2 like Mac OS X;)',
        'osName': 'iOS', 'osVersion': '18.2.22C152'
    }, '5', '21.03.1', None),
    ('IPADOS', {
        'clientName': 'IOS', 'clientVersion': '21.03.1', 'deviceMake': 'Apple', 'deviceModel': 'iPad14,1',
        'userAgent': 'com.google.ios.youtube/21.03.1 (iPad14,1; U; CPU OS 18_2 like Mac OS X;)',
        'osName': 'iOS', 'osVersion': '18.2.22C152'
    }, '5', '21.03.1', None),
    ('VISIONOS', {
        'clientName': 'VISIONOS', 'clientVersion': '1.02', 'deviceMake': 'Apple', 'deviceModel': 'RealityDevice14,1',
        'userAgent': 'com.google.visionos.youtube/1.02 (RealityDevice14,1; U; CPU OS 2_0 like Mac OS X;)',
        'osName': 'visionOS', 'osVersion': '2.0.0'
    }, '107', '1.02', None),
    ('ANDROID_VR', {
        'clientName': 'ANDROID_VR', 'clientVersion': '1.65.10', 'deviceMake': 'Oculus', 'deviceModel': 'Quest 3',
        'userAgent': 'com.google.android.apps.youtube.vr.oculus/1.65.10 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip',
        'osName': 'Android', 'osVersion': '12L'
    }, '55', '1.65.10', None),
    ('WEB_REMIX', {
        'clientName': 'WEB_REMIX', 'clientVersion': '1.20260707.12.00',
        'userAgent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36'
    }, '67', '1.20260707.12.00', 'https://music.youtube.com'),
    ('WEB', {
        'clientName': 'WEB', 'clientVersion': '2.20260708.00.00',
        'userAgent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/141.0.0.0 Safari/537.36'
    }, '1', '2.20260708.00.00', 'https://www.youtube.com'),
    ('TVHTML5', {
        'clientName': 'TVHTML5', 'clientVersion': '7.20260707.07.00',
        'userAgent': 'Mozilla/5.0(SMART-TV; Linux; Tizen 4.0.0.2) AppleWebkit/605.1.15 (KHTML, like Gecko) SamsungBrowser/9.2 TV Safari/605.1.15'
    }, '85', '7.20260707.07.00', 'https://www.youtube.com')
]

for name, ctx, cnum, cver, origin in clients:
    print(f'=== Testing {name} ===')
    body = {
        'context': {'client': ctx},
        'videoId': 'hX9S94XsySk'
    }
    hdrs = {
        'User-Agent': ctx.get('userAgent', ''),
        'Content-Type': 'application/json',
        'X-YouTube-Client-Name': cnum,
        'X-YouTube-Client-Version': cver
    }
    if origin:
        hdrs['Origin'] = origin
        hdrs['Referer'] = origin + '/'
    try:
        req = urllib.request.Request('https://www.youtube.com/youtubei/v1/player?prettyPrint=false', data=json.dumps(body).encode('utf-8'), headers=hdrs)
        with urllib.request.urlopen(req) as resp:
            data = json.loads(resp.read().decode('utf-8'))
        playability = data.get('playabilityStatus', {})
        print(f"  Status: {playability.get('status')} reason: {playability.get('reason')}")
        formats = data.get('streamingData', {}).get('adaptiveFormats', [])
        audio = [f for f in formats if f.get('mimeType', '').startswith('audio/')]
        print(f"  Audio formats found: {len(audio)}")
        for a in audio[:2]:
            url = a.get('url')
            if url:
                print(f"    itag={a.get('itag')} has direct url!")
                media_hdrs = {'User-Agent': ctx['userAgent'], 'Range': 'bytes=1500000-1600000'}
                if origin:
                    media_hdrs['Origin'] = origin
                    media_hdrs['Referer'] = origin + '/'
                req_m = urllib.request.Request(url, headers=media_hdrs)
                try:
                    with urllib.request.urlopen(req_m) as rm:
                        print(f"      Range 1.5MB -> HTTP {rm.status}")
                except Exception as ex:
                    print(f"      Range 1.5MB -> FAILED: {ex}")
            else:
                print(f"    itag={a.get('itag')} signatureCipher present (needs decipher)")
    except Exception as e:
        print(f"  Request error: {e}")
