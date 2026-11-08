"""Exercise the deployed URI/authentication boundary with a real, unprivileged Nginx."""
import hashlib
import http.server
import os
from pathlib import Path
import re
import shutil
import socket
import subprocess
import tempfile
import threading
import unittest
import urllib.error
import urllib.parse
import urllib.request


class GarminWebhookProxyTest(unittest.TestCase):
    def test_authenticated_delivery_and_private_logs(self):
        token = "t" * 43
        wrong_token = "w" * 43
        received = []

        class Backend(http.server.BaseHTTPRequestHandler):
            def do_POST(self):
                body = self.rfile.read(int(self.headers.get("Content-Length", "0")))
                received.append((self.path, hashlib.sha256(body).hexdigest()))
                url = urllib.parse.urlsplit(self.path)
                valid = url.path == "/api/v1/garmin/webhook" and url.query == "token=" + token
                self.send_response(200 if valid else 401)
                self.end_headers()

            def log_message(self, *_args):
                pass

        backend = http.server.ThreadingHTTPServer(("127.0.0.1", 0), Backend)
        thread = threading.Thread(target=backend.serve_forever, daemon=True)
        thread.start()
        nginx = os.environ.get("NGINX_BIN") or shutil.which("nginx")
        self.assertIsNotNone(nginx, "Set NGINX_BIN to the installed Nginx executable")
        with tempfile.TemporaryDirectory(prefix="garmin-proxy-") as directory:
            root = Path(directory)
            with socket.socket() as probe:
                probe.bind(("127.0.0.1", 0))
                port = probe.getsockname()[1]
            template = Path(__file__).with_name("dev-runtaeho.conf.example").read_text()
            site = template.replace("__GARMIN_WEBHOOK_TOKEN__", token)
            site = site.replace("listen 12643 ssl;", f"listen 127.0.0.1:{port};")
            site = site.replace("listen [::]:12643 ssl;", "")
            site = re.sub(r"^\s*listen (?:\[::\]:)?443 ssl;", "", site, flags=re.M)
            site = re.sub(r"^\s*(?:ssl_certificate|ssl_certificate_key|ssl_dhparam|include /etc/letsencrypt)[^;]*;", "", site, flags=re.M)
            site = site.replace("/var/log/nginx/", str(root) + "/")
            site = site.replace("http://127.0.0.1:12642", f"http://127.0.0.1:{backend.server_port}")
            config = root / "nginx.conf"
            config.write_text(
                f"worker_processes 1; pid {root}/nginx.pid; error_log {root}/error.log;\n"
                f"events {{}} http {{ client_body_temp_path {root}/body;\n{site}\n}}\n"
            )
            command = [nginx, "-p", str(root) + "/", "-c", str(config)]
            started = False
            try:
                subprocess.run(command, check=True, capture_output=True)
                started = True

                def post(path, body=b"{}"):
                    request = urllib.request.Request(
                        f"http://127.0.0.1:{port}" + path, data=body,
                        headers={"Content-Type": "application/json"}, method="POST",
                    )
                    try:
                        with urllib.request.urlopen(request, timeout=10) as response:
                            return response.status
                    except urllib.error.HTTPError as error:
                        status = error.code
                        error.close()
                        return status

                # A real ActivityDetail can exceed the default 1 MiB Nginx limit.
                body = b'{"activityDetails":[]}' + b" " * (1024 * 1024 + 1)
                path = "/api/v1/garmin/webhook/" + token
                self.assertEqual(200, post(path + "?token=untrusted-query", body))
                self.assertEqual([("/api/v1/garmin/webhook?token=" + token,
                                   hashlib.sha256(body).hexdigest())], received)
                self.assertEqual(404, post("/api/v1/garmin/webhook/" + wrong_token))
                self.assertEqual(1, len(received), "Unknown paths must not reach the backend")
                self.assertEqual(401, post("/api/v1/garmin/webhook"))
                backend.shutdown()
                backend.server_close()
                self.assertEqual(502, post(path))
                logs = "\n".join(p.read_text() for p in root.glob("*.log"))
                self.assertIn("/api/v1/garmin/webhook", logs)
                for private in (token, wrong_token, "untrusted-query"):
                    self.assertNotIn(private, logs)
            finally:
                if started:
                    subprocess.run(command + ["-s", "quit"], capture_output=True)
                backend.shutdown()
                backend.server_close()
                thread.join(timeout=5)


if __name__ == "__main__":
    unittest.main()
