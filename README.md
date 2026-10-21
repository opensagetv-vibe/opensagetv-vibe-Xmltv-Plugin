# OpenSageTV Vibe XMLTV Plugin

Repository: https://github.com/opensagetv-vibe/opensagetv-vibe-Xmltv-Plugin

Version3.6.0 adds a real SageTV **Standard plugin** around the existing XMLTV
EPG importer. It builds against an **unmodified stock Sage.jar**, targets Java8
bytecode, and uses only public SageTV property APIs. Linux/Windows packages use
the same JAR; no Core modification, Android client, FFmpeg or MCP plugin is
required. This conversion is not a public release/catalog submission yet.

Run `./opensagetv-vibe-dev.sh xmltv` from the sibling
`opensagetv-vibe-build-env`
repository. On Windows use `powershell -NoProfile -ExecutionPolicy Bypass -File
.\opensagetv-vibe-dev.ps1 xmltv`. Output is under `output/`.

The consistent AI takeover/update interface is documented in
[`WORKFLOW.md`](WORKFLOW.md); its root launchers work from any caller directory.

Version 3.5 treats import completion truthfully: an unavailable or malformed
feed, rejected SageTV database call, failed required `run.before` command, or
incomplete configuration makes `updateGuide()` return `false`. The existing
lineup is not replaced after a failed import.

Installation can register `xmltv.XMLTVImportPlugin` so XMLTV appears as an EPG
option without any legacy SageTV license key. No lineup is selected and no
guide import occurs until the user chooses a provider. If configuration is
missing, a safe `XMLTV Lineup` placeholder remains selectable while examples
are available under `.config/xmltv-examples`.

## Installation and setup

The plugin-manager identity is `OpenSageTVVibeXMLTVPlugin`, with implementation
`xmltv.XMLTVStandardPlugin`, requiring SageTV9.2.2+. The existing importer class
and `XMLTVImportPlugin.jar` filename stay unchanged to prevent duplicate class
copies during an upgrade from manual/container installation.

1. Build using `dev.cmd all` or `./dev.sh all`. Installable ZIP, public/development
   descriptors, JAR and SHA256SUMS are in `output/packages/`.
2. For development commissioning, use SageTV's existing development-plugin
   mechanism: place the generated ZIP and `*-dev.xml` under the server's
   `SageTVPluginsDev.d` with developer mode enabled, then refresh the plugin
   list and install **OpenSageTV Vibe XMLTV** through Available Plugins.
   A loaded-JAR update may require an idle restart. Do not modify live properties
   files or install a second copy of the importer under another filename.
3. Open Installed Plugins > OpenSageTV Vibe XMLTV > Configure. Registration is
   enabled by default **only when no importer is configured**. An existing XMLTV
   registration is preserved; any different importer is left untouched and
   reported as a conflict. No classname is guessed to be obsolete.
4. Existing `xmltv.properties`, `*.xmltv.properties`, profiles and Show-ID maps
   remain authoritative. For a new provider, enter its name, a unique ID1-998,
   local/HTTP(S) source and profile, then choose **Save provider settings**.
   This explicitly creates only `vibe-plugin.xmltv.properties`; enabling the
   plugin never creates an active feed, selects a lineup or starts an import.
5. Select the provider through SageTV's normal guide setup. No license key is
   needed. Monitor `xmltv.log` and SageTV's server log for import results.

Read-only status does not open a keyboard. Profile/logos/optional TMDB controls
are available in Configure. **Reload managed provider** discards unsaved fields
and acknowledges external edits; stale edits otherwise cannot overwrite that
file. Source details are not logged by the wrapper.

## Disable, uninstall and migration

- Back up the existing importer JAR, provider/profile files, `Sage.properties`,
  `Wiz.bin`, and Show-ID map before migrating an established lineup. Never
  switch its provider ID or legacy/v2 ID strategy automatically.
- Disable, Stop, Reset and uninstall restore a registration **only if this
  plugin created it and the administrator has not subsequently changed it**.
  Absent and explicitly empty properties are restored distinctly. Persisted
  ownership survives a server/plugin restart.
- A pre-existing administrator/container-owned XMLTV registration stays intact;
  disabling this wrapper does not disable that legacy importer. If uninstalling
  its only JAR, deliberately restore a legacy JAR or remove that registration
  as part of the administrator's migration before restarting. No guide/lineup,
  favourites, provider files, profiles, caches or ID mappings are deleted.
- Missing profiles/common defaults are seeded without overwriting user files.
  Example configurations stay under `.config/xmltv-examples`, outside automatic
  provider discovery; feeds/credentials are never packaged.
- A future catalog release is a separate approval step. Validation and remaining
  physical-platform limits are in `HANDOFF.md`/`TASKS.md`.

## Supported Unraid container installation

Existing `opensagetv-vibe-container` releases embed the tested JAR, copy it into
`server/JARs`, and safely upserts the plugin property at startup. Do not edit
`Sage.properties` with the obsolete `sed`/`sudo` commands from the historical
project. The legacy CA template's `XMLTV EPG Provider` value remains
`xmltv.XMLTVImportPlugin`, and XML source paths under `/mnt/user` are visible in
the container below `/unraid`.

Compile on the unified Ubuntu 26 build image, then move the finished container
image to a low-CPU Unraid system. Building the plugin inside the running server
container is unsupported.

## Channel logos

Set `sagetv.channel.IconDownload=true` in the selected provider's
`*.xmltv.properties` file to download each channel's `<icon src="...">` URL.
The historical key `xmltv.channel.IconDownload=true` remains supported for
existing configurations. The importer creates `ChannelLogos`, accepts image
URLs without a useful file extension, and converts supported input formats to
PNG. Logos preserve aspect ratio and transparency, are never enlarged, and are
downscaled to a 256x256 bounding box by default. The limits can be changed with:

```properties
sagetv.channel.IconMaxWidth=256
sagetv.channel.IconMaxHeight=256
```

Values are constrained to 16-2048 pixels. Downloads have connection/read
timeouts, an 8 MiB encoded-size limit, and a 16-megapixel decoded-size limit so
a bad or hostile icon cannot consume unbounded server resources. Failures are
logged to `xmltv.log` and do not abort the guide import.

## Generated Show IDs

The default remains fully compatible with existing SageTV databases:

```properties
xmltv.show_id.strategy=legacy
```

For a clean provider/database, the opt-in v2 strategy uses a provider-scoped
SHA-256 identity instead of the historical 32-bit CRC fallback:

```properties
xmltv.show_id.strategy=v2
xmltv.show_id.v2.map_file=xmltv-show-id-v2.properties
```

When `provider.id` is configured it is the provider namespace; otherwise the
provider name is used. The map is written atomically in the SageTV server
directory and keeps both
external Show IDs and SageTV's numeric SeriesInfo IDs stable if collision
resolution is ever required. Back up this file with `Sage.properties` and
`Wiz.bin`. Explicit provider programme IDs such as `tms`/`dd_progid` are never
rewritten by either strategy.

The emitted v2 ID deliberately retains SageTV's numeric series prefix and
four-digit episode token. The full SHA-256 identity remains in the mapping;
putting hexadecimal digest text directly in the external ID would prevent
SageTV Core from linking SeriesInfo and artwork.

Do not enable v2 on an established lineup without planning a migration. The
new fallback IDs are intentionally different, so SageTV can initially treat
existing programmes, favourites, and watched history as different records.

For commissioning or troubleshooting, expose the final ID in the SageTV UI:

```properties
xmltv.show_id.display=description
```

Valid values are `none` (default), `description`, and `bonus`. Disable the
display setting after validation if you do not want IDs shown in guide text.

Audit a feed before changing an established lineup:

```bash
./scripts/show-id-audit.sh --provider-id 999 /path/to/guide.xml > show-id-audit.tsv
```

The audit compares exact legacy IDs with the opt-in v2 IDs and reports
collisions without writing SageTV data. It is read-only by default; add
`--map FILE --write-map` only when intentionally creating a v2 mapping.

## Optional TMDB enrichment

The importer can fill missing programme metadata through the separately
installed `opensagetv-vibe-tmdb` service:

```properties
xmltv.tmdb.enrich=true
xmltv.tmdb.max_lookups_per_import=250
```

This is disabled by default. XMLTV-supplied values always win, and Show IDs
are resolved before enrichment so enabling or disabling TMDB cannot change
favourites, watched history, or programme identity. Lookups are deduplicated
and bounded per import. A missing, stopped, unconfigured, offline, or
rate-limited TMDB plugin never aborts an otherwise successful XMLTV import.
The XMLTV plugin accesses only the public TMDB service facade; it does not read
credentials or the SQLite cache directly.

This product uses the TMDB API but is not endorsed or certified by TMDB.

## Feed acquisition and parsing

`xmltv.files` accepts local paths, `file:` URLs, and HTTP(S) URLs. Remote feeds
have bounded connect/read time, byte, and redirect limits, support gzip and
conditional ETag/Last-Modified requests, and update the local cache atomically.
A failed download preserves the previous cache for diagnosis but fails the
current import instead of silently loading stale guide data.

XML parsing accepts the standard XMLTV `xmltv.dtd` declaration but disables
external DTD loading and external entity expansion. The default validation pass
detects truncated or hostile input before SageTV database calls, and element
text has a configurable memory bound. The relevant defaults are:

```properties
xmltv.download.connect_timeout_ms=10000
xmltv.download.read_timeout_ms=30000
xmltv.download.max_bytes=268435456
xmltv.download.max_redirects=5
xmltv.validate_before_import=true
xmltv.parser.max_element_chars=4194304
xmltv.language.preferred=
run.before.timeout_ms=300000
run.before.fail_on_error=true
run.before.log_command=false
```

`xmltv.language.preferred` selects a matching title, subtitle, and description
when a feed supplies alternatives. `run.before` output is drained into
`xmltv.log`; its command text is redacted unless logging is explicitly enabled.
Timestamps containing 12 or 14 digits but no UTC offset are interpreted in the
SageTV server's configured time zone; timestamps that include an offset retain
their explicit instant.

Legacy station IDs remain unchanged for established lineups. If two different
XMLTV channel IDs nevertheless calculate the same station ID, the first keeps
the legacy value and the later channel receives a deterministic provider-scoped
fallback. The collision and both IDs are logged, and neither channel is dropped.

## Configuration profiles

Profiles remove duplicated feed-format settings while preserving every existing
configuration. A configuration without `xmltv.profile` behaves exactly as it
did previously. Select a root-level profile with:

```properties
provider.name=EPG123
provider.id=777
xmltv.files=/unraid/appdata/xmltvdata/epg123.xmltv
xmltv.profile=EPG123
```

This loads `xmltv_EPG123.profile`. Precedence is:

```text
built-in defaults -> include/common -> selected profile -> .xmltv.properties
```

Consequently any setting in the existing provider file overrides the profile.
Existing `include=` chains continue to work. Shipped profiles are `Generic`,
`EPG123`, `Zap2XML`, `Pluto`, `Threadfin`, `xTeVe`, `IPTV`, `OTA_FTA`, and the
generic `OTA_Local` mapping. Every profile and `common.properties` lives in
the SageTV server root.

To safely convert a working configuration into a provider-named profile:

```properties
xmltv.profile=auto
```

On the next provider reload, the importer atomically creates
`xmltv_<provider.name>.profile`, makes it include `common.properties`, and
changes only the profile selection from `auto` to the generated name. It leaves
all existing settings in the provider file, so they remain authoritative. It
does not overwrite an existing profile.

# Examples `.properties` for channel
![](https://github.com/jzhvymetal/SageTv_XMLTVImportPlugin/blob/main/SAGETV_SERVER_ROOT_Contents/xmltv_src/DOC/PROP_Channel.png)

# Examples `.properties` for show
![](https://github.com/jzhvymetal/SageTv_XMLTVImportPlugin/blob/main/SAGETV_SERVER_ROOT_Contents/xmltv_src/DOC/PROP_Show.png)
