import os
import sys
import time
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer

class RobustHandler(SimpleHTTPRequestHandler):
    def __init__(self, *args, **kwargs):
        super().__init__(*args, directory="/app/applet/public", **kwargs)

    def handle(self):
        try:
            super().handle()
        except (BrokenPipeError, ConnectionResetError, ConnectionAbortedError, OSError):
            pass

    def copyfile(self, source, outputfile):
        try:
            super().copyfile(source, outputfile)
        except (BrokenPipeError, ConnectionResetError, ConnectionAbortedError, OSError):
            pass

    def log_message(self, format, *args):
        # Suppress noisy logs but keep server silent and stable
        pass

class ReusableServer(ThreadingHTTPServer):
    allow_reuse_address = True
    daemon_threads = True

if __name__ == '__main__':
    while True:
        try:
            with ReusableServer(('0.0.0.0', 3000), RobustHandler) as httpd:
                httpd.serve_forever()
        except Exception as e:
            time.sleep(1)
