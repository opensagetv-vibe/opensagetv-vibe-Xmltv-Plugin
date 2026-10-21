"""Source/packaging gates without shipping Sage.jar or private server data."""
import io
from pathlib import Path
import struct
import sys
import tempfile
import unittest
import zipfile

ROOT=Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'scripts'))
import package

class PluginPackageTests(unittest.TestCase):
    def payload(self, extra=None, major=52):
        entries={name:b'\xca\xfe\xba\xbe'+struct.pack('>HH',0,major) for name in
            ['xmltv/XMLTVImportPlugin.class','xmltv/XMLTVStandardPlugin.class','xmltv/ImporterRegistration.class']}
        entries['xmltv/resources/index.txt']=b'common.properties\n'
        entries['xmltv/resources/common.properties']=b'# safe example\n'
        entries.update(extra or {})
        return package.archive(entries,'2026.10.18')

    def test_metadata_and_rename(self):
        values=package.metadata()
        self.assertEqual(values['PACKAGE_ID'],'opensagetv-vibe-Xmltv-Plugin')
        self.assertIn(values['PUBLISH_APPROVED'],['false','true'])
        self.assertIn('xmltv.XMLTVStandardPlugin',(ROOT/'manifests/OpenSageTVVibeXMLTVPlugin.xml.template').read_text())

    def test_reproducibility_and_stable_jar(self):
        a=self.payload(); b=self.payload()
        self.assertEqual(a,b)
        package.inspect_jar(a)
        with zipfile.ZipFile(io.BytesIO(a)) as z:
            self.assertEqual(z.getinfo('xmltv/XMLTVImportPlugin.class').date_time,(2026,10,18,0,0,0))
        source=(ROOT/'scripts/package.py').read_text()
        self.assertIn("'XMLTVImportPlugin.jar'",source)

    def test_foreign_classes_credentials_and_paths_rejected(self):
        for name in ['sage/Sage.class','xmltv/TestHelper.class/private','config/private.properties',
                     'xmltv/StandardPluginTest.class','xmltv/StockPluginManagerProbe.class',
                     'xmltv/resources/credentials.properties','xmltv/resources/xmltv_private.xmltv.properties','xmltv/../escape.class']:
            with self.subTest(name=name), self.assertRaises(ValueError):
                package.inspect_jar(self.payload({name:b'secret'}))
        with self.assertRaisesRegex(ValueError,'Java8'):
            package.inspect_jar(self.payload(major=61))

    def test_standard_wrapper_public_api_only(self):
        source=(ROOT/'SAGETV_SERVER_ROOT_Contents/xmltv_src/XMLTVStandardPlugin.java').read_text()
        for api in ['GetServerProperty','SetServerProperty','RemoveServerProperty']:
            self.assertIn(api,source)
        for forbidden in ['Sage.put(', 'Sage.get(', 'getDeclaredField(', 'setAccessible(', 'Class.forName(']:
            self.assertNotIn(forbidden,source)
        self.assertIn('registration.restore()',source)
        self.assertIn('FileAlreadyExistsException',source)

    def test_examples_not_active_and_profile_limits(self):
        source=(ROOT/'SAGETV_SERVER_ROOT_Contents/xmltv_src/XMLTVStandardPlugin.java').read_text()
        self.assertIn('.config/xmltv-examples',source)
        self.assertIn('provider.id',source)
        self.assertIn('provider.digest',source)
        self.assertIsNone(package.ASSET_NAME.fullmatch('../xmltv_private.properties'))
        self.assertIsNone(package.ASSET_NAME.fullmatch('private.properties'))

    def test_build_requires_stock_not_modified_core(self):
        source=(ROOT/'scripts/build.sh').read_text()
        self.assertIn('/workspace/ffmpeg-plugin/.deps/stock/Sage.jar',source)
        self.assertNotIn('/work/sagetv/output/server/Sage.jar',source)
        self.assertIn('--release 8',source)
        self.assertIn('java-11-openjdk',source)

    def test_untracked_provider_cannot_enter_resources(self):
        with tempfile.TemporaryDirectory(prefix='xmltv-resource-test-') as temporary:
            root=Path(temporary); source=root/'SAGETV_SERVER_ROOT_Contents'; source.mkdir()
            for name in package.RESOURCE_NAMES: (source/name).write_text('# canonical example\n')
            private='xmltv_private.xmltv.properties'
            (source/private).write_text('synthetic-secret=NOT_A_REAL_CREDENTIAL\n')
            package.resources(root)
            output=root/'output/classes/xmltv/resources'
            self.assertFalse((output/private).exists())
            self.assertNotIn(private,(output/'index.txt').read_text())

if __name__=='__main__': unittest.main()
