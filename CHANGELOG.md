<!-- Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved. Drishti Engine. Proprietary: see LICENSE. -->
# Changelog

## 0.1.0 (unreleased)

The repository, milestone E0/E1 of combined i8 (ENGINE_INTEGRATION_DESIGN section 14), built on the accepted decisions EN-1 to EN-55
and the specification's draft 3 (`spec/1/SPEC.md`).

- `engine-api` 0.1, JDK only: `Engine`, `Session`, the capabilities, the host-side interfaces with the EN-46 additions
  (`Inputs.table(input, table)`, `InputTable.offers` and `groups`, `Control.interim`, `EngineContext.peer`), `Batch` as column arrays
  by reference, the values, `Table` and `Tables`, `EngineException`, the descriptor model and its strict YAML reader.
- `engine-host`: `EngineJarLoader`, `EngineJar`, `EngineLib` (lib folder states and words), the compatibility rule and its sentences,
  parameter normalisation, budget, control, context, stamps.
- `engine-tck`: cases, the kit's host, delivery and static scan, descriptor, contracts, sixteen laws, explain and workbook oracles
  (Rachana core, Apache POI), lifecycle, gates, results, report, certificate, `tck run` and `tck bench`, the JUnit harness, and the
  runnable `-cli` JAR.
- `engine-sample`: `wmean` and `group-totals` in one JAR with their cases; the kit and the drop-in tests on the packaged JAR.
- `engine-host` runtime parts (E3, the generic ones): the engine file and its start checks, the certificate, the instance lifecycle, the
  session pool, admission, the answer cache, coalescing, the engine-file template.
- `engine-inputs-files` (rows in memory as a host delivers them; JSONL folders) and `engine-cli` (`engine run`, E5); the kit's inputs
  moved there (one way).
- Guards, launchers per the platform launcher standard, README, LICENSE.
