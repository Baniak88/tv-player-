#!/usr/bin/env python3
"""CI-only bridge: feed actual accelerometer vectors to the running emulator."""
from http.server import BaseHTTPRequestHandler, HTTPServer
import subprocess


class RotationHandler(BaseHTTPRequestHandler):
    def do_GET(self):
        vectors = {"/rotate/0": "0:9.81:0", "/rotate/1": "9.81:0:0"}
        if self.path != "/health" and self.path not in vectors:
            self.send_error(404)
            return
        if self.path in vectors:
            try:
                subprocess.run(
                    ["adb", "emu", "sensor", "set", "acceleration", vectors[self.path]],
                    check=True, timeout=10,
                )
            except (subprocess.SubprocessError, OSError) as error:
                self.send_error(503, str(error))
                return
        self.send_response(200)
        self.end_headers()
        self.wfile.write(b"ok\n")


if __name__ == "__main__":
    HTTPServer.allow_reuse_address = True
    HTTPServer(("127.0.0.1", 8769), RotationHandler).serve_forever()
