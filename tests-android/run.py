"""Build/run the touch regression harness on an explicitly selected emulator only."""
import argparse
from pathlib import Path
import subprocess
import tempfile
import time
import zipfile

p = argparse.ArgumentParser(description=__doc__)
p.add_argument('--sdk', type=Path, required=True)
p.add_argument('--java-home', type=Path, required=True)
p.add_argument('--serial', required=True)
a = p.parse_args()
if not a.serial.startswith('emulator-'):
    raise ValueError('This test runner accepts emulator serials only; never a phone.')
root = Path(__file__).resolve().parent.parent
work = Path(tempfile.mkdtemp(prefix='touch-test-', dir=root / 'build'))
bt = a.sdk / 'build-tools/36.0.0'
android = a.sdk / 'platforms/android-36/android.jar'
java = a.java_home / 'bin/java.exe'
adb = [str(a.sdk / 'platform-tools/adb.exe'), '-s', a.serial]
def run(*args):
    subprocess.run([str(x) for x in args], check=True)
(work / 'classes').mkdir()
(work / 'dex').mkdir()
run(a.java_home / 'bin/javac.exe', '--release', '8', '-cp', android, '-d', work / 'classes',
    root / 'src/org/ungoogled/ui/MarkerTouchRouter.java',
    root / 'tests-android/org/ungoogled/ui/MarkerTouchActivity.java')
with zipfile.ZipFile(work / 'code.jar', 'w') as z:
    for f in (work / 'classes').rglob('*.class'):
        z.write(f, f.relative_to(work / 'classes').as_posix())
run(java, '-cp', bt / 'lib/d8.jar', 'com.android.tools.r8.D8', '--min-api', '32', '--lib', android,
    '--output', work / 'dex', work / 'code.jar')
run(bt / 'aapt2.exe', 'link', '--manifest', root / 'tests-android/AndroidManifest.xml', '-I', android,
    '-o', work / 'unsigned.apk')
with zipfile.ZipFile(work / 'unsigned.apk', 'a') as z:
    z.write(work / 'dex/classes.dex', 'classes.dex')
# Disposable test certificate, unrelated to the private Maps update key.
run(a.java_home / 'bin/keytool.exe', '-genkeypair', '-keystore', work / 'test.jks', '-storepass', 'android',
    '-keypass', 'android', '-alias', 'test', '-dname', 'CN=Local Touch Test', '-keyalg', 'RSA', '-validity', '2')
run(java, '-jar', bt / 'lib/apksigner.jar', 'sign', '--ks', work / 'test.jks', '--ks-pass', 'pass:android',
    '--out', work / 'test.apk', work / 'unsigned.apk')
run(*adb, 'install', '--no-incremental', '-r', work / 'test.apk')
try:
    run(*adb, 'shell', 'am', 'start', '-W', '-n', 'org.ungoogled.touchtest/org.ungoogled.ui.MarkerTouchActivity')
    for _ in range(30):
        result = subprocess.run(adb + ['shell', 'run-as', 'org.ungoogled.touchtest', 'cat', 'files/result.txt'], capture_output=True, text=True)
        if result.returncode == 0:
            (work / 'result.txt').write_text(result.stdout)
            print(result.stdout)
            if 'SUCCESS ' not in result.stdout:
                raise RuntimeError('Touch regression failed')
            break
        time.sleep(1)
    else:
        raise RuntimeError('Timed out waiting for touch regression results')
finally:
    run(*adb, 'uninstall', 'org.ungoogled.touchtest')
print('Evidence:', work)
