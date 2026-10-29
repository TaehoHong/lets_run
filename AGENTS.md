# RunTaeho 백엔드 프로젝트 계약

공통 원칙은 [작업공간 계약](../AGENTS.md), 탐색은 [백엔드 코드 가이드](docs/07-codebase-guide.md)를 따른다. 작업 폴더는 `running/`이다.

## 코드 구조와 관례

- Kotlin/Spring Boot, Gradle Kotlin DSL과 JDK 설정은 [running/build.gradle.kts](build.gradle.kts)이 소유한다.
- 도메인별 `controller/`, `service/`, `repository/`, `entity/`, `dto/` 등의 실제 구조를 따르고, 같은 도메인의 가까운 구현을 예제로 사용한다.
- 인증 사용자 식별은 [AuthenticationHelper](src/main/kotlin/com/example/running/helper/AuthenticationHelper.kt), API 오류는 [ControllerAdvice](src/main/kotlin/com/example/running/exception/ControllerAdvice.kt)와 기존 오류 타입을 따른다.
- JPA 공통 엔티티는 `domain/common/entity/`, DB 변경 이력은 `src/main/resources/db/migration/`이 소유한다.
- 기록 검증·수락·보상은 `domain/running/service/`의 기존 소유자와 트랜잭션·사용자 소유권 경계를 따른다.
- API·스키마·기록 처리 변경은 출시 클라이언트 및 기존 데이터와의 호환성 영향을 함께 검토한다.

## 운영 연결

기획·정책과 시스템 책임은 [공통 아키텍처](../docs/06-architecture.md), 배포·운영 목표는 [운영 문서](../docs/03-deployment-rules.md)를 따른다. API 로깅·오류 응답의 실제 범위는 해당 공통 구현에서 확인한다.

## Declared Verification Commands

JDK 17과 프로젝트 Gradle wrapper를 사용한다. 명령·준비 조건은 [코드 가이드의 Verification Map](docs/07-codebase-guide.md#verification-map), task와 JUnit 구성은 [build.gradle.kts](build.gradle.kts)을 따른다.
