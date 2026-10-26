# 손익·원가 월간 실사 캘린더 & 표준 업무 매뉴얼 웹앱

GitHub Pages 정적 웹앱 (서버 없음 · index.html + data.json 2개 파일로 동작)

## 파일 구성
| 파일 | 역할 |
|---|---|
| `index.html` | 앱 본체 (캘린더·체크리스트·간트·매뉴얼 편집기) |
| `data.json` | 공유 데이터 (정례 업무·가이드·월별 완료 기록). 앱의 [⬆️ GitHub에 즉시 저장]이 이 파일을 커밋 |
| `README.md` | 본 문서 |

※ GitHub Pages는 `index.html` 파일명을 요구하므로 파일명 규칙(C-·일시) 적용 제외

## 배포 (최초 1회) — sebang0/notion 저장소 docs/ 폴더
1. Settings → Pages → Build and deployment → Source: **Deploy from a branch**
2. Branch: 이 폴더가 있는 브랜치 · 폴더 **`/docs`** → Save
3. 1~2분 후 `https://sebang0.github.io/notion/` 접속
- 저장소 루트의 rawdata·보고서는 Pages에 노출되지 않음 (docs/ 폴더만 배포)
- Private 저장소의 Pages는 GitHub Pro 이상 요금제 필요

## 여러 PC·모바일 동기화
1. 앱 우측 상단 **[☁️ GitHub 동기화 설정]**
2. Owner·Repository는 github.io 접속 시 자동 입력 · 브랜치·경로는 비워두면 Pages 설정에서 자동 감지 · 토큰만 입력
   - 토큰: GitHub → Settings → Developer settings → **Fine-grained tokens** → Repository access: `notion` 1개 → Permissions **Contents: Read and write**, **Pages: Read-only**
   - 토큰은 브라우저 localStorage에 암호화 없이 저장 → 공용 PC 사용 금지
3. 이후 **[⬆️ GitHub에 즉시 저장]** → `docs/data.json` 커밋 → Pages 반영 1~2분
4. 다른 PC 접속 시 data.json이 더 최신이면 교체 여부 확인창 표시
5. **[🔄 사용량 조회]** : GitHub API 시간당 남은 호출 수 확인 (토큰 사용 시 5,000회/시간)

## 공휴일 관리
- `index.html`의 `KR_HOLIDAYS` 표 (2025~2028 등록, 근로자의날 포함)
- 선거일·임시공휴일 발표 시 `"YYYY-MM-DD": "명칭"` 추가
- 미등록 연도 조회 시 캘린더 상단 경고 표시

## 주의
- Pages 사이트는 저장소가 Private이어도 URL을 아는 누구나 열람 가능 (Enterprise 제외) → 화주명·내부 일정 등 민감정보 기재 주의
