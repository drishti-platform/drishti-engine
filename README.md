---
# Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved. Drishti Engine. Proprietary: see LICENSE.
name: Drishti Engine
summary: >-
  The engine contract of the Drishti family: a small JDK-only API an engine implements, a kit (TCK) that proves an engine keeps it with
  a JDK and nothing else, and the delivery a host shares (one loader per engine JAR, discovery, the compatibility rule in words).
  An engine is a pure, deterministic, explained function from contract tables to tables, built and certified without any host.
status: 0.1.0-SNAPSHOT, milestone E0/E1 of combined i8; engine-api 0.x until three engines pass (EN-27); not released
coordinates: {groupId: com.ash.engine, package: com.ash.engine (API), com.ash.engine.host, com.ash.engine.tck, bom: com.ash.engine:engine-bom}
java: 21
modules: [engine-api, engine-host, engine-inputs-files, engine-tck, engine-cli, engine-sample, engine-guards, engine-bom]
depends-on: [engine-api the JDK only; engine-host engine-api; engine-tck drishti-tools 1.1.0 (tools-xlsx), Rachana 1.0.0 (rachana-core at level 1.2, the EL oracle), Apache POI (the spreadsheet oracle)]
commands: {build: "cd java && ./mvnw clean verify", kit: "java -jar engine-tck-<v>-cli.jar tck run --engine <jar> --cases <dir> --out <dir>"}
consumers: [Drishti (the host, from H2), Drishti Risk (engine #1), drishti-engines (first-party engines, from M2)]
specification: spec/1/SPEC.md
design: docs/plans/ENGINE_INTEGRATION_DESIGN.md in the Drishti repository
licence: proprietary (LICENSE)
---

# Drishti Engine

**Engines by contract, proven with a JDK only.**

An engine is a JAR made somewhere else. It compiles against `engine-api` (no dependency but the JDK) and, if it wants them, the
family libraries; its kit runs on a laptop with a JDK; a host built before it loads it from its lib folder by `ServiceLoader`, checks
the versions it was built against, refuses in one sentence what does not fit, and binds it by configuration. No engine is compiled
into a host, and adding or upgrading one needs no host build. The normative text is [spec/1/SPEC.md](spec/1/SPEC.md) (draft 3); the
reasoning and the Drishti side are in Drishti's `docs/plans/ENGINE_INTEGRATION_DESIGN.md`.

Author: Ashutosh Sinha. Proprietary. All rights reserved (see [LICENSE](LICENSE)).

## Modules

| Module | Package | What it holds | Depends on |
|---|---|---|---|
| `engine-api` | `com.ash.engine`, `com.ash.engine.text` | `Engine` and `Session` (two interfaces, eight methods); the capabilities `Explaining`, `Incremental`, `Partitioned`, `Assembling`; what a host provides: `EngineContext` (with `peer` and `stamp`), `Inputs` (open tables, the grouped scan), `InputTable`, `Batch` (column arrays by reference), `VectorSink`, `Control` (with `interim`), `MemoryBudget`, `Supplier`; values (`Request`, `Visibility`, `Filter`, `Answer`, `Missing`, `Stamp`, `Explanation`, `Step`, `Changes`, `Shard`, `Partial`); `Table` and `Tables`; `EngineException` with `ENG-` codes; the descriptor model and its strict reader (`Descriptor`, `Descriptors`, `StrictYaml`, `Canonical`) | the JDK only |
| `engine-host` | `com.ash.engine.host` | what every host shares: `EngineJarLoader` (one loader per JAR, platform packages parent-first, everything else child-only), `EngineJar` (registrations matched to descriptors, built-against checks), `EngineLib` (a lib folder read at start: `running`, `off`, `not configured`, `not installed`, `blocked`, each with its words), `Versions` and `Provided` (the one compatibility rule), `Params` (normalisation, `ENG-3001` to `ENG-3003`), `Budget`, `HostControl`, `HostContext`, `Stamping`; the host runtime's generic parts: `EngineFile` (the engine file of section 9, read strictly and held against the descriptor), `StartChecks` (section 9.4: pin, trust, certificate, isolation), `Certificate`, `EngineInstance` (the lifecycle), `Sessions` (one per key, hot, least recently used, stale), `Admission` (`ENG-5001`), `AnswerCache` (by stamp), `Coalescer` (quiet, max-wait), `EngineTemplate` | `engine-api` |
| `engine-inputs-files` | `com.ash.engine.inputs` | inputs with no host, for tests, the kit and the command line (never inside a host): `RowInputs` (rows in memory delivered as a host would: batch size, order, the viewer's rows, grouped scans through a given evaluator, reused and poisoned arrays) and `JsonlFiles` (a folder of `<input>.jsonl[.gz]`, with a column map) | `engine-api` |
| `engine-tck` | `com.ash.engine.tck` | the kit: case files (`Cases`), the kit's host and in-memory inputs, the delivery group and static scan (`Delivery`, `ClassFiles`), the descriptor, contracts and lifecycle groups (`Checks`), the sixteen laws (`Laws`), explanations recomputed by Rachana's evaluator and workbooks recalculated by Apache POI (`ExplainCheck`), the gates (`Gates`), the run and its files (`KitRun`: `results.jsonl`, `report.md`, the certificate), the benchmark run (`Bench`), the command (`Main`), and the JUnit harness (`EngineHarness`). Also built as `engine-tck-<v>-cli.jar`, runnable with a JDK only | `engine-host`, drishti-tools, Rachana core, Apache POI |
| `engine-cli` | `com.ash.engine.cli` | `engine run`: any engine JAR over a folder of JSONL files with no host; tables as JSONL, the answer and its stamp, an explanation and the workbook out. Also built as `engine-cli-<v>-cli.jar` | `engine-host`, `engine-inputs-files`, `tools-xlsx` |
| `engine-sample` | `com.ash.sample.engines` | one JAR, two example engines built the way any engine is: `wmean` (the specification's worked example: explain, workbook, incremental, partition, what-if, progressive) and `group-totals` (read as the caller, grouped at the source, `force-fixed-order`); its kit cases under `tck/cases`; its kit and the drop-in tests run on the packaged JAR in this build | `engine-api`, `tools-xlsx` (both provided) |
| `engine-guards` | | the repository's rules, as tests (below); not published | |
| `engine-bom` | | one version for every module | |

## The kit

```text
java -jar engine-tck-0.1.0-SNAPSHOT-cli.jar tck run   --engine engine-sample-0.1.0-SNAPSHOT.jar --cases engine-sample/tck/cases --out target/kit
java -jar engine-tck-0.1.0-SNAPSHOT-cli.jar tck bench --engine engine-sample-0.1.0-SNAPSHOT.jar --cases engine-sample/tck/cases --out target/bench.jsonl
```

It needs a JDK 21 and nothing else: no host, no network, no Drishti artifact. It opens the JAR through the same `EngineJarLoader` a host
uses, so what passes here loads there. A run writes, per engine, `results.jsonl` (one line per case, law and check; no timing, so two
runs give the same bytes), `report.md`, and, when everything held, `<jar>.certificate.json`.

| Group | What it proves |
|---|---|
| delivery | the JAR loads through `EngineJarLoader`; registrations and descriptors agree; no platform class bundled; every class reference resolves in the JAR, the platform packages or the JDK; the descriptor states the API and family versions the JAR references; no host type named |
| scan | no network, process, exit, thread or executor, clock, environment, unseeded random or reflection call; no mutable static state (a lint, not a sandbox) |
| descriptor | the schema's rules (at read) and those it cannot say (section 6.4); `Engine.descriptor()` equals the file; capabilities equal the interfaces |
| contracts | a recording `Inputs` refuses undeclared columns; tables in the order asked, each exactly its output's schema, no NaN in a non-null number |
| cases | each case's `expect`, doubles by raw bits |
| laws | repeat, threads, batching, order, filter-equivalence, interleaving, incremental, additivity, absence, seed, explain, overlay, pushdown, interim, isolation (binding: an engine that lists `process` fails this kit) |
| explain | every measure cell explained; every formula recomputed by Rachana's evaluator; the workbook's defined names recalculated by Apache POI and equal to the cells; cached values equal; the same bytes twice |
| lifecycle | init, start, suspend, resume and shutdown: every legal transition, illegal ones `ENG-5020`, a suspended engine `ENG-5023` and suspend then resume keep answers bit-identical and cheap; shutdown is harmless twice and leaves no thread or file; a closed session holds nothing; two instances apart; cancel and deadline within the declared latency; a one-byte budget is `ENG-5002` in words and the engine stays usable |
| gates | coverage (outputs, enumerated values, every declared column read, own codes, `NOT_FOUND` per required input); additive only (`--previous`); oracles (every case has a `source`); stable (the run twice, one set of lines) |

The **harness** is for an author's own unit tests: `EngineHarness.of(MyEngine.class).snapshot(..).rows(input, rows).open()` gives a
session to ask, explain and apply changes to, with no JAR and no case file. `EngineHarness.kit(MyEngine.class, cases, out)` runs every
group but delivery on the class path.

## Writing an engine

1. Write the descriptor at `src/main/resources/META-INF/drishti-engine/<id>.engine.yaml` (`product:` the product id a licence grants; `api: "0.1"`; `platform:` for each family
   library the JAR references).
2. Implement `Engine` and `Session` (and a capability per need); register the class in
   `META-INF/services/com.ash.engine.Engine`. The constructor does no work; `descriptor()` is `Descriptors.of(MyEngine.class, "<id>")`.
3. Build with `engine-api` (and any family library) in scope `provided`, private libraries merged into the JAR, and the manifest entry
   `Engine-Api: 0.1`. `engine-sample/pom.xml` is the template.
4. Unit-test on the harness; write cases under `tck/cases/<id>/`; run the kit on the packaged JAR (`engine-sample` does it with
   failsafe: `SampleKitIT`).

## The rules (engine-guards)

| Guard | Rule |
|---|---|
| `EngineApiHasNoDependencyTest` | engine-api's POM names no dependency and its sources import the JDK and `com.ash.engine` only; engine-host adds engine-api and nothing else |
| `NamesNoHostTest` | no source, resource or case names `com.ash.drishti`, a `drishti.` setting, Spring or a servlet type (the kit's list of host types it refuses is the one exemption) |
| `ApiSurfaceTest` | `java/<module>/api-surface.txt` is the module's public surface; it only grows within a major (`-Dengine.api.surface.write=true` rewrites it in the same commit) |
| `NoMutableStaticsTest` | no static field that is not final, and no static final array or mutable collection, in any module |
| `LicenceHeaderAndLineCapTest` | the copyright header on every source; 2,500 lines at most (the family's one constant, from the tools guard kit) |
| `LaunchersTest` | the IDE launchers follow the platform launcher standard and the workspace mirrors them |

The reactor also bans, in any scope, `com.ash.drishti*`, Spring and servlet artifacts (enforcer).

## Install and build

The family installs in this order: **Rachana (tag) -> Drishti Tools (tag) -> Drishti Lake (tag) -> Drishti Engine -> Drishti**. A
consumer builds against a released tag installed into `~/.m2/repository/com/ash/engine/`, never against a branch.

```text
cd java && ./mvnw clean verify                          # JDK 21 or later: every module, the kit on the sample JAR, the drop-in tests, the guards
git checkout vX.Y.Z && cd java && ./mvnw clean install -DskipTests     # install a release (tag only)
```

## Build and run in the IDE

Launchers live in `run-configs/` (IntelliJ: open the repository root and link `java/pom.xml`; VS Code: open `drishti-engine.code-workspace`).

| Launcher | What it does |
|---|---|
| `Engine: build all` | every module, no tests |
| `Engine: test` | the full build with every test, the sample's kit and the guards |
| `Engine: check guards` | the guards only |
| `Engine: maintain API surface (rewrite)` | rewrites the `api-surface.txt` files (additions) |
| `Engine: install release (tag only)` | the jars into the local repository, on a release tag only |
| `Engine: run kit (sample)` | the kit's command on the sample JAR (Debug works on it) |
| `Engine: bench kit (sample)` | the benchmark run on the sample's reference asks |

## Not in 0.1

`engine-gen` (generated pages, screens, workbooks from steps), `engine-inputs-lake` (needs Lake's read API), the binder by contract and the triggers' firing (Drishti's side, H2), the process
binding and its law, supplier ports and peers in the kit, a case's `file:` inputs, and `memoryBytes()` against a heap measurement. Each
is named in the kit's report where it would apply.
