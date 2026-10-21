# Handoff

## Canonical repository casing (2026-10-21)

PLUGIN-RENAME-001 uses opensagetv-vibe-Xmltv-Plugin on GitHub and disk.
Metadata, future release URLs and package tests follow that name; installed
plugin/importer IDs, JAR, profiles and settings remain unchanged. Seven host
contracts and metadata validation pass; regenerate/check the source manifest
before commit. Existing physical-release gates and publication approval remain
open. A repository rename does not authorize a binary/catalog release.

## XMLTV Standard-plugin conversion complete (2026-10-18)

GitHub/local root renamed to opensagetv-vibe-xmltv-Plugin; origin points at the
new name, history retained.3.6.0 packages a real Standard wrapper with guarded
public stock-property registration, Config screen and explicit managed-provider
Save/Reload. Legacy xmltv.XMLTVImportPlugin and XMLTVImportPlugin.jar remain
unchanged names; profiles/IDs/third-party importers are preserved. Missing
profiles/examples seed from credential-free JAR resources; examples are not
active providers. See README migration/disable/uninstall semantics.

Default compilation explicitly uses JDK11/Java8 against unmodified stock
Sage.jar d76ded98, not the Vibe Core artifact. Final JAR SHA256
fb01c879efec0d05f9945bebb8a8c0d79a7a72c44a04b71f50acfa5c54ce6df7;
installableZIP43bc63b3207f1b63e062f47b947c30af438dff47a6f6dadafa07ac0cd277f8f0.
Full existing importer suite,30new lifecycle checks,7host contracts and
actual isolated stock plugin-manager install/settings/provider/restart/
disable/re-enable/conflict/uninstall pass. Retain meaningful compact results
and package, retire corrected raw. Native runtime comes from local Linux build;
this is not a Windows/physical-STV/private-corpus replay or a changed175/232.

Two integration assertions were corrected from observed stock behavior:
plugin identifiers are lowercase; initial Core EPG setup may create an empty
property before this plugin owns it. No production behavior was changed to
conceal those harness failures. Before public binary/catalog release finish
XMLTV-PLUGIN-004 and request explicit approval (PUBLISH_APPROVED=false).
Build-env/container references follow the rename; internal mount unchanged.
Legacy Vibe Core auto-discovery/container upsert are not removed by this
conversion. Their separately qualified migration/retirement is005; the new
Standard wrapper does not depend on them and preserves legacy ownership.
Earlier3.5/container-owned registration descriptions below are historical.

## Standard takeover

Read `AGENTS.md`, `README.md`, `TASKS.md`, and `WORKFLOW.md`, then use the common
root commands. Changed-files packages live in `artifacts/downloads`; the tested
JAR is installed only by the container staging/release workflow.

## Build and artifact

Run `opensagetv-vibe-dev.sh xmltv` from the sibling
`opensagetv-vibe-build-env`
repository. On Windows run:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\opensagetv-vibe-dev.ps1 xmltv
```

The unified Ubuntu 26/OpenJDK 11 environment consumes the locally built Core
`Sage.jar`, targets Java 8 bytecode, runs the complete suite, and writes
`output/packages/XMLTVImportPlugin.jar`. The current plugin is version 3.5.
The supported build never starts/stops SageTV and never removes `Wiz.*`, logs,
properties, or user data.

## Runtime behavior

Provider discovery needs no legacy EPG license. With no configuration,
`getLocalMarkets()` returns placeholder provider `867507149 / XMLTV Lineup`, so
the setup UI can offer XMLTV before a provider file exists. The container owns
the safe `Sage.properties` upsert; the JAR does not mutate it.

Version 3.4 reports guide completion truthfully. A required feed, parser,
`run.before`, or SageTV database error makes `updateGuide()` return `false`, and
the plugin does not call `setLineup()` after a partial failure. Feed downloads
are bounded, status checked, gzip aware, conditionally cached, and atomically
replaced. SAX readers accept the common XMLTV DTD declaration without loading
the external DTD and reject inline/external entity expansion. Configuration
secrets and URL credentials/queries are redacted from logs.

The new boundaries are `XmltvConfiguration`, `FeedDownloader`,
`SecureXmlReader`/`XmltvParser`, `XmltvDateParser`, `ExternalCommandRunner`,
`ChannelMapper`, `ProgrammeMapper`, `SageGuideWriter`, `ImportResult`, and
`ShowIdAudit`. See `docs/XMLTV_MODERNIZATION.md` for design and regression
coverage. The build currently emits no unchecked raw-collection warnings.

An optional, disabled-by-default `TmdbEnricher` now fills only missing
programme metadata through the public facade of the standalone
`opensagetv-vibe-tmdb` plugin. It owns no credential, HTTP, or SQLite behavior.
The importer creates the legacy/v2 Show ID before enrichment, deduplicates and
bounds requests, and treats missing service, no match, ambiguity, rate limit,
offline state, and request failure as non-fatal. Unit tests and an end-to-end
TMDB-off/on import prove that source values win, both synthetic programmes are
enriched only when enabled, and their exact Show IDs do not change.

Version 3.5 adds optional configuration profiles without changing legacy
resolution when `xmltv.profile` is absent. Profiles and `common.properties`
live beside `Sage.properties`. Resolution order is built-in defaults, ordinary
includes/common, selected profile, then the active `.xmltv.properties` file.
`xmltv.profile=auto` atomically creates a provider-named profile and changes
only the selection line; the existing provider settings remain authoritative.
The container seeds missing shipped profiles but never overwrites a user's
existing root-level profile.

For read-only commissioning against a server directory, run the compiled test
helper from that directory with the plugin and Sage JARs on the classpath:
`xmltv.ProfileRuntimeProbe CONFIG_FILE PROVIDER_ID PROVIDER_NAME`. It verifies
provider discovery and prints the resolved channel/profile fields.

## Show identities

`legacy` remains the default Show-ID strategy and is protected by exact-output
tests. Opt-in `v2` uses provider-scoped SHA-256 identities and atomically
persists show/series mappings to `xmltv-show-id-v2.properties`. Preserve that
file during backup/restore, and never silently switch an established lineup.
`xmltv.show_id.display=description|bonus` exposes final IDs temporarily.

Before migration, run:

```bash
./scripts/show-id-audit.sh --provider-id 999 /path/to/guide.xml > audit.tsv
```

It is read-only unless `--write-map` is supplied. A detected v2 collision exits
with status 2. See `docs/SHOW_ID_AUDIT.md` for the identity policy.

## Channel logos

Both `sagetv.channel.IconDownload` and the historical
`xmltv.channel.IconDownload` key work. Images are downloaded with time/size
limits, decoded independent of extension, normalized to PNG, aspect-preserving,
and bounded to 256x256 by default. Failures do not abort the guide import.

Historical Unraid commissioning on 2026-08-26 used plugin 3.2 and a 107-channel
private feed: 107/107 logos normalized successfully. This is historical
evidence, not a claim that private data was rerun for version 3.5.

## Validation status

The version 3.5 local suite passes secure acquisition/parser tests, provider
reload, failure injection, metadata mapping, multi-source imports, Show-ID
audit/identity tests, profile compatibility/precedence/auto generation, channel
logos, and a generated 500-channel stress lineup.
The same suite also passes the optional TMDB adapter unit and full-import
identity/fail-open gates. A live credentialed programme-facade smoke test is
owned and passed by the standalone TMDB project; no private credential enters
this repository or its artifacts.
Private corpus files are not retained. Their earlier results are documented in
`docs/PRIVATE_CORPUS_TEST_REPORT.md` and must not be presented as a 3.5 replay.

## Unraid commissioning result

Version 3.5 previously passed a private 107-channel commissioning run. The
private provider name, paths, feed, logs, credentials, and appdata are not part
of this repository. The compatibility cases learned from that run—standard DTD
declarations, offset-free timestamps, and duplicate displayed channel
numbers—are represented by committed synthetic regression fixtures.

Use `docs/UNRAID_PROFILE_COMMISSIONING.md` for a clean deployment. Component
updates and rollback are appdata-based and do not require rebuilding the
container image.
