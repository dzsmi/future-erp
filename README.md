# 퓨처리테일 사내 ERP (재경/인사)

Apache OFBiz 17.12 기반 사내 ERP. 재경(전표·결재·결산마감·세금계산서·지급) / 인사(급여·4대보험·근태·연차·퇴직) 커스터마이징.

## 구성
- `applications/accounting`, `applications/humanres`, `applications/party` : OFBiz 기본 컴포넌트 (필요 모듈만 반입)
- `plugins/fracct` : 재경 커스텀 (`/fracct`)
- `plugins/frhr` : 인사 커스텀 (`/frhr`)

## 개발 규칙
- 커스텀 로직은 plugins 아래에 작성 (OFBiz 기본 소스 수정 최소화)
- 서비스 로직은 minilang 또는 Groovy
