#!/usr/bin/env python3
"""One disposable stock-JAR server process inside the existing dev container.

Uses its normal public plugin API and a fixed startup assertion, not private
Core fields, production servers, or an external automation endpoint. No new
build/container/image. No configured tuners, recordings, lineups or credentials.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import time
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]
def properties(path):
    return dict(line.split('=',1) for line in path.read_text().splitlines() if line and not line.startswith('#') and '=' in line)

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--stock-jar',type=Path,default=Path('/workspace/ffmpeg-plugin/.deps/stock/Sage.jar'))
    parser.add_argument('--native-runtime',type=Path,default=Path('/work/sagetv/output/server'))
    args=parser.parse_args()
    stock=args.stock_jar.resolve(strict=True)
    checksum=hashlib.sha256(stock.read_bytes()).hexdigest()
    if checksum!='d76ded981b9bc51e25b9cec821b6abeb771b46c2996dc45e453349b5e703fcb0':
        raise RuntimeError('This gate requires the commissioned, unmodified stock JAR')
    runtime=args.native_runtime.resolve(strict=True)
    stage=Path(tempfile.mkdtemp(prefix='vibe-xmltv-stock-'))
    output=ROOT/'artifacts/active/XMLTV-PLUGIN-003'
    output.mkdir(parents=True,exist_ok=True)
    report={'task':'XMLTV-PLUGIN-003','stockCoreSha256Before':checksum,'status':'STARTING',
        'appdata':str(stage),'nativeRuntime':str(runtime),
        'scope':'Isolated stock Java server; existing Linux JNI runtime; no production or Windows/UI/picture claim'}
    try:
        shutil.copyfile(stock,stage/'Sage.jar')
        for path in runtime.glob('*.so*'):
            if path.is_file(): shutil.copyfile(path,stage/path.name)
        shutil.copytree(runtime/'JARs',stage/'JARs',ignore=shutil.ignore_patterns('*Vibe*','*XMLTV*'))
        for name in ('STVs','fonts'):
            if (runtime/name).is_dir(): shutil.copytree(runtime/name,stage/name)
        (stage/'XMLTV_STOCK_GATE_OWNED').touch()
        (stage/'synthetic.xml').write_text('<?xml version="1.0"?><tv/>\n')
        (stage/'Sage.properties').write_text('\n'.join([
            'devmode=true','disable_plugin_repository_updates=true','network_encoder_discovery=false',
            'linux/enable_vweb_capture=true','linux/disable_dvb_capture=true','enable_media_extender_server=false',
            'enable_server=false','sync_system_clock=false','load_at_startup_runnable_classes=xmltv.StockPluginManagerProbe',
            'wizard/disable_database_backup=true','debug_logging=true','']) )
        for name in ('SageTVPluginsV9.xml','SageTVPluginsV9Beta.xml'):
            (stage/name).write_text('<?xml version="1.0"?><PluginRepository version="1"/>\n')
        dev=stage/'SageTVPluginsDev.d'; dev.mkdir()
        package=ROOT/'output/packages'
        payload=next(package.glob('OpenSageTVVibeXMLTVPlugin-jar-*.zip'))
        shutil.copyfile(payload,dev/payload.name)
        tree=ET.parse(package/'OpenSageTVVibeXMLTVPlugin.xml')
        tree.find('Package/Location').text=(dev/payload.name).as_uri()
        tree.write(dev/'xmltv.xml',encoding='utf-8',xml_declaration=True)
        env=os.environ.copy(); env['LD_LIBRARY_PATH']=str(stage); env['HOME']=str(stage)
        java='/usr/lib/jvm/java-11-openjdk-amd64/bin/java'
        command=[java,'-Djava.awt.headless=true','-Djava.library.path='+str(stage),
            '-cp','Sage.jar:JARs/*:'+str(ROOT/'output/test-classes'),
            'sage.Sage','0','0','x','sagetv Sage.properties']
        for attempt in range(3):
            marker=stage/'stock-plugin-result.properties'; marker.unlink(missing_ok=True)
            log=output/('stock-server-'+str(time.time_ns())+'.log')
            with log.open('wb') as stream:
                process=subprocess.Popen(command,cwd=stage,env=env,stdout=stream,stderr=subprocess.STDOUT)
                try:
                    deadline=time.monotonic()+100
                    while not marker.exists() and process.poll() is None and time.monotonic()<deadline: time.sleep(.25)
                    if not marker.exists(): raise RuntimeError('Stock server did not complete its assertion; see '+str(log))
                    result=properties(marker)
                finally:
                    if process.poll() is None: process.terminate()
                    try: process.wait(timeout=15)
                    except subprocess.TimeoutExpired: process.kill(); process.wait(timeout=5)
            report.setdefault('runs',[]).append({'result':result,'log':str(log),'returncode':process.returncode})
            if result.get('status')=='RESTART_REQUIRED': continue
            if result.get('status')!='PASS': raise RuntimeError(result.get('error','Stock assertion failed'))
            report['status']='PASS'; break
        if report['status']!='PASS': raise RuntimeError('Restart did not qualify plugin')
        report['stockCoreSha256After']=hashlib.sha256((stage/'Sage.jar').read_bytes()).hexdigest()
        if report['stockCoreSha256After']!=checksum: raise RuntimeError('Stock JAR changed')
        report['jarSha256']=hashlib.sha256((ROOT/'output/packages/XMLTVImportPlugin.jar').read_bytes()).hexdigest()
        # Preserve a failed staging tree for diagnosis. On success it contains
        # only generated appdata and runtime copies, never user work.
        shutil.rmtree(stage)
        report['temporaryAppdataRemoved']=True
    except Exception as error:
        report['status']='FAIL'; report['error']=str(error)
    (output/'stock-plugin-manager.json').write_text(json.dumps(report,indent=2)+'\n')
    print(json.dumps(report,indent=2))
    return 0 if report['status']=='PASS' else 1

if __name__=='__main__': raise SystemExit(main())
