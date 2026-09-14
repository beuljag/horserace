# HorseRace (경마)

![Minecraft](https://img.shields.io/badge/Minecraft-Paper%2026.1.x-brightgreen)
![Java](https://img.shields.io/badge/Java-25%2B-orange)
[![Release](https://img.shields.io/github/v/release/beuljag/minecraft-horserace-plugin)](https://github.com/beuljag/minecraft-horserace-plugin/releases/latest)
[![License](https://img.shields.io/github/license/beuljag/minecraft-horserace-plugin)](LICENSE)

1인용 마인크래프트 경마 도박 플러그인.
**말을 고르고 금액을 누르면 대기 시간 없이 바로 레이스가 시작된다.**

## 주요 기능

- **즉시 시작** — 정해진 경기 시각이나 대기열이 없다. 혼자서도 바로 돌린다
- **GUI 레이스 연출** — 상자 GUI 5개 레인에서 12~16초간 경기가 진행된다
- **배당 = 승률 반비례** — 배당이 높은 말일수록 덜 이긴다 (`odds-power` 로 곡선 조절)
- **공정 배당 표시** — `/hr horses` 가 기대값 1.0 이 되는 배당을 같이 알려줘 밸런스를 맞추기 쉽다
- **인게임 편집** — 말 추가·삭제·배당 변경·판돈 목록을 명령어로 바로 수정
- **두 가지 베팅 재화** — 아이템(기본 금괴) 또는 Vault 이코노미(돈)
- **연속 플레이** — 결과 화면에서 같은 베팅으로 바로 한 판 더

## 목차

- [요구사항](#요구사항)
- [설치](#설치)
- [사용법](#사용법)
- [설정 (`config.yml`)](#설정-configyml)
- [빌드](#빌드)
- [구조](#구조)
- [라이선스](#라이선스)

---

## 요구사항

| 항목 | 값 |
|---|---|
| 서버 | Paper **26.1.x** |
| Java | **25** 이상 |
| 선행 플러그인 | 없음 (Vault 는 돈 베팅 쓸 때만 선택) |

---

## 설치

[Releases](../../releases) 에서 `horserace-1.0.0.jar` 를 받아 서버 `plugins/` 에 넣고
재시작. 첫 실행 시 `plugins/HorseRace/config.yml` 이 생성된다.
(직접 빌드하려면 아래 [빌드](#빌드) 참고)

---

## 사용법

| 명령어 | 설명 | 권한 |
|--------|------|------|
| `/hr` | 말 선택 화면 (경기 중이면 경기장) | `horserace.play` (기본 모두) |
| `/hr bet <말번호> <금액>` | 명령으로 바로 베팅 + 출발 | `horserace.play` |
| `/hr horses` | 말 목록·배당·승률 | - |
| `/hr odds <말번호> <배당>` | 말 배당 변경 (config 에 저장) | `horserace.admin` |
| `/hr horse add <이름> <배당> [아이템] [색코드]` | 말 추가 (최대 5마리) | `horserace.admin` |
| `/hr horse remove <번호>` | 말 삭제 (최소 2마리) | `horserace.admin` |
| `/hr amounts add\|remove\|set` | 판돈 버튼 목록 편집 | `horserace.admin` |
| `/hr reload` | 설정 다시 불러오기 | `horserace.admin` |

별칭: `/horserace`, `/horse`, `/경마`

### 흐름

1. `/경마` → 말 목록 GUI. 각 말에 배당과 승률(전체 말 중 이 말이 우승하는 비율, 전부 더하면 100%)이 표시된다.
2. 말 클릭 → 금액 클릭 → **즉시 경기장 GUI 가 열리고 레이스 시작** (약 12~16초).
   6행 × 9열 상자에서 위 5행이 레인, 안장 아이콘이 결승선(오른쪽)으로 달린다.
3. 우승마가 결승선을 넘으면 끝. 적중이면 원금 × 배당을 바로 받는다.
4. 하단 버튼: **같은 베팅 한 번 더** / **다른 말 고르기** / **나가기**.
   경기 중 GUI 를 닫아도 경기는 끝까지 돌고 결과는 채팅으로 온다.

### 승패 결정

- 말마다 가중치 = **(1 / 배당) ^ odds-power**. 승률 = 가중치 / 전체 합. 전부 더하면 100%.
  **베팅과 무관하게** 매 경기 이 확률대로 우승마가 뽑힌다. 내가 건 말이 더 잘 도착하는 일은 없다.
  기본값(odds-power 2.0) 기준: 1.1배 → 34%, 1.3배 → 24%, 1.5배 → 18%, 1.7배 → 14%, 2.0배 → 10%.
- `odds-power` 를 키우면 배당 높은 말의 승률이 더 가파르게 떨어진다.
- 표시되는 승률이 곧 실제 판정 확률이다. 숨은 보정은 없다.
- 승률이 배당과 독립이라 배당이 낮으면 플레이어가 크게 불리하다. 관리자가 `/hr horses` 를 치면
  말마다 **공정 배당**(기대값 1.0 이 되는 배당 = 1 / 승률)이 같이 나오니 그걸 보고 `/hr odds` 로 맞추면 된다.
- 우승마는 출발 전에 정해지고, 레이스 연출은 다른 말이 먼저 결승선을 넘지 않도록 선 앞에서 붙잡는다.
  랜덤 변동과 스퍼트가 있어서 매번 다르게 보인다.

---

## 설정 (`config.yml`)

```yml
prefix: "&6[경마] &f"
currency: { type: ITEM, material: GOLD_INGOT, display: "&e금괴" }   # VAULT 도 가능
bet-amounts: [1, 5, 10, 25, 50, 100]

race:
  odds-power: 2.0              # 클수록 배당 높은 말 승률이 가파르게 하락
  tick-interval-ticks: 8       # 레이스 진행 주기
  track-length: 100            # 경기 길이

horses:                        # 2 ~ 5마리. /hr odds, /hr horse 로 인게임 편집 가능
  - { name: "번개", color: "&e", material: YELLOW_WOOL, odds: 1.1 }
  - { name: "흑풍", color: "&8", material: BLACK_WOOL,  odds: 1.3 }
  - { name: "백설", color: "&f", material: WHITE_WOOL,  odds: 1.5 }
  - { name: "홍염", color: "&c", material: RED_WOOL,    odds: 1.7 }
  - { name: "청해", color: "&b", material: LIGHT_BLUE_WOOL, odds: 2.0 }
```

---

## 빌드

JDK **25** 가 필요하다. (없어도 Gradle toolchain 이 자동으로 받아온다)

```bash
./gradlew build
```

→ `build/libs/horserace-1.0.0.jar`

> **Gradle 이 JDK 25 에서 실행되지 않는 경우** — Gradle 8.14.3 은 JDK 25 위에서 못 돈다.
> Gradle 실행만 JDK 21~24 로 내리면 된다. 컴파일 타깃은 toolchain 이 25 로 맞춘다.
> ```bash
> ./gradlew build "-Dorg.gradle.java.home=<JDK 21~24 경로>"
> ```
>
> **`Unable to delete directory 'build'` 로 실패하는 경우** — OneDrive·Dropbox 같은
> 동기화 폴더 안에서 흔히 난다. 출력 위치를 밖으로 빼면 된다.
> ```bash
> ./gradlew build -PoutDir=/tmp/horserace-build
> ```

테스트 서버 실행: `./gradlew runServer`

## 구조

```
net.teujaem.horserace
├── HorseRacePlugin        메인. config·재화·말/배당 저장·보류 지급(pending.yml)
├── command/HorseRaceCommand
├── game/                  Bukkit 비의존 순수 로직
│   ├── Horse  (이름/색/아이템/배당)   Runner (출전마 위치·순위)
│   ├── Bet    (말 번호/금액/배당)     Race   (승자 결정 + tick 연출)
├── gui/  BetGui (말 선택) · AmountGui (금액) · RaceGui (5레인 경기장 + 하단 버튼)
├── listener/GuiListener   클릭 해석 + 접속 시 보류 지급
├── service/RaceManager    플레이어별 세션: 베팅 → 틱 → 정산 → 다시 하기
└── util/                  Currency / ItemCurrency / VaultCurrency / Text (블랙잭과 동일)
```

---

## 라이선스

[MIT](LICENSE)
