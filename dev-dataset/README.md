# Developer activity datasets — v0.36.10

## Controls and semantics

- Settings > Upload appears only in the app's developer mode.
- **Automatic recording/upload ON starts a separate diagnostic session immediately.** It does not wait for a detector threshold. OFF stops it. This is not a claim that the user is actually moving.
- The existing walking/running/cycling recorder remains independent. Its start/stop creates reference markers inside an ongoing automatic dataset. With automatic capture OFF, developer-mode normal activity recording creates a local-only diagnostic dataset (the manual-diagnostic setting defaults ON). Explicitly select that dataset to upload it.
- Normal automatic-detection settings are not overwritten. During collection the same hybrid formulas run in shadow mode for all three supported classes, without starting/stopping additional ordinary records. `diagnosticAllClasses=true` makes this eligibility difference explicit. Ordinary recorder inactivity timers still own ordinary recordings, never the continuous dataset.
- Switching back to normal mode stops diagnostic collection and location-sharing service, invalidates queued-upload approvals, and disconnects own-server requests. **An already transmitted request cannot be recalled.** Unsent data stays local until explicitly approved after re-entering developer mode. Normal local activity recording continues.
- Developer mode alone is not server authorization. An anonymous app account must redeem a one-use tester pairing code. No pairing secret or privileged service key is built into the APK/repository.

## Collection (developer only)

Independent raw streams: GPS and network-provider fixes before filtering (requested GPS interval 1 second, no distance threshold); accuracy, speed/speed accuracy, bearing/bearing accuracy, altitude/vertical accuracy, provider, mock status; GNSS satellite quality; accelerometer/gyroscope requested at 25 Hz; gravity/rotation vector at 5 Hz; pressure at 1 Hz; hardware step detector/counter; Android probable activities and confidence. Rates are requests and actual sample intervals are retained. Missing/unregistered sensors are explicit, never fabricated zeros.

Algorithm trace: native detector sensor/location inputs, platform transitions, per-class scores (**not probabilities**), candidate/confirmed state and hysteresis timers, step window, EMA values, configuration and source commit. During ordinary movement records, every GPS-filter branch emits input, acceptance/rejection reason, output distance/speed, gap/tunnel estimates, and current limits. Existing recorder snapshots and final metadata are retained. A continuous dataset without an ordinary workout contains raw inputs and detector outputs, not an invented workout route/pace summary.

Reference data: explicit actual-activity markers (stationary/walking/running/cycling/vehicle/unknown), phone placement, tunnel entry/exit, GPS issue; manual activity-selection markers are distinguished from detector-generated labels. Raw samples are never trimmed. Default boundary exclusion is an **analysis parameter of 10 seconds**, not a universal sensor calibration value. Unlabeled automatic data has no asserted ground truth. No audio, contacts, advertising ID, IMEI or Android ID is collected by this component. Device model/OS/sensor descriptions support diagnostic interpretation.

Each event has session UUID, event sequence, wall-clock milliseconds, monotonic receive time, clock epoch and source measurement time where available. Upload boundaries never reset detector state. Wall-clock/time-zone changes and process restarts are events. A reboot/interruption is not falsely filled with inferred movement.

## Local durability / upload

`getNoBackupFilesDir()/developer-datasets/<UUID>/` contains `manifest.json`, an append journal, immutable `part_NNNNNN.jsonl.gz` and matching `*.meta.json`, separate acknowledgment files. SHA-256 is computed over the compressed bytes. A corrupt/incomplete final journal line is truncated on recovery; acknowledged parts are never rewritten. The journal is synced every second; a sudden hardware power loss is not a guaranteed zero-loss scenario.

At each local system-clock hour boundary, existing sealed parts and the current partial part are released to the outbox. A size safety cut at 4 MiB uncompressed may make several transport parts within one hour; those size parts wait for the hour boundary. Network/OS restrictions can delay actual transmission. OFF releases the last part. Already acknowledged parts are not resent; an ambiguous response may cause an idempotent retry of the same part, not a duplicate server record. Finalization is a small metadata request after ALL parts are acknowledged. Server checks part numbering and event-sequence continuity before one session becomes `complete`.

No microphone is started by capture. A visible location foreground service and a bounded, renewed CPU wake lock are used during capture; this is deliberately heavier than ordinary app operation. A persistent network-constrained retry job handles failed uploads. Storage safety limits stop local capture below 256 MiB free space or above 1 GiB of local datasets. The diagnostic bucket has a conservative 256 MiB server safety cap and 8 MiB object cap; existing billing plans are not changed. Old records do not acquire sensor originals retroactively.

## Analysis

Export/download a completed dataset (manifest + part metadata + gzip objects), then:

```
python3 dev-dataset/analyze_dataset.py /path/to/session --boundary-seconds 10 --output report.json
```

It verifies hashes, byte counts, part numbers and event sequences, reports streams/actual rates/gaps, and compares logged algorithm output only to available manual reference intervals. Accuracy remains null for unlabeled sessions. For future algorithm tuning, split evaluation by entire sessions/dates; do not put adjacent windows from the same ride in both training and testing. Single-person performance is not population accuracy. Original traces allow new algorithms to be replayed and compared; collection alone does not improve the classifier.

## Verification before real collection

Check tester pairing, normal/developer UI, ON/OFF, screen-off sensor continuity on the actual Galaxy, manual record markers, an hour rollover, airplane mode and retry, switching to normal mode during transfer, and process death/recovery. APK compilation/unit/browser tests do not substitute for these physical-device checks.

## 관리자와 분석 도구

`admin-web`의 **개발자 활동 데이터** 탭에서 24시간 유효한 1회용 테스트 기기 연결 코드를 발급합니다. 이 코드나 인증 토큰을 공개 저장소에 넣지 않습니다. 서버에 확정된 세션은 SHA-256을 확인한 후 `.tar`로 내려받습니다. 압축을 푼 UUID 폴더에 `python3 dev-dataset/analyze_dataset.py <세션 폴더> --output report.json`을 실행하면 무결성·샘플 간격·정답 표시 구간의 혼동 행렬을 볼 수 있습니다. 정답이 없는 구간은 정확도로 계산하지 않습니다. 기존 관리자 웹 배포와 별도로 소스 브랜치를 적용하거나 제공된 관리자 웹 묶음을 로컬에서 실행할 수 있습니다.

앱 설정 목록의 **기기 진단 원본 삭제**는 사용자 확인 후 종료된 진단 세션만 삭제합니다. 서버의 원본이나 일반 활동 기록은 지우지 않습니다. 업로드되지 않은 원본 삭제는 복구할 수 없습니다. 서버 테스트 저장 한도에 도달한 경우 관리자가 원본을 보관한 후 Supabase 비공개 버킷을 정리해야 합니다. 자동 삭제는 하지 않습니다.

걷기·달리기·자전거뿐 아니라 기존 등산·Snow 활동의 시작/종료·요약/상태도 동일한 진단 타임라인에 연결합니다. Snow 요청의 시작 출처가 명확하지 않으면 수동 정답으로 승격하지 않습니다. 해당 종목의 자동 분류 모델을 새로 학습한 것은 아닙니다.
