# 손익·원가 월간 실사 캘린더 & 표준 업무 매뉴얼 웹앱

GitHub Pages 정적 웹앱 (서버 없음 · index.html + data.json 2개 파일로 동작)

## 파일 구성
| 파일 | 역할 |
|---|---|
| `index.html` | 앱 본체 (캘린더·체크리스트·간트·매뉴얼 편집기) |
| `data.json` | 공유 데이터 (정례 업무·가이드·월별 완료 기록). 앱의 [⬆️ GitHub에 즉시 저장]이 이 파일을 커밋 |
| `README.md` | 본 문서 |

※ GitHub Pages는 `index.html` 파일명을 요구하므로 파일명 규칙(C-·일시) 적용 제외

## 배포 (최초 1회)
1. 저장소 루트에 3개 파일 업로드
2. Settings → Pages → Build and deployment → Source: **Deploy from a branch** → Branch: `main` / `/(root)` → Save
3. 1~2분 후 `https://<계정명>.github.io/<저장소명>/` 접속

## 여러 PC·모바일 동기화
1. 앱 우측 상단 **[☁️ GitHub 동기화 설정]**
2. Owner · Repository · Branch · 토큰 입력 → 설정 저장
   - 토큰: GitHub → Settings → Developer settings → **Fine-grained tokens** → 이 저장소 1개만 선택 → Repository permissions **Contents: Read and write**
   - 토큰은 브라우저 localStorage에 암호화 없이 저장 → 공용 PC 사용 금지
3. 이후 **[⬆️ GitHub에 즉시 저장]** → data.json 커밋 → Pages 반영 1~2분
4. 다른 PC 접속 시 data.json이 더 최신이면 교체 여부 확인창 표시
5. **[🔄 사용량 조회]** : GitHub API 시간당 남은 호출 수 확인 (토큰 사용 시 5,000회/시간)

## 공휴일 관리
- `index.html`의 `KR_HOLIDAYS` 표 (2025~2028 등록, 근로자의날 포함)
- 선거일·임시공휴일 발표 시 `"YYYY-MM-DD": "명칭"` 추가
- 미등록 연도 조회 시 캘린더 상단 경고 표시

## 주의
- 공개(Public) 저장소의 Pages는 URL을 아는 누구나 열람 가능 → 화주명·내부 일정 등 민감정보 기재 주의
