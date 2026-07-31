#!/usr/bin/env python3

import importlib.util
import pathlib
import tempfile
import unittest


HERE = pathlib.Path(__file__).resolve().parent
SPEC = importlib.util.spec_from_file_location(
    "gm_runtime_installer", HERE / "install-gm-runtime-bridge-v2.py")
INSTALLER = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(INSTALLER)


HOST = """
function gmRuntimeBridgeStart() {}
function gmRuntimeBridgeStop() {}
function start() {
    gmRuntimeBridgeStart();
}
rpc.exports = { dispose: function () {
    gmRuntimeBridgeStop();
}};
"""


class InstallerTest(unittest.TestCase):

    def setUp(self):
        self.fragment = (HERE / "gm-runtime-bridge-v2.js").read_text(encoding="utf-8")

    def test_switches_lifecycle_and_is_idempotent(self):
        installed = INSTALLER.build_installed_source(HOST, self.fragment)
        INSTALLER.validate_installed_source(installed)
        installed_again = INSTALLER.build_installed_source(installed, self.fragment)
        self.assertEqual(installed, installed_again)
        self.assertNotIn("\n    gmRuntimeBridgeStart();", installed)
        self.assertEqual(1, installed.count(INSTALLER.BEGIN_MARKER))

    def test_install_creates_backup_before_atomic_write(self):
        with tempfile.TemporaryDirectory() as directory:
            root = pathlib.Path(directory)
            target = root / "df_game_r.js"
            target.write_text(HOST, encoding="utf-8")
            backup = INSTALLER.install(target, HERE / "gm-runtime-bridge-v2.js", root / "backups")
            self.assertEqual(HOST, backup.read_text(encoding="utf-8"))
            INSTALLER.validate_installed_source(target.read_text(encoding="utf-8"))

    def test_rejects_host_without_lifecycle_calls(self):
        with self.assertRaises(ValueError):
            INSTALLER.build_installed_source("function start() {}", self.fragment)


if __name__ == "__main__":
    unittest.main()
