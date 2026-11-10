# Garmin 직접 수신 운영

## 수신 경계

`deploy/nginx/dev-runtaeho.conf.example`이 URL 호환과 전용 로그를 소유한다.
Garmin API Tools에 저장한 endpoint에서 query가 유지되지 않아, 기존 43자
`GARMIN_WEBHOOK_TOKEN`을 URL 경로에 넣는다. Nginx의 exact location이 이를 기존
`/api/v1/garmin/webhook?token=…`으로 내부 변환한다. 외부 query는 버린다.
애플리케이션의 상수시간 token 검사, 암호화 inbox, worker와 기존 기록 교정은 유지한다.

공유기의 외부 443 → 서버 443 규칙을 사용한다. Nginx는 443에서 TLS를 종료하고
기존 loopback 백엔드 12642(blue)/12644(green)로 직접 proxy한다. 기존 앱·배포 점검의
12643 HTTPS listener도 같은 server block에서 유지한다. 443에서 12643으로 다시
HTTPS 요청을 보내는 중간 프록시는 필요하지 않다.
Nginx 버전은 숨기고 헤더·본문의 무응답 timeout은 각각 10초·30초로 제한한다.

- 실제 등록 주소는 `https://dev-api-run-taeho.com/api/v1/garmin/webhook/<delivery-token>`이다.
- 서버의 보호된 환경 값으로 `__GARMIN_WEBHOOK_TOKEN__`을 치환한다. 치환된 설정과 URL은
  Git·채팅·명령 인자·일반 로그에 남기지 않는다. 원본 키를 새로 발급할 필요는 없다.
- 실제 site 파일은 root와 배포 사용자만 읽도록 관리한다. Nginx master와 기존 배포
  스크립트가 읽을 수 있어야 한다. 적용 전 기존 설정을 0700 보호 폴더에 백업한다.
- webhook access log는 고정 URI와 응답 상태만 기록한다. URL 경로를 포함할 수 있는
  해당 location의 Nginx error log는 기록하지 않는다. 처리 오류는 기존 app worker와
  inbox 상태로 확인한다. 요청 본문·좌표는 로그로 출력하지 않는다.
- 잘못된 비밀 경로는 404로 차단한다. 기존 query 방식도 기존 앱 인증 아래 유지한다.
- `proxy_pass http://127.0.0.1:12642;` 형식은 blue/green 배포 스크립트와 공유한다.

## 검증과 복구

활동 요약·상세·샘플은 `GarminWebhook` DTO로 읽고 `GarminActivityMapper`에서 변환한다.
원본 JSON 트리는 수신 경계 검증과 정규화·event hash에만 사용한다. 마지막 샘플의 누적
활동시간이 요약보다 짧으면 그 잔여 활동시간까지 종료 경계에 포함한다. 마지막 구간에
활동시간만 더하면 `duration > elapsed`가 되어 상세 전체가 거절될 수 있다. 전체 거리와
활동시간은 원본을 유지하고 `RunningValidationService`의 공통 검증을 완화하지 않는다.

측정 구간 생성과 GPS 배분은 별도로 수행한다. 경로는 한 번 순회해 구간에 배분하고,
마지막 항목은 전체 종료 시각까지의 남은 GPS를 모두 포함한다. 마지막 측정 구간 이후
활동시간·거리가 늘지 않는 정지 샘플도 보존하며 구간의 거리·활동시간·센서 평균은 유지한다.

`GarminInboxWorker`는 영구 검증 오류도 inbox를 DONE으로 완료하고 원본을 제거하므로,
DONE만으로 상세 저장 성공을 판단하지 않는다. 같은 시각의 검증 로그와 실제 item·GPS를
대조한다. 동일 원본의 재전송은 event hash로 중복 제거되므로 이미 DONE인 거절 건을
복구할 때는 해당 inbox와 원본의 재처리 경로까지 확인해야 한다.

```sh
# 설치된 Nginx를 사용하는 격리된 localhost 프록시 회귀. 실제 DB·Garmin으로 보내지 않는다.
NGINX_BIN=/usr/sbin/nginx python3 deploy/nginx/test_garmin_webhook_proxy.py
```

이 검증은 1MiB 초과 body의 인증된 전달, 외부 query 제거, 틀린 경로의 upstream 차단,
upstream 장애와 로그의 토큰 비노출을 확인한다. 실행 중인 서비스 설정은 바꾸지 않는다.

실제 적용 시에는 보호된 설정으로 `nginx -t`를 통과한 뒤 reload하고 기존 health와
token 없는 요청의 차단을 확인한다. 실패하면 백업한 site 파일을 복원·검증·reload한다.
내부망의 도메인 접속 성공만으로 외부 연결을 판단하지 않는다. NAT loopback·UPnP
매핑과 정적 포워딩이 다른 경로를 사용할 수 있으므로 별도 외부 네트워크에서 443을
검증하고, 실제 서버의 443 listener·방화벽도 함께 확인한다.
Garmin endpoint의 5개 지원 항목에 새 주소를 저장한 뒤, 새 GET에서 경로 보존과 Enabled,
On Hold 상태를 확인한다. 원래 켜져 있지 않은 이벤트는 추가하지 않는다.

과거 기록은 API Tools의 Summary Resender에서 해당 사용자·날짜 범위의 Activity와
ActivityDetail만 재전송한다. 개발자 Data Viewer를 주기 수집하는 기능은 만들지 않는다.
직접 inbox 처리와 기존 기록 ID, 거리·GPS, 지급 포인트·리그 스냅샷을 함께 대조한다.
재전송 성공은 다음 운동이 앱 실행 없이 자동 도착했다는 증거와 구분해서 보고한다.

## 연결 장애 후 전송 재개

실패한 알림은 재시도 간격이 늘어날 수 있어 서버 복구 직후 도착하지 않을 수 있다.
외부 443·수신 URL·사용자 권한이 정상인지 확인한 뒤, 해당 이벤트의 On Hold를 저장·재조회하고
해제 상태도 저장·재조회해 큐의 전송 재개를 요청한다. 공식 Activity API 가이드는
On Hold 해제 후 2분 이내 재개를 안내한다. Enabled를 끄면 대기 알림이 버려지므로
전송 재개 용도로 사용하지 않는다.

실제 Garmin 발신 요청의 HTTP 200, inbox DONE, 연결의 last_received_at 갱신과
원본 거리·GPS를 함께 확인한다. Summary Resender의 접수 표시나 자체 복구 POST만으로
Garmin 발신 성공을 판단하지 않는다. 재전송 후 포인트·리그 스냅샷과 GPS가 보존되는지도 대조한다.
