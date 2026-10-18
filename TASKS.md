# OpenSageTV Vibe XMLTV Import tasks

> **Pre-commit task maintenance:** Immediately before every repository commit, move
> completed `[x]` items out of active sections and into
> `## Checklist change ledger`. Preserve IDs, evidence, and context; never
> discard completion history. Active sections contain unchecked work only.

This is the only active XMLTV backlog. Completed work moves to the checklist
change ledger; release evidence is also recorded in `CHANGELOG.md` and
`HANDOFF.md`.

- [ ] **XMLTV-PLUGIN-004 - Physical release qualification.** Before a binary
  release/catalog submission, qualify the Windows installer/filesystem and the
  actual stock/SageMC STV Configure presentation with generated data, then
  obtain publication approval. Isolated Linux stock-JAR/API evidence is not
  Windows or physical STV proof. No private feed or existing lineup mutation.
- [ ] **XMLTV-PLUGIN-005 - Retire legacy Core/container registration policy.**
  Original parent: replace Vibe Core's automatic importer discovery/property
  repair with the stock-compatible Standard wrapper. The alternative now works
  on stock Core; this task still owns removing the old optional Vibe Core and
  container upsert after their deployment/migration lane is qualified. Do not
  infer removal from the new plugin or silently change existing container
  settings. Preserve valid third-party importers, restart/disable/uninstall
  rollback, and stock/older-client fallback; no simultaneous multi-importers.
- [ ] Perform final clean-server provider/profile commissioning with all
  supported sample feeds and record lineup/logo/ShowID results.
- [ ] Add any newly encountered provider-specific parsing behavior only with a
  redacted committed fixture and regression test.

## Checklist change ledger

- [x] **XMLTV-PLUGIN-001 - Standard plugin lifecycle and settings (2026-10-18).**
  Implemented stock SageTVPlugin wrapper, public Get/Set/RemoveServerProperty,
  empty-only registration, journalled ownership/restore, administrator/legacy
  XMLTV preservation and missing-only resource seeding. Ten Configure entries
  provide explicit managed-provider Save/Reload/profile/logos/optional TMDB;
  no implicit import/lineup selection.30stock-JAR checks plus actual public
  manager lifecycle pass. Original generic policy-retirement work remains
  separately005: no speculative obsolete-class replacement, third-party
  override, Core mutation or simultaneous multi-importer claim.
- [x] **XMLTV-PLUGIN-002 - Rename and packaging (2026-10-18).** GitHub/local
  project renamed to opensagetv-vibe-xmltv-Plugin, origin corrected and active
  build/container/registry/handoff URLs updated. Retain established importer
  class/JAR/internal container mount names. Deterministic Java8 JAR/one-JAR ZIP,
  Standard-plugin public/dev descriptors, checksums, source manifest and host
  contracts. JDK11 explicit and compile-only stockCore d76ded98, no Sage/test
  classes packaged. Build-env contracts/twelve-repo handoff self-test pass;
  named caches/persistent ADB identity retained, no image rebuild.
- [x] **XMLTV-PLUGIN-003 - Qualification/migration (2026-10-18).** Complete
  importer suite including failure/parser/metadata/profile/logo/legacy-v2 IDs,
  TMDB and generated500channels;30new lifecycle checks and7host contracts pass.
  Isolated actual stock SageTV plugin manager discovers/installs/enables the
  final JAR, exposes settings, registers XMLTV, discovers saved provider,
  survives a real server restart, disables/re-enables, preserves a third-party
  importer and uninstalls while retaining provider/profiles. StockCore d76ded98
  before/after. Final JARfb01c879/ZIP43bc63b3. Corrected two test oracles only:
  stock IDs are lowercase and Core may materialize an empty importer default.
  No parser/runtime change to hide those measurements. Compact results under
  artifacts/results/XMLTV-PLUGIN-003; no running test/production server touched.
  Windows/physical STV/private-feed replay/publication remain distinct004 gates.

- 2026-10-08 pre-commit source-sync review: workflow policy only, no importer,
  profile, guide, Show-ID or server changes. Existing migration/commissioning
  tasks remain unchecked; completed entries ledger-only. Common workflow and
  source-layout checks apply; workspace execution order reviewed305.
