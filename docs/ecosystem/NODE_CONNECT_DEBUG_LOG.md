# Node Connect Debug Log & Root Cause Analysis

**Branch:** `feature/node-connect`  
**Base Commit:** `8c60ef1`  
**Test Devices:**
- Windows 11 PC (Host: `10.222.143.172`, Network Profile: `Public`, Adapter: `Wi-Fi 5`)
- Physical Android Phone: realme `4a505e5` (Android 16, IP: `10.222.143.220`, Interface: `wlan1`)
- USB Link: ADB active via USB debugging (`4a505e5`)

---

## 1. Diagnostic Checklist of Potential Failure Causes

| Category | Potential Failure Cause | Status | Evidence & Root Cause Finding |
| :--- | :--- | :---: | :--- |
| **Server Bind** | Server listens only on localhost or wrong interface | **NO (Partial)** | `NodeServer` binds to `InetSocketAddress(port)` which defaults to `0.0.0.0` (all interfaces). However, on Android, multiple interfaces (`rmnet_data3`, `vgate0`, `wlan1`) exist, and advertised host addresses were picking single default interface. |
| **Port Conflict** | Port conflict / fallback not advertised | **RESOLVED** | Dynamic port fallback (`54820..54830`) implemented in commit `8c60ef1`. Resolved `BindException`. |
| **Firewall** | Windows Firewall blocks inbound TCP/UDP; profile is Public | **YES (CRITICAL)** | PC connection profile is `Public` (`Krighz 255`). Windows Firewall drops all inbound ICMP echo requests (100% phone ping loss to PC) and blocks unsolicited inbound UDP/TCP packets unless an explicit allow rule or private profile is active. |
| **Android Permissions** | Missing network/Wi-Fi/multicast permissions | **YES (CRITICAL)** | `AndroidManifest.xml` lacked `CHANGE_WIFI_MULTICAST_STATE`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, and `CAMERA`. Without `CHANGE_WIFI_MULTICAST_STATE` and `WifiManager.MulticastLock`, the Android Linux kernel and Wi-Fi chipset silently drop incoming multicast UDP packets (`239.255.77.88`). |
| **Discovery** | mDNS/Multicast fails across hotspot or subnets | **YES (CRITICAL)** | `NodeDiscovery` relied exclusively on multicast UDP `239.255.77.88`. Hotspots and isolated APs routinely block multicast. Missing directed UDP broadcast (`255.255.255.255`, subnet `.255`), gateway probing (PC probing default gateway), and subnet scanning. |
| **TLS / Trust** | Handshake failure from cert pinning / client auth | **YES (CRITICAL)** | `NodeServer` configured `candidate.needClientAuth = true` and `PinningTrustManager` rejected any unpinned certificate immediately during TLS handshake before pairing or hello. Without an active pairing session, incoming connections were severed at the socket layer with no log output. |
| **Framing** | Framing header, length, or CRC bugs | **NO** | `FramingTest` unit tests pass. 14-byte binary frame header (`0x4E 0x50`, type, flags, seq, length, crc32) is verified little-endian/network-consistent. |
| **Pairing Machine** | State machine waiting for dual confirmation | **YES** | Strict pairing required concurrent pairing dialog open on both devices with manual 6-digit PIN entry or QR fingerprint matching. Silent catch blocks swallowed handshake exceptions. |
| **Coroutines / Life** | Foreground service killed on realme | **YES** | `ActivityManager` killed `PlaybackService` (`u0a605 com.nodeplayer.music/.service.PlaybackService`) due to app idle. Dedicated foreground service for Node Connect is needed to maintain sockets with screen off. |
| **Logging** | Silent catches hiding errors | **YES (CRITICAL)** | `NodeLog` was not wired to Android logcat or persistent disk files; `catch (e: Exception)` blocks in `NodeServer`, `NodeDiscovery`, and `NodeConnection` swallowed failures silently. |

---

## 2. Network Scenarios Reproduction & Analysis

### Scenario 1: Same Wi-Fi Router
- **Symptom:** Devices do not discover each other. Tapping "Connect" fails.
- **Root Cause:**
  1. Multicast packets (`239.255.77.88:54821`) dropped on Android due to missing `MulticastLock`.
  2. Windows Public profile drops inbound UDP 54821.
  3. Pre-pairing TLS handshake aborted by `PinningTrustManager` because neither device was in pairing registry.
- **Fix:**
  - Multi-method discovery: Add subnet broadcast (`10.222.143.255:54821`) and general broadcast (`255.255.255.255:54821`).
  - Acquire `WifiManager.createMulticastLock("node_discovery")` on Android.
  - Implement **Simple Mode**: Encrypted TLS with non-verifying TrustManager (or plain TCP toggle) so initial discovery and hello exchange succeed without prior pinning.

### Scenario 2: PC Connected to Phone Hotspot
- **Symptom:** PC cannot discover phone; phone's hotspot IP is on `ap0` or `swlan0`, not `wlan0`.
- **Root Cause:**
  - The phone operates as DHCP server / gateway (typically `192.168.43.1` or `192.168.x.1`).
  - Android hotspot routing blocks multicast between the AP interface and local apps.
- **Fix:**
  - Phone enumerates all network interfaces and advertises all non-loopback candidate IPs.
  - PC implements **Gateway Probe**: On hotspot connection, PC immediately probes the default gateway IP (`Get-NetRoute -DestinationPrefix '0.0.0.0/0'`) on port `54820..54830`.
  - Parallel subnet sweep (`/24`) with 200ms connection timeouts.

### Scenario 3: USB Tethering
- **Symptom:** Android phone connected via USB tethering presents a virtual RNDIS/NCM adapter (`192.168.42.x`).
- **Root Cause:** Single-interface bind missed the tethered adapter.
- **Fix:** Server binds `0.0.0.0`. Discovery and QR code include candidate IPs across all active interfaces.

### Scenario 4: USB Debugging Link (Deterministic Test Path)
- **Symptom:** Without Wi-Fi, apps cannot discover or communicate.
- **Root Cause:** No ADB port forwarding configured.
- **Fix:**
  - PC initiates `adb forward tcp:54825 tcp:54820` (PC connects to Phone) and `adb reverse tcp:54826 tcp:54820` (Phone connects to PC).
  - Advanced USB Link option in UI runs local loopback tunnel (`127.0.0.1`), completely bypassing Wi-Fi, routers, and external firewalls.

### Scenario 5: Manual IP & QR Code
- **Symptom:** Entering IP fails with timeout or TLS verification error; QR code crashed with overflow.
- **Root Cause:** QR payload previously included a 68-byte cert fingerprint and used an undersized Version 1-6 matrix; TLS rejected connection if not paired.
- **Fix:**
  - QR payload format: `nodeconnect://<ip1>,<ip2>:<port>?dev=<id>&name=<name>`.
  - Version 1-10 QR matrix capable of up to 216 bytes.
  - Direct connect tries each candidate address in parallel with 3-second timeout, using the first responding socket.
