package xmltv;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Arrays;
import java.util.Properties;
import sage.SageTV;

/** One-shot integration assertion in disposable stock-server appdata only.
 * No listener, arbitrary expression execution, or production server controls.
 * Loaded through stock load_at_startup_runnable_classes by the test runner.
 */
public final class StockPluginManagerProbe implements Runnable {
    private static final String ID="OpenSageTVVibeXMLTVPlugin";
    private static final String PROPERTY="epg/epg_import_plugin";
    private final Properties result=new Properties();
    private Object api(String name,Object... args) throws Exception { return SageTV.api(name,args); }
    private void check(boolean valid,String name) {
        if(!valid) throw new AssertionError(name);
        result.setProperty(name,"PASS");
    }
    private Object installed() throws Exception {
        for(Object item:(Object[])api("GetInstalledPlugins"))
            if(ID.equalsIgnoreCase(String.valueOf(api("GetPluginIdentifier",item)))) return item;
        return null;
    }
    public void run() {
        try {
            // Only this runner creates this marker, in its own new directory.
            check(new File("XMLTV_STOCK_GATE_OWNED").isFile(),"owned-appdata");
            if(api("GetServerProperty","test/xmltv/baseline.present",null)==null) {
                Object baseline=api("GetServerProperty",PROPERTY,null);
                api("SetServerProperty","test/xmltv/baseline.present",Boolean.toString(baseline!=null));
                api("SetServerProperty","test/xmltv/baseline.value",baseline==null?"":baseline);
            }
            String baseline="true".equals(api("GetServerProperty","test/xmltv/baseline.present",null))
                    ? (String)api("GetServerProperty","test/xmltv/baseline.value",null) : null;
            Object plugin=installed();
            if(plugin==null) {
                api("RefreshAvailablePlugins");
                Object available=api("GetAvailablePluginForID",ID);
                check(available!=null,"catalog-discovery");
                check(Boolean.TRUE.equals(api("IsPluginCompatible",available)),"stock-compatible-metadata");
                String status=String.valueOf(api("InstallPlugin",available));
                check("OK".equals(status)||"RESTART".equals(status),"public-install");
                if("RESTART".equals(status)) { result.setProperty("status","RESTART_REQUIRED"); return; }
                plugin=installed();
            }
            check(plugin!=null,"installed-identity");
            if(!Boolean.TRUE.equals(api("IsPluginEnabled",plugin))) api("EnablePlugin",plugin);
            check(Boolean.TRUE.equals(api("IsPluginEnabled",plugin)),"enabled");
            check("xmltv.XMLTVImportPlugin".equals(api("GetServerProperty",PROPERTY,null)),"registered-with-stock-api");
            check(((String[])api("GetPluginConfigSettings",plugin)).length==10,"stv-settings");
            check("Register XMLTV when importer is empty".equals(api("GetPluginConfigLabel",plugin,"registration.enabled")),"stv-label");
            Object markets=api("GetLocalMarketsFromEPGServer");
            boolean afterRestart="true".equals(api("GetServerProperty","test/xmltv/restart.checked",null));
            check(markets instanceof String[] && Arrays.asList((String[])markets).contains(afterRestart ? "Vibe Synthetic Gate" : "XMLTV Lineup"),"stock-epg-discovery");

            api("SetPluginConfigValue",plugin,"provider.name","Vibe Synthetic Gate");
            api("SetPluginConfigValue",plugin,"provider.id","777");
            api("SetPluginConfigValue",plugin,"xmltv.files",new File("synthetic.xml").getAbsolutePath());
            api("SetPluginConfigValue",plugin,"provider.apply","save");
            check(new File("vibe-plugin.xmltv.properties").isFile(),"explicit-provider-save");
            markets=api("GetLocalMarketsFromEPGServer");
            check(markets instanceof String[] && Arrays.asList((String[])markets).contains("Vibe Synthetic Gate"),"configured-provider-discovery");
            if(!afterRestart) {
                api("SetServerProperty","test/xmltv/restart.checked","true");
                result.setProperty("status","RESTART_REQUIRED"); return;
            }
            check(true,"real-server-restart-registration-and-settings");
            check(Boolean.TRUE.equals(api("DisablePlugin",plugin)),"standard-disable");
            Object restored=api("GetServerProperty",PROPERTY,null);
            check(baseline==null ? restored==null : baseline.equals(restored),"disable-restores-original");
            api("SetServerProperty",PROPERTY,"third.party.AdministratorImporter");
            api("EnablePlugin",plugin);
            check("third.party.AdministratorImporter".equals(api("GetServerProperty",PROPERTY,null)),"third-party-not-replaced");
            api("DisablePlugin",plugin);
            check("third.party.AdministratorImporter".equals(api("GetServerProperty",PROPERTY,null)),"third-party-not-removed");
            api("RemoveServerProperty",PROPERTY);
            api("EnablePlugin",plugin);
            check("xmltv.XMLTVImportPlugin".equals(api("GetServerProperty",PROPERTY,null)),"re-enable-registration");
            api("DisablePlugin",plugin);
            String uninstall=String.valueOf(api("UninstallPlugin",plugin));
            check("OK".equals(uninstall)||"RESTART".equals(uninstall),"public-uninstall");
            restored=api("GetServerProperty",PROPERTY,null);
            // Stock EPG discovery may materialize its empty default after a
            // removal. Neither absent nor empty selects any importer.
            check(restored==null || "".equals(restored),"uninstall-restoration");
            check(new File("vibe-plugin.xmltv.properties").isFile(),"uninstall-keeps-user-feed");
            check(new File("common.properties").isFile(),"uninstall-keeps-user-profiles");
            result.setProperty("status","PASS");
        } catch(Throwable error) {
            error.printStackTrace(); result.setProperty("status","FAIL");
            result.setProperty("error",error.getClass().getName()+": "+error.getMessage());
        } finally {
            try(FileOutputStream output=new FileOutputStream("stock-plugin-result.properties")) {
                result.store(output,"Isolated stock SageTV plugin-manager gate");
            } catch(Exception error) { error.printStackTrace(); }
        }
    }
}
