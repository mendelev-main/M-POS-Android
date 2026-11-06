#!/usr/bin/env python3
"""Local-only pinned Web reference. Never serves the checkout or user backups wholesale."""
import argparse, hashlib, json, mimetypes, re, subprocess
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import unquote, urlparse

REPO = Path(__file__).resolve().parents[1]
TOOLS = REPO / 'tools/web-reference'

def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--source', type=Path, default=REPO.parent / 'reference')
    p.add_argument('--output', type=Path, default=REPO / 'output/playwright/web-reference')
    p.add_argument('--port', type=int, default=8765)
    args = p.parse_args()
    source = args.source.resolve()
    spec = json.loads((REPO / 'config/reference.json').read_text())
    commit = subprocess.check_output(['git', '-C', str(source), 'rev-parse', 'HEAD'], text=True).strip()
    if commit != spec['commit']:
        p.error('Web checkout must match config/reference.json; do not capture a moving HEAD')
    assets = source / 'app/src/main/assets/pos'
    # All reviewed source hashes must still match, not just the selected commit label.
    for name, expected in spec['files'].items():
        if hashlib.sha256((source / name).read_bytes()).hexdigest() != expected:
            p.error('Pinned source changed: ' + name)
    args.output.mkdir(parents=True, exist_ok=True)
    scenes = json.loads((TOOLS / 'scenes.json').read_text())
    manifest = dict(reference=spec['commit'], nativeBase='b36f6101f711c26abc8d48feda1c9055b7c05778',
                    viewport=dict(width=1280, height=900, deviceScaleFactor=1.25),
                    scenes=scenes, verifiedPixelParity=False)
    for name in ['index.html', 'gallery.js', 'gallery.css', 'measure.js']:
        (args.output / name).write_bytes((TOOLS / name).read_bytes())
    (args.output / 'manifest.json').write_text(json.dumps(manifest, ensure_ascii=False, indent=2))
    (args.output / 'manifest.js').write_text('window.REFERENCE_MANIFEST='+json.dumps(manifest, ensure_ascii=False)+';')

    class Handler(BaseHTTPRequestHandler):
        def do_GET(self):
            path = unquote(urlparse(self.path).path)
            if path == '/': path = '/index.html'
            base, rel = (assets, path[8:]) if path.startswith('/source/') else (args.output.resolve(), path[1:])
            if path in ['/fixture.js', '/bootstrap.js']:
                base, rel = TOOLS, path[1:]
            f = (base / rel).resolve()
            if not f.is_relative_to(base.resolve()) or not f.is_file():
                self.send_error(404); return
            data = f.read_bytes()
            if path == '/source/pos.html':
                data = data.replace(b'<head>', b'<head><script src="/bootstrap.js"></script>', 1)
                data = data.replace(b'</body>', b'<script src="/measure.js"></script><script src="/fixture.js"></script></body>', 1)
            self.send_response(200)
            self.send_header('Content-Type', mimetypes.guess_type(f.name)[0] or 'application/octet-stream')
            self.send_header('Content-Length', str(len(data)))
            self.send_header('Cache-Control', 'no-store')
            self.send_header('X-Content-Type-Options', 'nosniff')
            self.send_header('Content-Security-Policy', "default-src 'self' data: blob:; script-src 'self' 'unsafe-inline' 'unsafe-eval'; style-src 'self' 'unsafe-inline'; connect-src 'self'; object-src 'none'; form-action 'none'")
            self.end_headers(); self.wfile.write(data)
        def log_message(self, fmt, *a):
            if a and str(a[1] if len(a)>1 else '') not in ('200',):
                super().log_message(fmt, *a)
    print(f'Web reference {commit[:7]}: http://127.0.0.1:{args.port}', flush=True)
    ThreadingHTTPServer(('127.0.0.1', args.port), Handler).serve_forever()

if __name__ == '__main__': main()
