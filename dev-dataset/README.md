# Developer activity datasets — Mac mini

개발자 활동 원본 데이터는 Supabase가 아니라 Mac mini의 `yamone-location-server`가 저장·관리·분석합니다.

## 흐름

- 앱 개발자 모드 > 설정 > 업로드에서 테스트 기기를 1회 연결합니다.
- 자동 기록·업로드 ON부터 OFF까지를 진단 세션 1건으로 기록합니다.
- 시스템 정시마다 이미 닫힌 조각을 `https://yamone-data.anynow.net`으로 전송하고, OFF 시 마지막 조각을 전송해 세션을 완료합니다.
- Mac mini는 원본 gzip 조각과 manifest를 로컬 디스크에 보관합니다.
- `https://yamone-admin.anynow.net`의 **활동 데이터** 메뉴에서 세션 확인, 1차 분석, 분석 결과/원본 다운로드를 합니다.

## 저장 위치

Mac mini 기본 경로:

`~/yamone-location-server/data/developer-datasets/`

세션별 구조:

- `sessions/<UUID>/manifest.json`
- `sessions/<UUID>/parts/part_XXXXXX.jsonl.gz`
- `sessions/<UUID>/parts/part_XXXXXX.meta.json`
- `sessions/<UUID>/final.json`
- `sessions/<UUID>/analysis/report.json`
- `sessions/<UUID>/analysis/summary.csv`
- `sessions/<UUID>/analysis/confusion.csv`

## 수집 데이터

GPS 원본, GNSS 상태, 가속도/자이로/중력/회전/기압/걸음 센서, Android 활동 인식, 현재 야모네 알고리즘의 입력·점수·후보·확정 상태, 기존 운동 기록의 GPS 필터 판단과 터널/단절 처리, 수동 정답 표시를 동일한 시간축에 저장합니다.

원본 데이터는 보정값으로 덮어쓰지 않으며 업로드 분할 경계에서도 알고리즘 상태를 초기화하지 않습니다. 정답이 없는 자동 수집 구간은 정확도 계산에서 정답으로 취급하지 않습니다.

## 보안

`yamone-data.anynow.net`은 앱 업로드 전용이며 Cloudflare Access OTP를 사용하지 않습니다. 대신 관리자 웹에서 만든 1회용 연결코드로 최초 등록 후 Mac mini가 발급한 긴 기기 토큰을 사용합니다. 관리자 웹 `yamone-admin.anynow.net`은 기존 Cloudflare Access 로그인을 그대로 사용합니다.

## 분석

관리자 웹에서 세션별 **1차 분석** 버튼을 누르면 Mac mini에서 로컬 분석을 실행합니다. 결과는 다운로드하여 ChatGPT에 전달할 수 있습니다. 원본 전체가 필요한 경우에만 원본 다운로드를 사용합니다.

Supabase의 개발자 데이터용 Storage/테이블/RPC는 사용하지 않습니다.
