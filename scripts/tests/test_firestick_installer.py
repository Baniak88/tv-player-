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
if args[:1] == ['connect']:
    print('failed to connect' if scenario == 'disconnected' else 'connected to '+args[1]); sys.exit(0)
if args == ['devices']:
    count_file = pathlib.Path(os.environ['ADB_COUNT'])
    count = int(count_file.read_text()) if count_file.exists() else 0
    count_file.write_text(str(count+1))
    state = 'unauthorized' if scenario == 'unauthorized' or (scenario == 'authorize-later' and count == 0) else 'device'
    print('List of devices attached\nchromebook-other-device\tdevice')
    if scenario != 'disconnected': print('192.168.1.50:5555\t'+state)
    sys.exit(0)
if args[:2] != ['-s', '192.168.1.50:5555']: sys.exit('UNSCOPED DEVICE COMMAND')
args = args[2:]
if args == ['shell', 'getprop', 'ro.build.version.sdk']: print('22' if scenario == 'old-sdk' else '25')
elif args == ['shell', 'getprop', 'ro.product.model']: print('Test Fire TV')
elif args[:4] == ['shell', 'pm', 'install', '-r']:
    print('Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE]' if scenario in ['signature', 'signature-nonzero'] else 'Failure [INSTALL_FAILED_INSUFFICIENT_STORAGE]' if scenario == 'storage' else 'Success')
    if scenario == 'signature-nonzero': sys.exit(1)
elif args == ['shell', 'pm', 'path', 'pl.tvplayer.television']: print('package:/data/app/pl.tvplayer.television/base.apk')
elif args[:3] == ['shell', 'am', 'start']: print('Error: Activity not started' if scenario == 'no-launch' else 'Status: ok')
elif args[:1] == ['push'] or args[:3] == ['shell', 'rm', '-f']: pass
else: sys.exit('UNEXPECTED COMMAND '+repr(args))
'''


class InstallerTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix='oktv installer ')
        self.path = Path(self.temp.name)
        shutil.copy(ROOT / 'scripts/install-firestick.sh', self.path)
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

    def run_script(self, scenario='', address='192.168.1.50'):
        command = ['bash', str(self.path/'install-firestick.sh')]
        if address is not None:
            command.append(address)
        result = subprocess.run(command, input='192.168.1.50\n' if address is None else '',
                                env=dict(self.env, ADB_SCENARIO=scenario), capture_output=True, text=True, timeout=15)
        log = self.path/'calls'
        self.calls = [json.loads(line) for line in log.read_text().splitlines()] if log.exists() else []
        # This script must never erase data, change another ADB device, or kill its server.
        for args in self.calls:
            self.assertNotIn('uninstall', args)
            self.assertNotIn('kill-server', args)
            self.assertNotIn('clear', args)
            self.assertNotIn('-d', args)
            if 'shell' in args or 'push' in args:
                self.assertEqual(args[:2], ['-s', '192.168.1.50:5555'])
        return result

    def test_install_and_launch_with_spaces_in_path_and_other_adb_device(self):
        result = self.run_script()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn('został zainstalowany', result.stdout)
        self.assertTrue(any(args[2:6] == ['shell', 'pm', 'install', '-r'] for args in self.calls))
        self.assertTrue(any(args[2:5] == ['shell', 'am', 'start'] for args in self.calls))

    def test_waits_for_authorization(self):
        self.env['OKTV_AUTH_TIMEOUT'] = '5'
        self.assertEqual(0, self.run_script('authorize-later').returncode)
        self.assertGreaterEqual(sum(args == ['devices'] for args in self.calls), 2)

    def test_interactive_ip_prompt(self):
        result = self.run_script(address=None)
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn('Adres IP Fire Sticka', result.stdout)

    def test_unauthorized_does_not_install(self):
        self.assertNotEqual(0, self.run_script('unauthorized').returncode)
        self.assertFalse(any('push' in args for args in self.calls))

    def test_connect_exit_zero_does_not_prove_connection(self):
        self.assertNotEqual(0, self.run_script('disconnected').returncode)
        self.assertFalse(any('shell' in args for args in self.calls))

    def test_old_fire_os_rejected_before_push(self):
        result = self.run_script('old-sdk')
        self.assertNotEqual(0, result.returncode)
        self.assertIn('zbyt stara', result.stderr)
        self.assertFalse(any('push' in args for args in self.calls))

    def test_pm_failure_exit_zero_and_signature_conflict(self):
        result = self.run_script('signature')
        self.assertNotEqual(0, result.returncode)
        self.assertIn('inny podpis', result.stderr)
        self.assertFalse(any('am' in args for args in self.calls))

    def test_insufficient_storage_reports_failure(self):
        result = self.run_script('storage')
        self.assertNotEqual(0, result.returncode)
        self.assertIn('Brakuje miejsca', result.stderr)

    def test_pm_failure_nonzero_retains_specific_message(self):
        result = self.run_script('signature-nonzero')
        self.assertNotEqual(0, result.returncode)
        self.assertIn('inny podpis', result.stderr)

    def test_installed_but_launcher_fails(self):
        result = self.run_script('no-launch')
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIn('ręcznie', result.stdout)

    def test_checksum_failure_does_not_contact_device(self):
        self.apk.write_bytes(b'corrupted')
        result = self.run_script()
        self.assertNotEqual(0, result.returncode)
        self.assertIn('Suma kontrolna', result.stderr)
        self.assertEqual([], self.calls)

    def test_invalid_addresses_rejected_before_adb(self):
        for address in ['192.168.1.999', '192.168.1.50:0', '192.168.1.50:65536', '$(touch /tmp/no)', '-s other', 'localhost']:
            with self.subTest(address=address):
                self.assertNotEqual(0, self.run_script(address=address).returncode)
                self.assertEqual([], self.calls)

    def test_bundle_contains_exact_apk_and_installer(self):
        with zipfile.ZipFile(self.apk, 'w') as archive:
            archive.writestr('AndroidManifest.xml', b'test')
            archive.writestr('classes.dex', b'test')
        output = self.path/'bundle.zip'
        subprocess.run(['python3', str(ROOT/'scripts/package-firestick.py'), '--apk', str(self.apk), '--output', str(output)], check=True, capture_output=True)
        with zipfile.ZipFile(output) as archive:
            self.assertEqual({'OKTV-Player-TV.apk', 'SHA256SUMS', 'install-firestick.sh', 'CZYTAJ-CHROMEBOOK.md'}, set(archive.namelist()))
            self.assertEqual(self.apk.read_bytes(), archive.read('OKTV-Player-TV.apk'))
            self.assertEqual((ROOT/'scripts/install-firestick.sh').read_bytes(), archive.read('install-firestick.sh'))
            self.assertIn(hashlib.sha256(self.apk.read_bytes()).hexdigest(), archive.read('SHA256SUMS').decode())


if __name__ == '__main__':
    unittest.main()
