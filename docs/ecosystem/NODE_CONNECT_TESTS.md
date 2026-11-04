# Node Connect: Automated Test Matrix & Conformance Specification

**Module:** `:core-network`, `:app-desktop:test`, `:app-android:test`  
**Test Harness:** JUnit 5, Kotest Assertions, Headless In-Process Peer Harness  
**Target Coverage:** > 90% Protocol & State Machine Coverage  

---

## 1. Test Harness Architecture

To verify the complete Node Connect protocol without requiring physical hardware in CI, Node Player provides a dedicated dual-node in-process test harness:

```
[Virtual Node A (Sender)] <─── Direct In-Memory Channel (mTLS) ───> [Virtual Node B (Receiver)]
          │                                                                      │
          ├── MockAudioSource (Synthetic 48kHz PCM)                              ├── JitterBufferVerifier
          ├── FaultInjectionInterceptor (Drop, Delay, Corrupt)                   ├── DspBypassVerifier
          └── HybridClockSim (Clock drift +/- 5 hours)                           └── ConflictLogCollector
```

---

## 2. Automated Test Matrix

| Test Suite | Test Class | Focus Area | Verification Criteria |
| :--- | :--- | :--- | :--- |
| **mTLS Security** | `MutualTlsHandshakeTest` | Certificate Pinning | Handshake succeeds only when public key fingerprints match pinned registry; untrusted cert fails immediately. |
| **Pairing Security** | `PairingSecurityTest` | OTT & PIN Rate Limiting | 3 consecutive invalid PINs trigger 60s lockout; expired OTT (>120s) rejected with `EXPIRED_PAIRING_SESSION`. |
| **Audio Stream** | `AudioCastStreamTest` | Raw PCM Streaming | Pre-DSP PCM chunks delivered at exact 20ms intervals; PTS monotonically advances; zero file bytes created on disk. |
| **Seek & Flush** | `CastSeekSynchronizationTest` | Seek Handling | `AudioFlush` command flushes receiver jitter buffer within <10ms; playback resumes from target PTS without audible discontinuity. |
| **Drift Correction**| `DriftResamplerTest` | Clock Synchronization | Synthetic clock skew (+50ms drift) triggers $\pm 0.05\%$ resampling; restores alignment without sample dropping. |
| **Opus Fallback** | `AdaptiveBitrateFallbackTest` | Network Degradation | Jitter injected >100ms; stream smoothly switches from PCM to Opus 320 kbps without playback halt. |
| **Library Browse** | `RemoteLibraryServiceTest` | Remote Search & Metadata | Remote search returns filtered `RemoteTrackItem` list within 1500ms timeout; thumbnail LRU cache limits memory. |
| **Transfer Rail** | `TransferChunkIntegrityTest` | Data Chunking & Resume | 10 MB payload chunked into 256 KB slices; SHA-256 validated per chunk; interrupted transfer resumes from last slice. |
| **HLC Sync** | `HybridLogicalClockTest` | Conflict Resolution | Concurrent playlist edits across disconnected nodes merge deterministically using causality rules. |
| **Tombstones** | `TombstoneGarbageCollectionTest` | Deletion Gossip | Track deletion propagates tombstone; disconnected peer does not resurrect deleted tracks on reconnect. |
| **Stats Deduplication**| `ListeningStatsMergeTest` | Listening History Union | Identical listening sessions merged via UUIDv5 set union; zero double-counting of play stats. |
| **Snapshot & Undo** | `SyncSnapshotUndoTest` | Database Rollback | Pre-sync snapshot captured; `undoLastSync()` reverts local database to exact byte-for-byte pre-sync state. |
| **Fault Injection** | `MalformedMessageSecurityTest` | Buffer Overflow & Fuzzing | Oversized payloads (>4MB), corrupted protobuf frames, and out-of-order sequence numbers rejected safely. |

---

## 3. Headless Test Peer (`HeadlessTestPeer`)

A standalone desktop test peer implemented in `:core-network:src/test/kotlin/com/nodeplayer/network/HeadlessTestPeer.kt`:
- **Execution**: Can be launched from Gradle CLI (`./gradlew :core-network:runHeadlessPeer`).
- **Capabilities**: Acts as a library server or audio receiver without GUI; prints real-time logs of received PCM frames, buffer levels, and sync RPCs.
- **Automated Validation**: Provides programmable hooks to assert audio frame delivery, measure latency, and simulate network drops.

---

*Automated test specification maintained by the Node Player Core Engineering Team.*
