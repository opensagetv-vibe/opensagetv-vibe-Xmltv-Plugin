/* Copyright 2026 OpenSageTV Vibe. Licensed under Apache-2.0. */
package xmltv;

/** Only the public server-property boundary is needed, never a Core patch. */
final class ImporterRegistration {
    interface Properties {
        String get(String key);
        void set(String key, String value);
        void remove(String key);
    }
    static final String IMPORTER = "epg/epg_import_plugin";
    static final String CLASS_NAME = "xmltv.XMLTVImportPlugin";
    static final String PREFIX = "plugins/opensagetv-vibe-xmltv/";
    private final Properties properties;

    ImporterRegistration(Properties properties) { this.properties = properties; }

    synchronized String register() {
        String current = properties.get(IMPORTER);
        if (CLASS_NAME.equals(current)) return owned() ? "XMLTV registered by this plugin" : "Existing XMLTV registration preserved";
        if (current != null && !current.trim().isEmpty()) {
            // Even an unresolvable third-party class is not ours to replace.
            return "Another importer is configured; registration unchanged";
        }
        // Write custody first. A crash before/after the importer write can
        // then recover without guessing which value the administrator owned.
        properties.set(PREFIX + "previous.present", Boolean.toString(current != null));
        properties.set(PREFIX + "previous.value", current == null ? "" : current);
        properties.set(PREFIX + "registration.owned", "true");
        properties.set(IMPORTER, CLASS_NAME);
        if (!CLASS_NAME.equals(properties.get(IMPORTER))) throw new IllegalStateException("Importer registration was not retained");
        return "XMLTV registered; choose a lineup in SageTV setup";
    }

    synchronized String restore() {
        if (!owned()) return "Existing importer left unchanged";
        if (CLASS_NAME.equals(properties.get(IMPORTER))) {
            if (Boolean.parseBoolean(properties.get(PREFIX + "previous.present")))
                properties.set(IMPORTER, value(PREFIX + "previous.value", ""));
            else properties.remove(IMPORTER);
            // Do not forget custody until the public API confirms restoration.
            String expected = Boolean.parseBoolean(properties.get(PREFIX + "previous.present"))
                    ? value(PREFIX + "previous.value", "") : null;
            String actual = properties.get(IMPORTER);
            if (expected == null ? actual != null : !expected.equals(actual))
                throw new IllegalStateException("Importer restoration was not retained");
        }
        // A later administrator change always wins. No global property reset.
        properties.remove(PREFIX + "registration.owned");
        properties.remove(PREFIX + "previous.present");
        properties.remove(PREFIX + "previous.value");
        return "Previous importer restored; feeds and guide data retained";
    }

    boolean owned() { return Boolean.parseBoolean(properties.get(PREFIX + "registration.owned")); }
    String value(String key, String fallback) { String v = properties.get(key); return v == null ? fallback : v; }
}
