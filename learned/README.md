# Learned Patterns

ManySStuBs4J (`sstubsLarge`, 63,923 bug-fixes) 에서 anti-unification + hierarchical clustering 으로 학습한 패턴들. Mutation 팀에서 swap(LHS↔RHS) 해서 mutation operator로 사용.

## 파일

```
learned/
├── README.md                    ← 이 파일
├── patterns-full.json           ← 16 type 통합, 10,389 패턴, 76 MB
└── per-type/<BUG_TYPE>.json     ← 16개 분리 파일
```

`patterns-full.json` 한 파일만 써도 충분. `per-type/` 은 type별로 따로 다루고 싶을 때.

## JSON 구조

```json
{
  "meta": {
    "dataset": "data/sstubsLarge.json",
    "totalCohortSize": 63923,
    "totalLearnedPatterns": 10389
  },
  "runs": [
    {
      "label": "SWAP_BOOLEAN_LITERAL",
      "cohortSize": 1842,
      "patternCount": 181,
      "patterns": [
        {
          "rank": 0,
          "support": 939,
          "specificity": 1.0,
          "score": 939.0,
          "before": { "type": "BooleanLiteral", "label": "true",  "children": [] },
          "after":  { "type": "BooleanLiteral", "label": "false", "children": [] }
        },
        ...
      ]
    },
    ...
  ]
}
```

### Tree 노드 표현

두 종류뿐:
- **TreeNode**: `{ "type": "...", "label": "...", "children": [...] }`
- **Hole** (변수 자리): `{ "hole": "?h0" }`

같은 hole id (예: `?h0`) 가 LHS와 RHS 양쪽에 등장하면 같은 sub-expression을 가리킴.

예 — `assertEquals(x, y) → assertEquals(y, x)` (인자 swap):
```
LHS: MethodInvocation[SimpleName(assertEquals), ARGS[?h0, ?h1]]
RHS: MethodInvocation[SimpleName(assertEquals), ARGS[?h1, ?h0]]
```

### Mutation operator로 쓰는 법

원래 학습은 `bug → fix` 방향 (LHS = bug, RHS = fix). Mutation은 그 **반대**:
- 패턴의 `after` 가 mutation 입력 (정상 코드 매칭)
- 패턴의 `before` 가 mutation 출력 (mutant)

즉 `swap(after, before)` 하고 LHS를 매칭, RHS로 치환.

## 필드 의미

| 필드 | 의미 |
|---|---|
| `support` | 이 패턴이 학습된 fix 개수 |
| `specificity` | `1 - holes/nodes`. 1.0 = 완전 concrete, 0 = 거의 다 hole |
| `score` | `support × specificity`. 정렬용 |
| `cohortSize` | 해당 bugType 의 fix 총수 (필터에 쓰일 분모) |

`patterns` 는 `score` 내림차순 정렬됨.

## 추천 필터 (선택)

> 필터는 **선택사항**입니다. Mutation operator 관점에선 매칭 안 되는 패턴은 자동 도태되어 (fire 안 함 → mutant 생성 안 함) dead weight 일 뿐 손해 거의 없음. 10,389 개 모두 그대로 써도 무방.

- `support >= 20` — 최소 20 fix 사례
- `support / cohortSize >= 0.005` — 한 프로젝트만의 우연 제외
- `specificity >= 0.2` — hole 너무 많은 degenerate 제외

Identifier-rename 계열 (`CHANGE_IDENTIFIER`, `CHANGE_CALLER_IN_FUNCTION_CALL`, `CHANGE_OPERAND`) 에는 추가 룰:

- `specificity < 1.0` 이거나 `support >= 100` — 100% concrete identifier rename 은 well-known API 만 살림 (project-specific 변수명 컷)

이 필터로 ~250개로 줄어듦. 필요에 맞게 조정.

## Type별 특징 (mutator 후보 품질)

| BugType | Patterns | Mutator 품질 | 비고 |
|---|---:|---|---|
| **SWAP_BOOLEAN_LITERAL** | 181 | ★★★ | `true ↔ false` 우세. PIT `TRUE_RETURNS` 와 중복 |
| **CHANGE_OPERATOR** | 83 | ★★★ | `> ↔ >=`, `!= ↔ ==`. PIT `CONDITIONALS_BOUNDARY` 등과 중복 |
| **CHANGE_NUMERAL** | 1241 | ★★ | `0 ↔ 1` 외 long-tail 은 project-specific 상수 |
| **CHANGE_MODIFIER** | 112 | ★★ | JDT 의 modifier bit flag 변경 (NumberLiteral). `1 → 33` = `private → public static final` 같은 의미 |
| **SWAP_ARGUMENTS** | 198 | ★★★ | `assertEquals(?h0,?h1) ↔ (?h1,?h0)` 같은 generic template 우수 |
| **OVERLOAD_METHOD_DELETED_ARGS** | 279 | ★★★ | `args[?h0,?h0] → [?h0]` 같은 argument 제거 template |
| **OVERLOAD_METHOD_MORE_ARGS** | 14 | ★ | 거의 project-specific |
| **CHANGE_IDENTIFIER** | 5362 | ★ | 대부분 project-specific 변수 rename. Top 일부만 generic API rename |
| **DIFFERENT_METHOD_SAME_ARGS** | 2329 | ★★ | API rename (selenium, reflection) 다수. project-specific 도 섞임 |
| **CHANGE_CALLER_IN_FUNCTION_CALL** | 355 | ★ | 거의 project-specific receiver 교체 |
| **CHANGE_OPERAND** | 225 | ★ | `mFoo == null → mBar == null` 류, project-specific |
| **MORE_SPECIFIC_IF** | 5 | ★ | if condition 이 너무 깊고 unique 해서 거의 안 묶임 |
| **LESS_SPECIFIC_IF** | 5 | ★ | 위와 동일 |
| **ADD_THROWS_EXCEPTION** | 0 | — | snippet 이 throws clause 라 학습 불가 |
| **DELETE_THROWS_EXCEPTION** | 0 | — | 위와 동일 |
| **CHANGE_UNARY_OPERATOR** | 0 | — | wrap 노이즈로 cluster root 가 hole 되어 모두 degenerate AU |

## 알려진 한계

1. **PIT 기본 mutator 와 중복**: 상위 패턴 다수는 PIT 가 이미 제공 (`true↔false`, `>↔>=`, `! 부정`). Novel value 는 mid-tier.
2. **Project-specific identifier 노이즈**: 같은 변수명이 한 프로젝트에서 수십 번 등장하면 high support 받음. 위 필터로 어느 정도 거름.
3. **Multi-granularity 표현**: 같은 변경이 leaf (e.g., `SimpleName(x) → SimpleName(y)`) + 더 큰 context (e.g., `MethodInvocation[..., SimpleName(x), ...] → ...`) 둘 다 학습됨. 둘 다 살릴지 leaf 만 살릴지 선택.
4. **Mutation safety**: 학습 단에서 "LHS holes ⊆ RHS holes" 보장됨. swap 후에도 새 hole 생성 안 됨.
5. **No-op 필터 적용됨**: `before == after` 인 패턴은 학습 단에서 제거.
6. **Wrap context 제거됨**: `class _Wrapper_ { void _m_() { ... } }` 같은 학습용 wrap 은 GumTree diff 이후 stripping. 패턴 트리에 wrap 노드 없음.

## 로딩 예시 (Java + Jackson)

```java
ObjectMapper mapper = new ObjectMapper();
JsonNode root = mapper.readTree(new File("learned/patterns-full.json"));
for (JsonNode run : root.get("runs")) {
    String bugType = run.get("label").asText();
    int cohort = run.get("cohortSize").asInt();
    for (JsonNode p : run.get("patterns")) {
        int support = p.get("support").asInt();
        double spec = p.get("specificity").asDouble();
        if (support < 20 || (double) support / cohort < 0.005 || spec < 0.2) continue;
        JsonNode lhs = p.get("before");
        JsonNode rhs = p.get("after");
        // swap for mutation: match `rhs`, replace with `lhs`
        registerMutator(bugType, rhs, lhs);
    }
}
```

## 학습 파라미터 (현재 산출물)

이 폴더의 `patterns-full.json` 은 다음 설정으로 학습됨:

| 파라미터 | 값 | 설명 |
|---|---|---|
| Dataset | `data/sstubsLarge.json` | ManySStuBs4J 1000-projects 변형, 63,923 fix |
| Cohort | 풀스케일 | 16 bugType 전부, 각 type 의 모든 fix 사용 (limit 없음) |
| `minSupport` | **2** | 최소 2 fix 에서 매칭돼야 cluster 유지 (`PatternLearner` 생성자) |
| `maxHoles` | **4** | 한 패턴이 가질 수 있는 hole 수 상한 — cluster 머지 컷오프 (`HierarchicalClusterer`) |
| `maxBucketSize` | **1500** | 한 bucket 이 이 크기 초과면 random sub-bucket 으로 분할 (메모리 폭증 방지) |
| No-op 필터 | on | `before.equals(after)` 인 cluster 제거 (`PatternLearner.learn`) |
| Mutation-safety 필터 | on | `LHS holes ⊆ RHS holes` 만 유지 (swap 후 unbound hole 없음) |
| Wrap stripping | on | `class _Wrapper_ { void _m_() {…} }` 제거 후 학습 (`GumTreeDiffEngine.stripWrap`) |
| JVM heap | `-Xmx5g` | per-process |
| 병렬도 | `PARALLEL=2` | 2 JVM 동시 실행 |
| Wall time | ~40 min | 10-core M-series, 16 GB RAM |

## 재학습 (선택)

다른 임계값으로 다시 학습하고 싶으면:

```bash
rm -rf learned/per-type learned/patterns-full.json
PARALLEL=2 bash tools/train-parallel.sh    # ~40min, JDK17 필요. per-type/<TYPE>.json 16개 산출.
```

per-type 16 파일을 한 파일로 머지하는 건 별도 단계 (현재는 외부 스크립트로 처리).

파라미터 변경: `LearnCommand` 옵션 (`--min-support N`, `--max-holes M`, `--limit K`) 또는 코드의 `DEFAULT_*` 상수.

기본 파라미터: `minSupport=2`, `maxHoles=4`, bucket-cap 1500. 변경하려면 `PatternLearner` / `HierarchicalClusterer` 생성자 인자 또는 `LearnCommand` 옵션 (`--min-support`, `--max-holes`) 참고.
