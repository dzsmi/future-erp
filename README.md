# 퓨처리테일 사내 ERP (재경/인사)

> **안내** — 이 저장소는 AI 부트캠프 실습용으로 만든 가상의 레거시 ERP 운영 소스입니다. 회사·인물·커밋 작성자·데이터는 모두 가상입니다. Apache OFBiz 17.12의 일부만 포함해 빌드되지 않으며 정적 분석용입니다. 출처와 라이선스는 `NOTICE`, `LICENSE`(Apache-2.0)를 참고하세요.

Apache OFBiz 17.12 기반 사내 ERP. 재경(전표·결재·결산마감·세금계산서·지급) / 인사(급여·4대보험·근태·연차·퇴직) 커스터마이징.

## 구성
- `applications/accounting`, `applications/humanres`, `applications/party` : OFBiz 기본 컴포넌트 (필요 모듈만 반입)
- `plugins/fracct` : 재경 커스텀 (`/fracct`)
- `plugins/frhr` : 인사 커스텀 (`/frhr`)

## 개발 규칙
- 커스텀 로직은 plugins 아래에 작성 (OFBiz 기본 소스 수정 최소화)
- 서비스 로직은 minilang 또는 Groovy
