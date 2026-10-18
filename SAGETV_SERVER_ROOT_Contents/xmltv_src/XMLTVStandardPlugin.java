/* Copyright 2026 OpenSageTV Vibe. Licensed under Apache-2.0. */
package xmltv;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Properties;
import sage.SageTV;
import sage.SageTVPlugin;
import sage.SageTVPluginRegistry;

/** Standard-plugin installation/settings around the unchanged EPG importer.
 * Never starts guide acquisition or changes a lineup merely by being enabled.
 */
public final class XMLTVStandardPlugin implements SageTVPlugin {
    static final String PREFIX = ImporterRegistration.PREFIX;
    static final String FILE = "vibe-plugin.xmltv.properties";
    static final String[] FIELDS = {"provider.name", "provider.id", "xmltv.files",
        "xmltv.profile", "sagetv.channel.IconDownload", "xmltv.tmdb.enrich"};
    private final ImporterRegistration.Properties properties;
    private final ImporterRegistration registration;
    private final Path directory;
    private boolean running;
    private String status = "Stopped";

    public XMLTVStandardPlugin(SageTVPluginRegistry registry) { this(registry, false); }
    public XMLTVStandardPlugin(SageTVPluginRegistry registry, boolean reset) {
        this(new ImporterRegistration.Properties() {
            public String get(String key) { return (String) api("GetServerProperty", key, null); }
            public void set(String key, String value) { api("SetServerProperty", key, value); }
            public void remove(String key) { api("RemoveServerProperty", key); }
        }, Paths.get(System.getProperty("user.dir")));
        if (reset) resetConfig();
    }
    XMLTVStandardPlugin(ImporterRegistration.Properties properties, Path directory) {
        this.properties = properties;
        this.registration = new ImporterRegistration(properties);
        this.directory = directory.toAbsolutePath().normalize();
    }
    private static Object api(String name, Object... values) {
        try { return SageTV.api(name, values); }
        catch (Exception e) { throw new IllegalStateException("SageTV property API unavailable", e); }
    }

    @Override public synchronized void start() {
        running = true;
        try {
            seedResources();
            status = enabled() ? registration.register() : "Registration disabled; existing importer unchanged";
        } catch (Exception e) { status = "ERROR: setup unavailable; inspect permissions/configuration"; }
    }
    @Override public synchronized void stop() {
        running = false;
        try { status = registration.restore(); }
        catch (Exception e) { status = "ERROR: importer restoration pending; settings retained"; }
    }
    @Override public void destroy() { stop(); }
    private boolean enabled() { return Boolean.parseBoolean(registration.value(PREFIX + "registration.enabled", "true")); }

    @Override public String[] getConfigSettings() {
        return new String[]{"registration.enabled", "status", "provider.name", "provider.id",
            "xmltv.files", "xmltv.profile", "sagetv.channel.IconDownload", "xmltv.tmdb.enrich",
            "provider.apply", "provider.reload"};
    }
    @Override public synchronized String getConfigValue(String key) {
        if ("registration.enabled".equals(key)) return Boolean.toString(enabled());
        if ("status".equals(key)) return status;
        if ("provider.apply".equals(key)) return "Save provider settings";
        if ("provider.reload".equals(key)) return "Reload managed provider";
        if (!isField(key)) return "";
        String staged = properties.get(PREFIX + "edit/" + key);
        if (staged != null) return staged;
        try { return loadProvider().getProperty(key, defaultValue(key)); }
        catch (IOException e) { return defaultValue(key); }
    }
    @Override public String[] getConfigValues(String key) { return new String[0]; }
    @Override public int getConfigType(String key) {
        if ("registration.enabled".equals(key) || "sagetv.channel.IconDownload".equals(key) || "xmltv.tmdb.enrich".equals(key)) return CONFIG_BOOL;
        if ("status".equals(key) || key.startsWith("provider.") && (key.endsWith("apply") || key.endsWith("reload"))) return CONFIG_BUTTON;
        if ("xmltv.profile".equals(key)) return CONFIG_CHOICE;
        return CONFIG_TEXT;
    }
    @Override public synchronized void setConfigValue(String key, String value) {
        try {
            if ("registration.enabled".equals(key)) {
                requireBoolean(value);
                properties.set(PREFIX + key, value);
                if (running) status = enabled() ? registration.register() : registration.restore();
            } else if ("provider.apply".equals(key)) {
                saveProvider();
                status = "Provider saved; select its lineup in SageTV setup";
            } else if ("provider.reload".equals(key)) {
                // Explicitly acknowledge edits made by an administrator; no write.
                Properties current = loadProvider();
                for (String field : FIELDS) properties.remove(PREFIX + "edit/" + field);
                if (Files.exists(directory.resolve(FILE)))
                    properties.set(PREFIX + "provider.digest", digest(directory.resolve(FILE)));
                status = "Managed provider reloaded; guide data unchanged";
            } else if (isField(key)) {
                if (value == null || value.length() > 8192 || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0)
                    throw new IllegalArgumentException("Invalid field length/line break");
                if ("sagetv.channel.IconDownload".equals(key) || "xmltv.tmdb.enrich".equals(key)) requireBoolean(value);
                if ("xmltv.profile".equals(key) && !validProfile(value)) throw new IllegalArgumentException("Choose a shipped profile");
                properties.set(PREFIX + "edit/" + key, value.trim());
            }
            // Status is read-only, and clicking it never opens a text editor.
        } catch (Exception e) {
            // Do not expose URL tokens, feed paths, commands or exception text.
            status = "ERROR: no provider saved; check required fields, ownership and file permissions";
        }
    }
    @Override public void setConfigValues(String key, String[] value) { }
    @Override public String[] getConfigOptions(String key) {
        return "xmltv.profile".equals(key) ? new String[]{"Generic", "EPG123", "Zap2XML", "Pluto", "Threadfin", "xTeVe", "IPTV", "OTA_FTA", "OTA_Local"} : new String[0];
    }
    @Override public String getConfigLabel(String key) {
        if ("registration.enabled".equals(key)) return "Register XMLTV when importer is empty";
        if ("status".equals(key)) return "Status (Read-only)";
        if ("provider.name".equals(key)) return "Provider name";
        if ("provider.id".equals(key)) return "Provider ID (1-998, unique)";
        if ("xmltv.files".equals(key)) return "XMLTV source (file or HTTP/HTTPS URL)";
        if ("xmltv.profile".equals(key)) return "Feed profile";
        if ("sagetv.channel.IconDownload".equals(key)) return "Download channel logos";
        if ("xmltv.tmdb.enrich".equals(key)) return "Optional TMDB enrichment";
        if ("provider.apply".equals(key)) return "Save provider settings";
        if ("provider.reload".equals(key)) return "Reload managed provider";
        return key;
    }
    @Override public String getConfigHelpText(String key) {
        if ("registration.enabled".equals(key)) return "Uses stock server APIs only. Never replaces another importer. Disable restores only a registration this plugin owns; existing XMLTV/guide data stay.";
        if ("provider.apply".equals(key)) return "Explicitly saves only " + FILE + "; existing provider files and Show-ID mappings are not rewritten. Does not start an import or select a lineup.";
        if ("provider.reload".equals(key)) return "Discard unsaved fields and acknowledge external edits to this plugin's provider file.";
        if ("provider.id".equals(key)) return "Keep established IDs unchanged. A new managed provider must not reuse another provider's ID.";
        if ("xmltv.tmdb.enrich".equals(key)) return "Off by default; optional separately installed TMDB service. XMLTV values and Show IDs remain authoritative.";
        return "Existing legacy xmltv.properties and *.xmltv.properties remain supported. Feed credentials stay private on this server.";
    }
    @Override public synchronized void resetConfig() {
        try {
            registration.restore();
            properties.set(PREFIX + "registration.enabled", "false");
            for (String field : FIELDS) properties.remove(PREFIX + "edit/" + field);
            status = "Registration disabled; provider files and guide data retained";
        } catch (Exception e) { status = "ERROR: reset incomplete; existing settings retained"; }
    }
    @Override public void sageEvent(String name, Map vars) { }

    private static boolean isField(String key) { for (String field : FIELDS) if (field.equals(key)) return true; return false; }
    private static void requireBoolean(String value) {
        if (!"true".equals(value) && !"false".equals(value)) throw new IllegalArgumentException("Boolean required");
    }
    private boolean validProfile(String value) { for (String p : getConfigOptions("xmltv.profile")) if (p.equals(value)) return true; return false; }
    private static String defaultValue(String key) {
        if ("xmltv.profile".equals(key)) return "Generic";
        if ("sagetv.channel.IconDownload".equals(key) || "xmltv.tmdb.enrich".equals(key)) return "false";
        return "";
    }
    private Properties loadProvider() throws IOException {
        Path file = directory.resolve(FILE);
        Properties result = new Properties();
        if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) > 1048576)
                throw new IOException("Unsafe managed configuration");
            try (InputStream input = Files.newInputStream(file)) { result.load(input); }
        }
        return result;
    }
    private void saveProvider() throws Exception {
        Path file = directory.resolve(FILE);
        boolean exists = Files.exists(file, LinkOption.NOFOLLOW_LINKS);
        if (exists && (!Boolean.parseBoolean(properties.get(PREFIX + "provider.owned")) ||
                !digest(file).equals(properties.get(PREFIX + "provider.digest"))))
            throw new IOException("Managed file custody changed");
        Properties result = loadProvider();
        String oldId = result.getProperty("provider.id", "");
        for (String field : FIELDS) result.setProperty(field, getConfigValue(field));
        int id = Integer.parseInt(result.getProperty("provider.id"));
        if (id < 1 || id > 998 || !Integer.toString(id).equals(result.getProperty("provider.id")) || result.getProperty("provider.name").trim().isEmpty() || result.getProperty("xmltv.files").trim().isEmpty())
            throw new IllegalArgumentException("Provider name, ID and source required");
        if (!exists || !oldId.equals(Integer.toString(id))) {
            for (String[] provider : new XMLTVImportPlugin().getLocalMarkets())
                if (Integer.toString(id).equals(provider[0])) throw new IllegalArgumentException("Provider ID already exists");
        }
        Path temporary = Files.createTempFile(directory, ".vibe-xmltv-", ".tmp");
        try {
            try (OutputStream output = Files.newOutputStream(temporary)) { result.store(output, "Managed by OpenSageTV Vibe XMLTV Plugin"); }
            if (!exists) {
                // Publish the fully written file without ever exposing partial
                // provider text or replacing a concurrently created user file.
                try { Files.createLink(file, temporary); }
                catch (UnsupportedOperationException e) { Files.move(temporary, file); }
            } else {
                // Recheck immediately before committing an explicitly requested edit.
                if (!digest(file).equals(properties.get(PREFIX + "provider.digest"))) throw new IOException("Configuration changed during save");
                try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
                catch (AtomicMoveNotSupportedException e) { Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING); }
            }
            properties.set(PREFIX + "provider.owned", "true");
            properties.set(PREFIX + "provider.digest", digest(file));
        } finally { Files.deleteIfExists(temporary); }
    }
    private static String digest(Path file) throws Exception {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file) > 1048576) throw new IOException("Unsafe configuration");
        byte[] value = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file));
        StringBuilder result = new StringBuilder();
        for (byte b : value) result.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
        return result.toString();
    }
    private void seedResources() throws IOException {
        try (InputStream index = XMLTVStandardPlugin.class.getResourceAsStream("/xmltv/resources/index.txt")) {
            if (index == null) throw new IOException("Missing packaged profile index");
            java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(index, "UTF-8"));
            String name;
            while ((name = reader.readLine()) != null) {
                if (!name.matches("(?:common\\.properties|xmltv_[A-Za-z0-9_]+\\.(?:profile|properties)|xmltv_[A-Za-z0-9_]+\\.xmltv\\.properties)"))
                    throw new IOException("Invalid packaged profile name");
                boolean profile = name.endsWith(".profile") || name.equals("common.properties");
                Path parent = profile ? directory : directory.resolve(".config/xmltv-examples");
                // Reject redirected parents; resources must stay under the server.
                Path walk = parent;
                while (walk != null && walk.startsWith(directory)) {
                    if (Files.isSymbolicLink(walk)) throw new IOException("Redirected profile directory");
                    if (walk.equals(directory)) break;
                    walk = walk.getParent();
                }
                Files.createDirectories(parent);
                try (InputStream data = XMLTVStandardPlugin.class.getResourceAsStream("/xmltv/resources/" + name)) {
                    if (data == null) throw new IOException("Missing packaged profile");
                    try { Files.copy(data, parent.resolve(name)); }
                    catch (FileAlreadyExistsException expected) { /* User file always wins. */ }
                }
            }
        }
    }
}
