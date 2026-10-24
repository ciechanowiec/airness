#!/usr/bin/env python3
"""Serves the @airness/node tarball this reactor packed, and the fixtures' own scoped packages, as an npm
registry on a local port.

The package is not published, and a consumer must install it by its version rather than from a path, so
the fixtures install it from this registry through a scoped registry line in their .npmrc. It answers the
package document requests and the tarball requests, nothing else. Every other package still comes from the
public registry, because the scope line routes only @airness. The harness package is dated now, as a fresh
release is; every other package is dated years back, so the release-age floor, read from this registry,
admits it.

    npm-registry.py <port-file> <harness-tarball> [<tarball> ...]

It writes the port it bound to into the port file once it serves, and stops when the shell that started it
is gone, so a run that is interrupted leaves no server behind.
"""

import base64
import hashlib
import io
import json
import os
import sys
import tarfile
import threading
import time
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

OLD_RELEASE = '2020-01-01T00:00:00.000Z'
PARENT_POLL_SECONDS = 2


def manifest_of(tarball):
    with tarfile.open(tarball, 'r:gz') as archive:
        member = archive.extractfile('package/package.json')
        if member is None:
            raise SystemExit(f'{tarball} holds no package/package.json')
        return json.load(io.TextIOWrapper(member, encoding='utf-8'))


def served(tarball, published):
    with open(tarball, 'rb') as stream:
        content = stream.read()
    manifest = manifest_of(tarball)
    name, version = manifest['name'], manifest['version']
    path = f'/{name}/-/{name.split("/")[-1]}-{version}.tgz'
    return name, {'manifest': manifest, 'content': content, 'path': path, 'published': published}


def main():
    port_file, harness, others = sys.argv[1], sys.argv[2], sys.argv[3:]
    now = datetime.now(timezone.utc).isoformat(timespec='milliseconds').replace('+00:00', 'Z')
    packages = dict([served(harness, now)] + [served(tarball, OLD_RELEASE) for tarball in others])
    documents = {f'/{name}': name for name in packages}
    documents.update({'/' + name.replace('/', '%2f'): name for name in packages})
    documents.update({'/' + name.replace('/', '%2F'): name for name in packages})
    tarballs = {package['path']: package for package in packages.values()}

    class Registry(BaseHTTPRequestHandler):
        def do_GET(self):
            if self.path in documents:
                name = documents[self.path]
                package = packages[name]
                version = package['manifest']['version']
                published = package['published']
                content = package['content']
                document = {
                    'name': name,
                    'dist-tags': {'latest': version},
                    'time': {version: published, 'created': published, 'modified': published},
                    'versions': {
                        version: {
                            **package['manifest'],
                            'dist': {
                                'tarball': f'http://{self.headers.get("Host")}{package["path"]}',
                                'shasum': hashlib.sha1(content).hexdigest(),
                                'integrity': 'sha512-' + base64.b64encode(hashlib.sha512(content).digest()).decode(),
                            },
                        }
                    },
                }
                self.reply(200, 'application/json', json.dumps(document).encode())
            elif self.path in tarballs:
                self.reply(200, 'application/octet-stream', tarballs[self.path]['content'])
            else:
                self.reply(404, 'application/json', b'{"error":"not found"}')

        def reply(self, status, content_type, body):
            self.send_response(status)
            self.send_header('Content-Type', content_type)
            self.send_header('Content-Length', str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def log_message(self, *arguments):
            pass

    server = ThreadingHTTPServer(('127.0.0.1', 0), Registry)
    parent = os.getppid()

    def stop_with_parent():
        while os.getppid() == parent:
            time.sleep(PARENT_POLL_SECONDS)
        server.shutdown()

    threading.Thread(target=stop_with_parent, daemon=True).start()
    with open(port_file + '.partial', 'w', encoding='utf-8') as stream:
        stream.write(str(server.server_address[1]))
    os.replace(port_file + '.partial', port_file)
    server.serve_forever()


if __name__ == '__main__':
    main()
