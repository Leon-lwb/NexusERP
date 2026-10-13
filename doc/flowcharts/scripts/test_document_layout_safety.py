"""Safety regressions; no real Office application is activated by these tests."""

from pathlib import Path
from types import SimpleNamespace
import sys
import unittest
from unittest.mock import patch

import verify_document_layout as layout


class Documents:
    def __init__(self, app, path):
        self.app = app
        self.path = path
        self.Count = 0

    def Open(self, *args, **kwargs):
        self.app.changes.append("Open")
        self.Count = 1
        self.document = SimpleNamespace(
            FullName=str(self.path), ActiveWindow=SimpleNamespace(Hwnd=self.app.Hwnd),
            Close=self.close,
        )
        return self.document

    def close(self, *args):
        self.app.changes.append("Close")
        self.Count = 0

    def Item(self, index):
        return self.document


class Application:
    def __init__(self, path, hwnd):
        self.changes = []
        self.Hwnd = hwnd
        self.Documents = Documents(self, path)

    def __setattr__(self, name, value):
        if name in ("Visible", "DisplayAlerts", "AutomationSecurity"):
            self.changes.append(name)
        object.__setattr__(self, name, value)

    def Quit(self, *args):
        self.changes.append("Quit")


@unittest.skipUnless(sys.platform == "win32", "Windows COM safety adapter")
class RenderSafetyTests(unittest.TestCase):
    def render(self, owner_pid, stays_alive=False, hwnd=100, visible=False, executable_matches=True):
        path = Path(__file__).resolve().parent / "unopened-fixture.docx"
        app = Application(path, hwnd)
        self.app = app
        self.report = {}
        server = {"clsid": "test", "executable": str(path), "engine": "test"}
        with patch.object(layout, "installed_renderers", return_value=[server]), \
                patch.object(layout, "office_processes", return_value={6200: str(path), 999: str(path)}), \
                patch("win32com.client.DispatchEx", return_value=app), \
                patch("win32process.EnumProcesses", side_effect=lambda: [6200, 999] if stays_alive and "Quit" in app.changes else [6200]), \
                patch("win32process.GetWindowThreadProcessId", return_value=(1, owner_pid)), \
                patch("win32gui.IsWindow", return_value=True), \
                patch("win32gui.GetAncestor", return_value=100), \
                patch("win32gui.IsWindowVisible", return_value=visible), \
                patch.object(layout.os.path, "samefile", return_value=executable_matches), \
                patch.object(layout, "refresh_document"), patch.object(layout, "export_pdf"), \
                patch.object(layout.time, "monotonic", side_effect=[0, 10]), \
                patch.object(layout.time, "sleep"):
            layout.render_copy({"working_copy": path, "pdf": path.with_suffix(".pdf")}, False, self.report)

    def test_existing_empty_application_plus_unrelated_new_pid_is_untouched(self):
        with self.assertRaises(layout.VerificationError):
            self.render(owner_pid=6200)
        self.assertEqual(self.app.changes, [])

    def test_confirmed_process_that_does_not_exit_is_failure(self):
        with self.assertRaises(layout.VerificationError):
            self.render(owner_pid=999, stays_alive=True)
        self.assertFalse(self.report["office"]["cleanup"]["document_process_exited"])

    def test_unavailable_read_only_binding_is_fail_closed(self):
        with self.assertRaisesRegex(layout.VerificationError, "Cannot bind the empty Office COM object"):
            self.render(owner_pid=999, hwnd=None)
        self.assertEqual(self.app.changes, [])
        self.assertFalse(self.report["office"]["isolation"]["pre_open_binding_confirmed"])
        self.assertFalse(self.report["office"]["cleanup"]["quit_called"])

    def test_bound_new_process_can_render_and_exit(self):
        self.render(owner_pid=999)
        isolation = self.report["office"]["isolation"]
        self.assertTrue(isolation["pre_open_binding_confirmed"])
        self.assertEqual(isolation["application_process_id"], 999)
        self.assertTrue(self.report["office"]["cleanup"]["document_process_exited"])
        self.assertEqual(self.app.changes, ["Visible", "DisplayAlerts", "AutomationSecurity", "Open", "Close", "Quit"])

    def test_visible_application_is_not_modified(self):
        with self.assertRaisesRegex(layout.VerificationError, "window is visible"):
            self.render(owner_pid=999, visible=True)
        self.assertEqual(self.app.changes, [])

    def test_executable_mismatch_is_not_modified(self):
        with self.assertRaisesRegex(layout.VerificationError, "COM executable"):
            self.render(owner_pid=999, executable_matches=False)
        self.assertEqual(self.app.changes, [])


if __name__ == "__main__":
    unittest.main(verbosity=2)
