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
- Rachana 1.0.0 (level 1.2) is the kit's EL oracle. Descriptors name their `product`; a host's `Licence` blocks an engine whose product
  it does not grant (`ENG-1014`, one sentence), beside the version check; the format of the licence file stays pluggable. The exact-path
  option is declared (`forces: exact-path`), and the kit's pushdown law reads the declaration. The specification copy here is amended.
- Engine lifecycle (owner requirement): `Engine` has `init`, `start`, `suspend`, `resume` and `shutdown` (replacing `open` and `close`);
  `LifecycleState` (`NEW`, `INITIALISED`, `RUNNING`, `SUSPENDED`, `FAILED`, `SHUTDOWN`); `EngineInstance` runs the sequence, gates requests
  and sessions to `RUNNING`, drains or cancels calls in flight at suspend (`ENG-5023` "suspended" for new ones), shutdown in any state;
  the kit's lifecycle law (`Lifecycle`) proves it; both sample engines implement it; specification 7.1 rewritten, `ENG-5023` added.
- Guards, launchers per the platform launcher standard, README, LICENSE.
