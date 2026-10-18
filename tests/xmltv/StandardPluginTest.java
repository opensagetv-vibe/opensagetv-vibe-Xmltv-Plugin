package xmltv;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/** Stock-JAR lifecycle boundary tests; no live SageTV server/guide mutation. */
public final class StandardPluginTest {
    private static int checks;
    static class Memory implements ImporterRegistration.Properties {
        final Map<String,String> values = new HashMap<String,String>();
        public String get(String key) { return values.get(key); }
        public void set(String key, String value) { values.put(key,value); }
        public void remove(String key) { values.remove(key); }
    }
    private static void check(boolean value, String message) {
        checks++; if (!value) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        final String key = ImporterRegistration.IMPORTER, name = ImporterRegistration.CLASS_NAME;
        Memory m = new Memory();
        ImporterRegistration r = new ImporterRegistration(m);
        r.register(); check(name.equals(m.get(key)) && r.owned(), "empty importer registers");
        r = new ImporterRegistration(m); // new process/lifetime, same persisted journal
        r.register(); r.restore(); check(m.get(key)==null && !r.owned(), "restart restores absent property");
        m.set(key, ""); r.register(); r.restore(); check("".equals(m.get(key)), "explicit empty is not absent");
        m.set(key, name); r.register(); r.restore(); check(name.equals(m.get(key)) && !r.owned(), "preexisting XMLTV remains owned by admin");
        m.set(key, "third.party.Epg"); r.register(); r.restore(); check("third.party.Epg".equals(m.get(key)), "third-party importer preserved");
        m.remove(key); r.register(); m.set(key,"admin.later.Epg"); r.restore();
        check("admin.later.Epg".equals(m.get(key)) && !r.owned(), "later administrator always wins");
        m.remove(key); r.register(); r.restore(); r.restore(); check(m.get(key)==null,"stop/destroy idempotent");

        Path root = Paths.get(".").toAbsolutePath().normalize();
        m = new Memory();
        Files.write(root.resolve("common.properties"), "# USER PROFILE\ncustom=true\n".getBytes(StandardCharsets.UTF_8));
        XMLTVStandardPlugin plugin = new XMLTVStandardPlugin(m,root);
        plugin.start();
        check(name.equals(m.get(key)), "Standard start registers through injected stock-property boundary");
        check(new String(Files.readAllBytes(root.resolve("common.properties")), StandardCharsets.UTF_8).contains("USER PROFILE"), "never overwrite profile");
        check(Files.isRegularFile(root.resolve("xmltv_Generic.profile")), "missing packaged profile seeded");
        check(Files.isRegularFile(root.resolve(".config/xmltv-examples/xmltv_OTA_Local.xmltv.properties")), "examples stay outside provider discovery");
        check(!Files.exists(root.resolve("xmltv_OTA_Local.xmltv.properties")), "example not installed as active provider");
        check(!Files.exists(root.resolve(XMLTVStandardPlugin.FILE)), "start never creates active feed");
        plugin.setConfigValue("status","anything"); check(plugin.getConfigType("status")==8,"read-only status has no keyboard");
        plugin.setConfigValue("registration.enabled","false"); check(m.get(key)==null,"feature disable restores owned registration");
        plugin.setConfigValue("registration.enabled","true"); check(name.equals(m.get(key)),"feature re-enable registers");
        plugin.stop(); check(m.get(key)==null,"Standard Disable restores");
        plugin.setConfigValue("registration.enabled","true"); check(m.get(key)==null,"editing stopped plugin does not activate it");
        plugin.start(); check(name.equals(m.get(key)),"start after stopped edit");

        plugin.setConfigValue("provider.apply", "save");
        check(plugin.getConfigValue("status").startsWith("ERROR:") && !Files.exists(root.resolve(XMLTVStandardPlugin.FILE)),"incomplete config fails without writes");
        plugin.setConfigValue("provider.name","Synthetic plugin provider");
        plugin.setConfigValue("provider.id","777");
        plugin.setConfigValue("xmltv.files","https://example.invalid/guide.xml?token=PRIVATE_TEST_TOKEN");
        plugin.setConfigValue("xmltv.profile","Generic");
        plugin.setConfigValue("provider.apply","save");
        Path file = root.resolve(XMLTVStandardPlugin.FILE);
        check(Files.isRegularFile(file) && !plugin.getConfigValue("status").startsWith("ERROR"),"explicit complete save creates managed config");
        check(!plugin.getConfigValue("status").contains("PRIVATE_TEST_TOKEN"),"status never leaks feed token");
        Properties p = new Properties();
        try (java.io.InputStream stream=Files.newInputStream(file)) { p.load(stream); }
        check("777".equals(p.getProperty("provider.id")) && "Generic".equals(p.getProperty("xmltv.profile")),"provider fields persist");
        byte[] original=Files.readAllBytes(file);
        plugin.setConfigValue("provider.id","0"); plugin.setConfigValue("provider.apply","save");
        check(java.util.Arrays.equals(original,Files.readAllBytes(file)),"invalid ID does not overwrite");
        plugin.setConfigValue("provider.id","777");
        Files.write(file,"\nexternal.user=value\n".getBytes(StandardCharsets.UTF_8),java.nio.file.StandardOpenOption.APPEND);
        byte[] external=Files.readAllBytes(file);
        plugin.setConfigValue("provider.apply","save"); check(java.util.Arrays.equals(external,Files.readAllBytes(file)),"external edit blocks stale save");
        plugin.setConfigValue("provider.reload","reload");
        plugin.setConfigValue("provider.name","Changed label"); plugin.setConfigValue("provider.apply","save");
        p.clear(); try (java.io.InputStream stream=Files.newInputStream(file)) { p.load(stream); }
        check("value".equals(p.getProperty("external.user")) && "777".equals(p.getProperty("provider.id")),"reload preserves unknown keys/ID");
        plugin.resetConfig(); plugin.destroy(); check(Files.isRegularFile(file) && m.get(key)==null,"reset/uninstall never delete provider/guide data");

        m = new Memory(); m.set(key,"third.party.Epg");
        plugin = new XMLTVStandardPlugin(m,root); plugin.start(); plugin.stop();
        check("third.party.Epg".equals(m.get(key)),"actual wrapper respects conflict");
        m = new Memory(); m.set(key,name);
        plugin = new XMLTVStandardPlugin(m,root); plugin.start(); plugin.stop();
        check(name.equals(m.get(key)),"legacy registration stays after stop");
        plugin.setConfigValue("provider.id","777"); plugin.setConfigValue("provider.name","Do not overwrite"); plugin.setConfigValue("provider.apply","save");
        check(plugin.getConfigValue("status").startsWith("ERROR:"),"never adopt preexisting unowned managed file");
        System.out.println("[PASS] Standard plugin stock-API lifecycle/settings/rollback/profile/provider custody: "+checks+" checks");
    }
}
