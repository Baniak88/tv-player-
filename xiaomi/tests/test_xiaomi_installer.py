import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[2]
FAKE_ADB = r'''#!/usr/bin/env python3
import json, os, pathlib, sys
args = sys.argv[1:]
with open(os.environ['ADB_LOG'], 'a') as output: output.write(json.dumps(args)+'\n')
scenario = os.environ.get('ADB_SCENARIO', '')
target = os.environ.get('ADB_TARGET', '192.168.1.50:5555')
if args[:1] == ['pair']:
    if scenario == 'pair-fail': print('Failed: Wrong password'); sys.exit(1)
    print('Successfully paired to '+args[1]); sys.exit(0)
if args[:1] == ['connect']:
    print('failed to connect' if scenario == 'disconnected' else 'connected to '+args[1]); sys.exit(0)
if args == ['devices']:
    count_file = pathlib.Path(os.environ['ADB_COUNT'])
    count = int(count_file.read_text()) if count_file.exists() else 0
    count_file.write_text(str(count+1))
    state = 'unauthorized' if scenario == 'unauthorized' or (scenario == 'authorize-later' and count == 0) else 'device'
    print('List of devices attached\nchromebook-other-device\tdevice')
    if scenario != 'disconnected': print(target+'\t'+state)
    sys.exit(0)
if args[:2] != ['-s', target]: sys.exit('UNSCOPED DEVICE COMMAND')
args = args[2:]
if args == ['shell', 'getprop', 'ro.build.version.sdk']: print('22' if scenario == 'old-sdk' else '34')
elif args == ['shell', 'getprop', 'ro.product.model']: print('Other TV' if scenario == 'other-model' else 'MiTV-AFMU0')
elif args[:4] == ['shell', 'pm', 'install', '-r']:
    print('Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE]' if scenario == 'signature' else 'Failure [INSTALL_FAILED_INSUFFICIENT_STORAGE]' if scenario == 'storage' else 'Success')
elif args == ['shell', 'pm', 'path', 'pl.tvplayer.television']: print('package:/data/app/pl.tvplayer.television/base.apk')
elif args[:3] == ['shell', 'am', 'start']: print('Error: Activity not started' if scenario == 'no-launch' else 'Status: ok')
elif args[:1] == ['push'] or args[:3] == ['shell', 'rm', '-f']: pass
else: sys.exit('UNEXPECTED COMMAND '+repr(args))
'''


class XiaomiInstallerTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='oktv xiaomi ')
        self.path = Path(self.temp.name)
        shutil.copy(ROOT / 'xiaomi/install-xiaomi.sh', self.path)
        self.apk = self.path / 'OKTV-Player-TV.apk'
        self.apk.write_bytes(b'PK\x03\x04test-apk')
        (self.path / 'SHA256SUMS').write_text(hashlib.sha256(self.apk.read_bytes()).hexdigest() + '  OKTV-Player-TV.apk\n')
        adb = self.path / 'adb'
        adb.write_text(FAKE_ADB)
        adb.chmod(0o755)
        self.env = dict(os.environ, PATH=str(self.path)+os.pathsep+os.environ['PATH'],
                        ADB_LOG=str(self.path/'calls'), ADB_COUNT=str(self.path/'count'), OKTV_AUTH_TIMEOUT='1')

    def tearDown(self):
        self.temp.cleanup()

    def run_script(self, scenario='', *args, stdin='', target='192.168.1.50:5555'):
        command = ['bash', str(self.path/'install-xiaomi.sh'), *args]
        result = subprocess.run(command, input=stdin, env=dict(self.env, ADB_SCENARIO=scenario, ADB_TARGET=target),
                                capture_output=True, text=True, timeout=15)
        log = self.path/'calls'
        self.calls = [json.loads(line) for line in log.read_text().splitlines()] if log.exists() else []
        for call in self.calls:
            self.assertNotIn('uninstall', call)
            self.assertNotIn('kill-server', call)
            self.assertNotIn('clear', call)
            if 'shell' in call or 'push' in call:
                self.assertEqual(call[:2], ['-s', target])
        return result

    def test_installs_same_package_on_android_14_box(self):
        result = self.run_script('', '192.168.1.50')
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn('MiTV-AFMU0 (Android API 34)', result.stdout)
        self.assertIn('został zainstalowany', result.stdout)
        self.assertNotIn('nie wygląda na Xiaomi', result.stdout)
        self.assertTrue(any(call[2:6] == ['shell', 'pm', 'install', '-r'] for call in self.calls))
        self.assertFalse(any(call[:1] == ['pair'] for call in self.calls))

    def test_interactive_ip_prompt(self):
        result = self.run_script('', stdin='192.168.1.50\n')
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn('Adres IP Xiaomi TV Box', result.stdout)

    def test_wireless_debugging_pairing_then_connect_port(self):
        result = self.run_script('', '192.168.1.50:41234', '--pair', '192.168.1.50:37123', target='192.168.1.50:41234')
        self.assertEqual(0, result.returncode, result.stderr)
        pair_index = self.calls.index(['pair', '192.168.1.50:37123'])
        connect_index = self.calls.index(['connect', '192.168.1.50:41234'])
        self.assertLess(pair_index, connect_index)

    def test_failed_pairing_stops_before_install(self):
        result = self.run_script('pair-fail', '192.168.1.50', '--pair', '192.168.1.50:37123')
        self.assertNotEqual(0, result.returncode)
        self.assertIn('Parowanie nie powiodło się', result.stderr)
        self.assertFalse(any('push' in call for call in self.calls))

    def test_invalid_pairing_address_rejected_before_adb(self):
        for pair in ['192.168.1.50', '192.168.1.300:1', '$(touch /tmp/no):1', '-s']:
            with self.subTest(pair=pair):
                self.assertNotEqual(0, self.run_script('', '192.168.1.50', '--pair', pair).returncode)
                self.assertEqual([], self.calls)

    def test_other_model_warns_but_installs(self):
        result = self.run_script('other-model', '192.168.1.50')
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn('nie wygląda na Xiaomi', result.stdout)

    def test_unauthorized_does_not_install(self):
        result = self.run_script('unauthorized', '192.168.1.50')
        self.assertNotEqual(0, result.returncode)
        self.assertIn('--pair', result.stderr)
        self.assertFalse(any('push' in call for call in self.calls))

    def test_old_system_rejected_before_push(self):
        result = self.run_script('old-sdk', '192.168.1.50')
        self.assertNotEqual(0, result.returncode)
        self.assertIn('zbyt stara', result.stderr)
        self.assertFalse(any('push' in call for call in self.calls))

    def test_signature_conflict_keeps_app(self):
        result = self.run_script('signature', '192.168.1.50')
        self.assertNotEqual(0, result.returncode)
        self.assertIn('inny podpis', result.stderr)

    def test_storage_failure_names_device(self):
        result = self.run_script('storage', '192.168.1.50')
        self.assertNotEqual(0, result.returncode)
        self.assertIn('Brakuje miejsca na Xiaomi TV Box', result.stderr)

    def test_launch_failure_points_to_google_tv_apps(self):
        result = self.run_script('no-launch', '192.168.1.50')
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn('Twoje aplikacje', result.stdout)

    def test_checksum_failure_does_not_contact_device(self):
        self.apk.write_bytes(b'corrupted')
        result = self.run_script('', '192.168.1.50')
        self.assertNotEqual(0, result.returncode)
        self.assertIn('Suma kontrolna', result.stderr)
        self.assertEqual([], self.calls)

    def test_invalid_addresses_rejected_before_adb(self):
        for address in ['192.168.1.999', '192.168.1.50:0', '192.168.1.50:65536', '$(touch /tmp/no)', '-s other', 'localhost']:
            with self.subTest(address=address):
                self.assertNotEqual(0, self.run_script('', address).returncode)
                self.assertEqual([], self.calls)

    def test_bundle_has_identical_apk_to_fire_stick_bundle(self):
        with zipfile.ZipFile(self.apk, 'w') as archive:
            archive.writestr('AndroidManifest.xml', b'test')
            archive.writestr('classes.dex', b'test')
        xiaomi, fire = self.path/'xiaomi.zip', self.path/'fire.zip'
        subprocess.run(['python3', str(ROOT/'xiaomi/package-xiaomi.py'), '--apk', str(self.apk), '--output', str(xiaomi)], check=True, capture_output=True)
        subprocess.run(['python3', str(ROOT/'scripts/package-firestick.py'), '--apk', str(self.apk), '--output', str(fire)], check=True, capture_output=True)
        with zipfile.ZipFile(xiaomi) as archive, zipfile.ZipFile(fire) as fire_archive:
            self.assertEqual({'OKTV-Player-TV.apk', 'SHA256SUMS', 'install-xiaomi.sh', 'CZYTAJ-XIAOMI.md'}, set(archive.namelist()))
            self.assertEqual(fire_archive.read('OKTV-Player-TV.apk'), archive.read('OKTV-Player-TV.apk'))
            self.assertEqual(fire_archive.read('SHA256SUMS'), archive.read('SHA256SUMS'))
            self.assertEqual((ROOT/'xiaomi/install-xiaomi.sh').read_bytes(), archive.read('install-xiaomi.sh'))


if __name__ == '__main__':
    unittest.main()
