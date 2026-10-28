from pathlib import Path
import subprocess,zipfile,os
project=Path(__file__).resolve().parent
output=project.parent/'apk/TclVpnGuard.apk'
output.parent.mkdir(parents=True,exist_ok=True)
build=project/'build'
for name in ('classes','dex'):(build/name).mkdir(parents=True,exist_ok=True)
sdk=Path(os.environ.get('ANDROID_SDK_ROOT') or os.environ.get('ANDROID_HOME') or str(Path(os.environ['LOCALAPPDATA'])/'Android/Sdk'))
bt=sdk/'build-tools/36.0.0'
jar=sdk/'platforms/android-35/android.jar'
java=Path(os.environ.get('JAVA_HOME') or os.environ['JAVA_HOME'])/'bin'
env=dict(os.environ,JAVA_HOME=str(java.parent))
def run(args):
    print('RUN',Path(str(args[0])).name,flush=True)
    subprocess.run([str(a) for a in args],check=True,env=env)
run([bt/'aapt2.exe','link','-I',jar,'--manifest',project/'AndroidManifest.xml','-o',build/'base.apk','--min-sdk-version','28','--target-sdk-version','28'])
run([java/'javac.exe','--release','8','-encoding','UTF-8','-classpath',jar,'-d',build/'classes',*project.glob('src/**/*.java')])
run([bt/'d8.bat','--min-api','28','--lib',jar,'--output',build/'dex',*build.glob('classes/**/*.class')])
unsigned=build/'unsigned.apk'
unsigned.write_bytes((build/'base.apk').read_bytes())
with zipfile.ZipFile(unsigned,'a',compression=zipfile.ZIP_DEFLATED) as z:z.write(build/'dex/classes.dex','classes.dex')
run([bt/'zipalign.exe','-f','4',unsigned,build/'aligned.apk'])
key=Path(os.environ.get('TVHOME_KEYSTORE') or str(project.parent/'keys/local.jks'))
key.parent.mkdir(parents=True,exist_ok=True)
if not key.exists():
    run([java/'keytool.exe','-genkeypair','-keystore',key,'-storepass','android','-keypass','android','-alias','guard','-keyalg','RSA','-keysize','2048','-validity','3650','-dname','CN=TCL local VPN guard'])
run([bt/'apksigner.bat','sign','--ks',key,'--ks-pass','pass:android','--ks-key-alias','guard','--min-sdk-version','28','--out',output,build/'aligned.apk'])
run([bt/'apksigner.bat','verify','--verbose','--min-sdk-version','28',output])
run([bt/'zipalign.exe','-c','4',output])
print('APK:',output,'size:',output.stat().st_size,flush=True)
