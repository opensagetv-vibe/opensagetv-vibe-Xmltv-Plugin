#!/usr/bin/env python3
"""Deterministic SageTV JAR/plugin package; never installs or publishes."""
import argparse
import datetime
import hashlib
import io
import json
from pathlib import Path
import re
import stat
import struct
import zipfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ASSET_NAME = re.compile(r'(?:common\.properties|xmltv_[A-Za-z0-9_]+\.(?:profile|properties)|xmltv_[A-Za-z0-9_]+\.xmltv\.properties)\Z')
RESOURCE_NAMES = frozenset({
    'common.properties','xmltv_EPG123.profile','xmltv_epg123.xmltv.properties',
    'xmltv_Generic.profile','xmltv_IPTV.profile','xmltv_IPTV_Example.xmltv.properties',
    'xmltv_MAIN_FULL.properties','xmltv_OTA_FTA.profile','xmltv_OTA_Local.profile',
    'xmltv_OTA_Local.xmltv.properties','xmltv_Pluto.profile','xmltv_Pluto.xmltv.properties',
    'xmltv_Threadfin.profile','xmltv_xTeVe.profile','xmltv_Zap2XML.profile'})

def metadata(root=ROOT, require_approved=False):
    result = dict(line.split('=', 1) for line in (root/'release.properties').read_text().splitlines() if '=' in line)
    if not re.fullmatch(r'\d+\.\d+\.\d+', result['VERSION']) or (root/'VERSION').read_text().strip() != result['VERSION']:
        raise ValueError('VERSION mismatch')
    datetime.datetime.strptime(result['RELEASE_DATE'], '%Y.%m.%d')
    if result['PACKAGE_ID'] != 'opensagetv-vibe-xmltv-Plugin': raise ValueError('Wrong project identity')
    if require_approved and result.get('PUBLISH_APPROVED') != 'true': raise ValueError('Explicit publication approval required')
    return result

def resources(root=ROOT):
    source = root/'SAGETV_SERVER_ROOT_Contents'
    output = root/'output/classes/xmltv/resources'
    # Do not glob arbitrary .properties files: a developer may have a private,
    # untracked feed configuration beside the historical example sources.
    names = sorted(RESOURCE_NAMES)
    if 'common.properties' not in names or 'xmltv_Generic.profile' not in names: raise ValueError('Missing profiles')
    output.mkdir(parents=True, exist_ok=True)
    for name in names:
        path = source/name
        if path.is_symlink() or not path.is_file(): raise ValueError('Unsafe resource')
        # Canonical credential-free checked-in examples only, never appdata.
        (output/name).write_bytes(path.read_bytes().replace(b'\r\n', b'\n'))
    (output/'index.txt').write_text('\n'.join(names)+'\n', encoding='utf-8', newline='\n')

def archive(entries, date):
    result = io.BytesIO()
    stamp = datetime.datetime.strptime(date, '%Y.%m.%d')
    with zipfile.ZipFile(result, 'w') as z:
        for name, data in sorted(entries.items()):
            info = zipfile.ZipInfo(name, (stamp.year, stamp.month, stamp.day, 0, 0, 0))
            info.create_system = 3
            info.external_attr = (stat.S_IFREG | 0o644) << 16
            info.compress_type = zipfile.ZIP_DEFLATED
            z.writestr(info, data)
    return result.getvalue()

def inspect_jar(data):
    with zipfile.ZipFile(io.BytesIO(data)) as jar:
        names = jar.namelist()
        if len(names) != len(set(names)) or not {'xmltv/XMLTVImportPlugin.class','xmltv/XMLTVStandardPlugin.class','xmltv/ImporterRegistration.class','xmltv/resources/index.txt'}.issubset(names):
            raise ValueError('Incomplete/duplicate importer payload')
        for name in names:
            if name == 'META-INF/MANIFEST.MF': continue
            if '..' in name.split('/') or '\\' in name or name.startswith('/'):
                raise ValueError('Unsafe entry')
            if name.startswith('xmltv/') and name.endswith('.class') and '/' not in name[len('xmltv/'):]:
                if re.search(r'(?:Test|Harness|Probe)(?:\$.*)?\.class$',name): raise ValueError('Test class in runtime payload')
                value = jar.read(name)
                if value[:4] != b'\xca\xfe\xba\xbe' or struct.unpack('>H',value[6:8])[0] != 52: raise ValueError('Java8 required')
            elif name.startswith('xmltv/resources/') and (name == 'xmltv/resources/index.txt' or name[len('xmltv/resources/'):] in RESOURCE_NAMES):
                continue
            else: raise ValueError('Foreign Core, private configuration or test payload')
        indexed=jar.read('xmltv/resources/index.txt').decode('utf-8').splitlines()
        actual={name[len('xmltv/resources/'):] for name in names if name.startswith('xmltv/resources/') and name!='xmltv/resources/index.txt'}
        if len(indexed)!=len(set(indexed)) or set(indexed)!=actual or not actual.issubset(RESOURCE_NAMES):
            raise ValueError('Resource index mismatch/private resource')

def package(root=ROOT, require_approved=False):
    values = metadata(root, require_approved)
    classes = root/'output/classes'
    entries = {'META-INF/MANIFEST.MF':b'Manifest-Version: 1.0\r\n\r\n'}
    for path in sorted((classes/'xmltv').rglob('*')):
        if path.is_symlink(): raise ValueError('Symlink in build')
        if path.is_file(): entries[path.relative_to(classes).as_posix()] = path.read_bytes()
    jar = archive(entries, values['RELEASE_DATE'])
    inspect_jar(jar)
    output = root/'output/packages'; output.mkdir(parents=True,exist_ok=True)
    # Keep the established JAR filename: two copies of xmltv.* must never be
    # installed under different names during migration from manual/container use.
    (output/'XMLTVImportPlugin.jar').write_bytes(jar)
    filename = 'OpenSageTVVibeXMLTVPlugin-jar-'+values['VERSION']+'.zip'
    payload = archive({'XMLTVImportPlugin.jar':jar},values['RELEASE_DATE'])
    (output/filename).write_bytes(payload)
    descriptor = (root/'manifests/OpenSageTVVibeXMLTVPlugin.xml.template').read_text().replace('@VERSION@',values['VERSION']).replace('@DATE@',values['RELEASE_DATE']).replace('@MD5@',hashlib.md5(payload).hexdigest())
    tree = ET.fromstring(descriptor)
    if tree.findtext('ImplementationClass') != 'xmltv.XMLTVStandardPlugin': raise ValueError('Wrong implementation')
    (output/'OpenSageTVVibeXMLTVPlugin.xml').write_text(descriptor,encoding='utf-8',newline='\n')
    dev = descriptor.replace(tree.findtext('Package/Location'),'file:/opt/sagetv/server/SageTVPluginsDev.d/'+filename)
    (output/'OpenSageTVVibeXMLTVPlugin-dev.xml').write_text(dev,encoding='utf-8',newline='\n')
    assets = [filename,'XMLTVImportPlugin.jar','OpenSageTVVibeXMLTVPlugin.xml']
    report = {'version':values['VERSION'],'project':values['PACKAGE_ID'],'publishApproved':values.get('PUBLISH_APPROVED')=='true',
        'jarSha256':hashlib.sha256(jar).hexdigest(),
        'assets':{name:hashlib.sha256((output/name).read_bytes()).hexdigest() for name in assets}}
    (output/'package-report.json').write_text(json.dumps(report,indent=2)+'\n',encoding='utf-8')
    assets.append('package-report.json')
    (output/'SHA256SUMS').write_text(''.join(hashlib.sha256((output/name).read_bytes()).hexdigest()+'  '+name+'\n' for name in sorted(assets)),encoding='utf-8')
    return report

if __name__ == '__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action',choices=['resources','package','validate'])
    parser.add_argument('--require-approved',action='store_true')
    args=parser.parse_args()
    if args.action=='resources': resources()
    elif args.action=='package': print(json.dumps(package(require_approved=args.require_approved),indent=2))
    else: metadata(require_approved=args.require_approved); print('PASS: XMLTV Standard-plugin metadata')
