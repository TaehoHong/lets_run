# 07. 백엔드 코드 가이드

## 읽기와 근거 확인

[백엔드 계약](../AGENTS.md)과 아래 소유 경로를 읽는다. 각 행의 소유·대표 구현·검증 경로를 근거로 소유 Git 저장소의 변경을 확인하고 직접 호출 관계로 이어서 탐색한다.

## Module Map

| 영역 | 소유 경로·진입점 | 대표 구현/심볼 | 관련 검증 근거 | 연결/책임 |
| --- | --- | --- | --- | --- |
| 앱·공통 실행 | [RunningApplication](../src/main/kotlin/com/example/running/RunningApplication.kt) / [config/](../src/main/kotlin/com/example/running/config) / [common/](../src/main/kotlin/com/example/running/common) | [HealthyCheckController](../src/main/kotlin/com/example/running/common/HealthyCheckController.kt) | [상태 확인 테스트](../src/test/kotlin/com/example/running/common/HealthyCheckControllerTest.kt) | Spring 실행·상태 확인·공통 처리 |
| 인증·계정·약관 | [auth/](../src/main/kotlin/com/example/running/domain/auth) / [user/](../src/main/kotlin/com/example/running/domain/user) / [term/](../src/main/kotlin/com/example/running/domain/term) / [security/](../src/main/kotlin/com/example/running/security) | [authenticateWithUser](../src/main/kotlin/com/example/running/helper/AuthenticationHelper.kt) | [user 테스트](../src/test/kotlin/com/example/running/domain/user) | OAuth/JWT·사용자·약관·접근 경계 |
| 기록·수락·통계 | [running/](../src/main/kotlin/com/example/running/domain/running) / [activity/](../src/main/kotlin/com/example/running/domain/activity) | [RunningAcceptanceService.accept](../src/main/kotlin/com/example/running/domain/running/service/RunningAcceptanceService.kt) | [running service 테스트](../src/test/kotlin/com/example/running/domain/running/service) | 기록 소유자·검증·중복·보상·집계 |
| 워치 완료 진입 | [CompletedRunningController](../src/main/kotlin/com/example/running/domain/running/controller/CompletedRunningController.kt) | [CompletedRunningService.complete](../src/main/kotlin/com/example/running/domain/running/service/CompletedRunningService.kt) | [완료 기록 테스트](../src/test/kotlin/com/example/running/domain/running/service/CompletedRunningServiceTest.kt) | 폰 중계 완료 기록의 수락 경로 |
| 포인트·아바타 | [point/](../src/main/kotlin/com/example/running/domain/point) / [avatar/](../src/main/kotlin/com/example/running/domain/avatar) | [ItemPurchaseService.purchase](../src/main/kotlin/com/example/running/domain/avatar/service/ItemPurchaseService.kt) | [point 테스트](../src/test/kotlin/com/example/running/domain/point) | 재화 이력·아이템/구매·착장 |
| 리그·게임 도메인 | [league/](../src/main/kotlin/com/example/running/domain/league) / [achievement/](../src/main/kotlin/com/example/running/domain/achievement) | [LeagueService](../src/main/kotlin/com/example/running/domain/league/service/LeagueService.kt) | [league 테스트](../src/test/kotlin/com/example/running/domain/league) | 리그·참가·시즌·관련 게임 도메인 |
| 신발 | [shoe/](../src/main/kotlin/com/example/running/domain/shoe) | [ShoeService](../src/main/kotlin/com/example/running/domain/shoe/service/ShoeService.kt) | [신발 테스트](../src/test/kotlin/com/example/running/domain/shoe/service/ShoeServiceActiveMainTest.kt) | 소유 신발과 기록 거리 연결 |
| Garmin | [garmin/](../src/main/kotlin/com/example/running/domain/garmin) | [GarminConnectionService](../src/main/kotlin/com/example/running/domain/garmin/service/GarminConnectionService.kt) | [garmin 테스트](../src/test/kotlin/com/example/running/domain/garmin) | OAuth·API·webhook·inbox·예약 작업 |
| 알림 | [notification/](../src/main/kotlin/com/example/running/domain/notification) | [NotificationService](../src/main/kotlin/com/example/running/domain/notification/service/NotificationService.kt) / [NotificationPushWorker.process](../src/main/kotlin/com/example/running/domain/notification/service/NotificationPushWorker.kt) | [notification 테스트](../src/test/kotlin/com/example/running/domain/notification) | 예약·집합 SQL·Expo 배치 발송·수명주기 |
| 문의 | [inquiry/](../src/main/kotlin/com/example/running/domain/inquiry) | [InquiryService.create](../src/main/kotlin/com/example/running/domain/inquiry/service/InquiryService.kt) | [문의 진입/DTO 근거](../src/main/kotlin/com/example/running/domain/inquiry/controller) | 고객 문의 접수·번호·저장 |
| 앱 버전 | [app/](../src/main/kotlin/com/example/running/domain/app) | [AppVersionService](../src/main/kotlin/com/example/running/domain/app/service/AppVersionService.kt) | [버전 진입 근거](../src/main/kotlin/com/example/running/domain/app/controller/AppController.kt) | 클라이언트 버전과 업데이트 연결 |
| 공통 엔티티·오류 | [domain/common/](../src/main/kotlin/com/example/running/domain/common) / [exception/](../src/main/kotlin/com/example/running/exception) / [helper/](../src/main/kotlin/com/example/running/helper) | [ControllerAdvice](../src/main/kotlin/com/example/running/exception/ControllerAdvice.kt) | [HTTP 오류 경로 테스트](../src/test/kotlin/com/example/running/domain/running/controller) | 공통 타입·오류 응답·인증 helper |

## Shared Capability Catalog

| 공유 기능 | 소유 구현/원본 | 사용·경계 |
| --- | --- | --- |
| 인증 사용자 | [authenticateWithUser](../src/main/kotlin/com/example/running/helper/AuthenticationHelper.kt) / [SecurityConfig](../src/main/kotlin/com/example/running/security/SecurityConfig.kt) / [TokenService](../src/main/kotlin/com/example/running/security/service/TokenService.kt) | 도메인 controller와 인증 경계 |
| 공통 오류 | [ApiException](../src/main/kotlin/com/example/running/exception/ApiException.kt) / [ControllerAdvice](../src/main/kotlin/com/example/running/exception/ControllerAdvice.kt) / [RunningValidationException](../src/main/kotlin/com/example/running/exception/RunningValidationException.kt) | 기존 API 오류 형태와 도메인 검증 |
| 기록 검증·수락 | [RunningValidationService](../src/main/kotlin/com/example/running/domain/running/service/RunningValidationService.kt) / [RunningAcceptanceService](../src/main/kotlin/com/example/running/domain/running/service/RunningAcceptanceService.kt) / [CompletedRunningService](../src/main/kotlin/com/example/running/domain/running/service/CompletedRunningService.kt) | 기록 진입 경로의 검증·소유권·수락·보상 |
| 업체별 운동 연동 저장 | [ActivityProviderRepository](../src/main/kotlin/com/example/running/domain/activity/repository/ActivityProviderRepository.kt) / [GarminRepository](../src/main/kotlin/com/example/running/domain/garmin/repository/GarminRepository.kt) | 공통 SQL은 provider가 고정된 일반 객체가 소유하고, Spring `@Repository`인 Garmin 저장소가 상속 없이 위임한다. 서비스·워커가 트랜잭션을 소유한다. |
| Garmin 인증 복귀 | [GarminConnectionService](../src/main/kotlin/com/example/running/domain/garmin/service/GarminConnectionService.kt) / [GarminAuthorizationRequest](../src/main/kotlin/com/example/running/domain/garmin/controller/dto/GarminAuthorizationRequest.kt) | appScheme은 app·runtaeho-dev만 허용한다. 스킴과 난수를 결합한 state 전체의 해시를 저장·소비한 뒤 고정된 연동 화면으로 복귀한다. body 없는 기존 요청과 43자 state는 기존 경로를 유지한다. |
| 영속 엔티티 | [BaseDatetime](../src/main/kotlin/com/example/running/domain/common/entity/BaseDatetime.kt) / [CreatedDatetime](../src/main/kotlin/com/example/running/domain/common/entity/CreatedDatetime.kt) / [Flyway migration](../src/main/resources/db/migration) | JPA 공통 필드와 DB 변경 이력 |
| API 로그 | [ApiLoggingInterceptor](../src/main/kotlin/com/example/running/common/interceptor/ApiLoggingInterceptor.kt) | 요청/응답 데이터와 경로별 처리의 실제 구현 |

## Conventions and Canonical Examples

| 관심사 | 현재 예제/원본 | 확인할 책임 |
| --- | --- | --- |
| 도메인 계층 | [CompletedRunningController](../src/main/kotlin/com/example/running/domain/running/controller/CompletedRunningController.kt) → [CompletedRunningService](../src/main/kotlin/com/example/running/domain/running/service/CompletedRunningService.kt) | controller·서비스·repository 역할과 실제 호출 |
| 검증·트랜잭션 | [RunningAcceptanceService.accept](../src/main/kotlin/com/example/running/domain/running/service/RunningAcceptanceService.kt) | 사용자 소유권·기록 수락·보상·트랜잭션 경계 |
| 입력·오류 변환 | [ControllerAdvice](../src/main/kotlin/com/example/running/exception/ControllerAdvice.kt) | 기존 예외/오류 응답 |
| 설정·의존성 | [build.gradle.kts](../build.gradle.kts) / [config/](../src/main/kotlin/com/example/running/config) | JDK·Kotlin·KSP·Spring 설정 |
| 테스트 예제 | [CompletedRunningServiceTest](../src/test/kotlin/com/example/running/domain/running/service/CompletedRunningServiceTest.kt) | 관측 결과·소유권·중복 보상 경계 |

## Dependency and Ownership

기존 controller → service → repository의 실제 호출 관계를 따라 변경한다. 도메인 간 기록·재화·리그 영향은 기존 서비스 경계에서 확인한다. 클라이언트 연동 계약은 [워치 기록 원본](../../runtaeho_app/src/features/watchSync/README.md)과 폰 service의 호출을 연결해 확인한다.

### 알림 푸시 배치

- [NotificationScheduler.pushes](../src/main/kotlin/com/example/running/domain/notification/scheduler/NotificationScheduler.kt)는 한 번에 최대 100건을 조회해 [NotificationPushWorker.process](../src/main/kotlin/com/example/running/domain/notification/service/NotificationPushWorker.kt)가 동기로 한 배치를 마칠 때까지 기다린다.
- [NotificationPushRepository](../src/main/kotlin/com/example/running/domain/notification/repository/NotificationPushRepository.kt)는 점유·발송 직전 검사·결과 저장을 각각 집합 UPDATE 한 번으로 처리한다. 서버 간 후보 조회 중복은 허용하고, 배치 토큰으로 점유한 행 및 최종 검사에서 새 토큰으로 갱신된 행만 처리한다. 점유 만료 복구 UPDATE는 별도로 실행한다.
- 최종 검사는 사용자·기기 바인딩·토큰·수신 설정·알림 만료·리마인더 기준·시도 횟수를 재검사한다. 이미 ticket이 있는 행은 발송 시도 횟수를 늘리지 않고 receipt 조회 대상으로만 점유를 갱신한다. 결과 저장과 `DeviceNotRegistered` 기기 비활성화는 같은 소유권·바인딩 조건을 적용한 하나의 UPDATE로 처리한다. HTTP 통신 중 DB 트랜잭션을 유지하지 않는다.
- [NotificationRepository.pushContent](../src/main/kotlin/com/example/running/domain/notification/repository/NotificationRepository.kt)는 알림함과 같은 번역 fallback을 재사용해 내용을 일괄 조회한다. [ExpoPushClient](../src/main/kotlin/com/example/running/domain/notification/service/ExpoPushClient.kt)는 최대 100개 메시지를 한 요청으로 보내고 ticket 배열의 길이와 순서를 확인한다. receipt는 한 요청으로 조회해 ticket ID로 대응시킨다.

## Verification Map

작업 폴더: `running/`. JDK 설정과 JUnit 실행은 `build.gradle.kts`, [각 테스트](../src/test/kotlin)의 DB·설정·대역 조건은 해당 테스트가 소유한다.

| 분류 | 프로젝트 폴더에서 사용하는 명령/원본 | 조건·소유 범위 |
| --- | --- | --- |
| 전체 configured tests | `./gradlew test` | JUnit 5·각 테스트의 외부 의존/실행 조건 |
| 전체 빌드 | `./gradlew build` | 프로젝트 build task와 그 의존 task |
| 기록 수락·검증 | `./gradlew test --tests "*CompletedRunningServiceTest" --tests "*RunningValidationServiceTest"` | 해당 단위 테스트 |
| 연동 로그 예제 | `./gradlew test --tests "*GarminLoggingTest"` | Garmin 로깅 테스트의 대상 범위 |
| 연동 저장·프록시 경계 | `./gradlew test --tests "*GarminDatabaseTest"` | [GarminDatabaseTest](../src/test/kotlin/com/example/running/domain/garmin/service/GarminDatabaseTest.kt)의 격리 MariaDB 조건. Spring 예외 변환 프록시를 거친 저장소의 미연결 조회·연결·state·inbox·provider 격리 시나리오. 실행 여부와 결과는 작업 계획에서 구분한다. |
| 집계·재화 예제 | `./gradlew test --tests "*RunningStatisticsTest" --tests "*UserPointHistoryServicePaginationTest"` | 각 클래스의 조건 |
| DB·HTTP 경계 | [DB 테스트 구성](../src/test/kotlin/com/example/running/domain/running/service/RunningAcceptanceDatabaseTest.kt) / [HTTP 테스트 구성](../src/test/kotlin/com/example/running/domain/running/controller/CompletedRunningHttpTest.kt) | 접속·설정·소유 데이터 조건을 해당 테스트에서 확인 |
| 컴파일·패키징 | `./gradlew compileKotlin compileTestKotlin`; `./gradlew bootJar` | Gradle/KSP·JDK 설정; compileTestKotlin은 테스트 소스 컴파일이며 테스트를 실행하지 않음 |
| 배포 | [배포 스크립트](../deploy/blue-green-deploy.sh) | 서버·컨테이너·Nginx 실행 범위 |

## 생성물과 환경 경로

`build/`, `bin/`, `.gradle/`, `.kotlin/`은 빌드·도구 산출물이다. DB 변경의 소유 원본은 `src/main/resources/db/migration/`이다. 자격증명과 환경 파일의 경로 관계는 [.gitignore](../.gitignore) 및 실행 구성을 따른다.


## Linked Sources

| Source | 소유 주제 | 적용 범위 | 근거 |
| --- | --- | --- | --- |
| [Dockerfile](../Dockerfile) | 컨테이너 실행 설정 | 백엔드 | JAR·profile |
