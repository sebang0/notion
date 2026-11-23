# 업무 일정표 안드로이드 앱

웹앱(`docs/index.html`)을 그대로 담은 안드로이드 앱 · 업무 데이터는 웹과 같은 GitHub 저장소 공유

## 구성
| 항목 | 내용 |
|---|---|
| 화면 | `docs/` 폴더(index.html · 글꼴 · key.json)를 빌드 때 앱에 포함 → 웹 수정 시 다음 빌드 자동 반영 |
| 데이터 | `data` 브랜치 `data.json` (암호문) · 비밀번호 입력 → 웹과 같은 내용 · 자동 저장 · 변경 이력 |
| 열쇠 파일 | 실행 시 GitHub Pages 최신 `key.json` → 실패(오프라인) 시 앱 포함본 |
| 최소 버전 | Android 8.0 (API 26) |
| 패키지 | `com.sebang.notion` · 앱 이름 「업무 일정표」 |

## 웹 기능 → 앱 처리
| 웹 기능 | 앱 동작 |
|---|---|
| PDF 출력 | 안드로이드 인쇄 창 → 대상 「PDF로 저장」 · 용지 A4 가로/세로 자동 지정 · 파일명 C-…_YYMMDD_HHMM |
| ☰ 백업 내보내기 | 저장 위치 선택 창 |
| ☰ 불러오기 | 파일 선택 창 |
| 알림·확인 창 | 안드로이드 대화상자 |
| 뒤로 가기 | 앱 종료 없이 백그라운드 (잠금 해제 상태 유지) |
| 앱 복귀 | 서버 최신 내용 자동 반영 (웹과 동일) |
| GitHub API 사용량 | ☰ 메뉴 기존 사용량 표시 그대로 |

## 패턴/지문·얼굴 잠금 (앱 전용)
| 항목 | 내용 |
|---|---|
| 등록 | 비밀번호로 연 뒤 ☰ 메뉴 → 🔢 패턴/생체 잠금 → 패턴 2회 입력 / 지문·얼굴 켜기 |
| 보관 | 비밀번호를 Android Keystore AES-256-GCM으로 암호화해 앱 전용 저장공간에 보관 (서버 전송 없음) |
| 패턴 | 3x3, 최소 4점, PBKDF2 해시만 저장 |
| 지문/얼굴 | 안드로이드 10 이상, 휴대폰에 생체정보 등록 필요 |
| 실패 제한 | 패턴 연속 5회 실패 → 패턴/생체 설정과 보관 비밀번호 삭제, 비밀번호 필수 |
| 자동 잠금 | 백그라운드 5분 경과 후 복귀 시 잠금 (미저장 변경은 암호화 임시 보관 후 다음 해제 때 복구) |
| 비밀번호 변경 | 이 기기: 자동 갱신 / 다른 기기에서 변경: 비밀번호 1회 입력 후 자동 갱신 |
| 코드 | `QuickLock.java` (보관), `MainActivity.QuickBridge` (웹 창구 `SebangQuick`), `docs/index.html` [4-0] |

## 빌드 · 배포 (GitHub Actions `Android APK`)
| 단계 | 내용 |
|---|---|
| 실행 | `android/**` · `docs/index.html` · `docs/fonts/**` 변경 push 또는 Actions 수동 실행 |
| 결과 | Releases `android-v1.0.N` (APK 첨부) · Actions Artifacts |
| 서명 키 | Secrets `ANDROID_KEYSTORE_B64` · `ANDROID_KEYSTORE_PASSWORD` (미등록 시 임시 키 → 업데이트 때 재설치) |

## 설치
1. 휴대폰에서 GitHub 로그인 → 저장소 Releases → 최신 `SebangSchedule_v1.0.N.apk` 내려받기
2. 「출처를 알 수 없는 앱 설치」 허용 → 설치
3. 앱 실행 → 웹과 같은 비밀번호 입력
