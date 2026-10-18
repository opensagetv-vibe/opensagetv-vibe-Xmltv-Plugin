# Common project workflow

## Task fix and server-boundary policy

Apply this policy to every task workflow, including dependency fixes across
Vibe repositories. Fix and test necessary plugins and update the test-server
plugin without asking again solely for repository-boundary approval.

Prefer the Android client, then a stock-compatible plugin. Change non-stock
`.232` Core only for a proven production defect that neither can correct;
document the API gap and alternatives, keep optional negotiation and safe
stock/older-client fallback, and run affected compatibility tests. Never patch
Core merely to simplify testing.

Stock `.175` installation changes are limited to plugin installation/update.
Do not modify its stock Sage.jar, stock FFmpeg, Core binaries, or server
installation/configuration files. Preserve user settings, recordings and
unrelated clients; reversible supported SageTV playback APIs remain allowed.

Non-stock `.232` restarts are authorized for task updates without asking
again; coordinate them with active test guards and preserve data/settings.
  Always ask the user before restarting stock `.175`, even when it appears idle,
  unless an explicit user-granted bounded restart window is active. Record
  its UTC expiry in the task/handoff, and check expiry and revocation before
  every restart. After expiry or revocation, ask again; stock files stay protected.

Update owning TASKS.md, linked dependencies and the workspace suggested order
as work changes; move completed checkoffs into the checklist change ledger.
Test only affected gates, preserve unrelated completed matrices, and do not
stop independent authorized work for a status question or a dependency-only
permission request. Unrelated work, publication, destructive actions and
interruption of recordings/other users still require their own authority.

Run `dev.cmd` or `./dev.sh` with `test`, `validate`, `build`, `package`, `install`,
or `all`. Build/package use the unified container, explicitly select JDK11 and
compile Java8 classes against the unmodified stock JAR at the sibling FFmpeg
plugin's `.deps/stock/Sage.jar` (or explicit SAGETV_JAR/SAGE_JAR). Never qualify
this plugin against a modified Core JAR. `install` is artifact-only SKIPPED,
not an implicit deployment or restart of any running server.

The installable Standard-plugin ZIP has exactly one `XMLTVImportPlugin.jar`,
preserving the established filename and `xmltv.XMLTVImportPlugin` EPG class.
Metadata, development descriptor, reproducible JAR/ZIP, package report and
checksums live under `output/packages`. Resources contain only checked-in
profiles and credential-free examples, never live feeds/settings/caches.
`scripts/package.py package --require-approved` refuses publication metadata
unless PUBLISH_APPROVED=true; builds and the repository rename do not grant
catalog/binary-release approval.

Before committing, stage deliberate source, regenerate/check
`scripts/project_manifest.py --write/--check`, run source/package tests and
`git diff --check`, and move completed task checkoffs into the ledger.

`scripts/stock_plugin_gate.py` runs a one-shot assertion via stock SageTV's
supported startup-Runnable and public plugin APIs in system-created temporary
appdata inside the existing dev container. It validates real discovery/install/
settings/provider discovery/restart/disable/re-enable/conflict/uninstall and
Core hashes, never production servers or private Core fields. No open-ended
control endpoint is added. Local native/JAR evidence is not Windows hardware,
an STV screenshot or a private-feed replay. Keep compact results and retire
corrected raw captures/logs. Normal runtime does not require this probe or MCP.

Put update ZIPs in `artifacts/downloads`, run `update.cmd`/`update.sh`, and use
`create_ai_handoff_zip.cmd` to create the verified changed-files package. See
the sibling build environment's `WORKFLOW.md` for the common format.
