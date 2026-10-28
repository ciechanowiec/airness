#!/usr/bin/env python3
"""Serves npm package tarballs as a registry on a local port, until the harness packages are published.

Neither @airness/node nor ploaness is on npm yet, and a node project must install a harness package by its
version from a registry rather than from a path, because the wiring and release-age gates refuse a path. This
answers the package-document and tarball requests for the tarballs it is given. Each tarball is read when it is
requested, so a tarball packed after the registry started is served as it is now. The harness tarballs are dated
now, as a fresh release is; the others are dated years back, so the release-age floor admits them.

A scoped harness package can be routed here by a scope line alone. ploaness is unscoped, so a project that
installs it names this registry as its default one, and with --upstream every other request is answered from the
public registry, the tarball addresses in its documents rewritten to come through here, so the lockfile records
plain registry resolutions.

    npm-registry.py (--port <port> | --port-file <file>) --fresh <tarball> ... [--old <tarball> ...] [--upstream]
                    [--npmrc <file> --scope <scope>]

With --npmrc it writes the scope line naming the bound port into that file before the port file, so a build that
waits for the port file finds the configuration already in place.

With --port it binds that port and fails when the port is taken. With --port-file it binds a free port and
writes it into the file once it serves. It stops when the process that started it is gone, so an interrupted
build leaves no server behind.
"""

import argparse
import base64
import hashlib
import io
import json
import os
import sys
import tarfile
import threading
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

OLD_RELEASE = '2020-01-01T00:00:00.000Z'
PARENT_POLL_SECONDS = 2
UPSTREAM = 'https://registry.npmjs.org'
UPSTREAM_TIMEOUT_SECONDS = 120
CHUNK = 1 << 16


def manifest_of(content):
    with tarfile.open(fileobj=io.BytesIO(content), mode='r:gz') as archive:
        member = archive.extractfile('package/package.json')
        if member is None:
            raise ValueError('the tarball holds no package/package.json')
        return json.load(io.TextIOWrapper(member, encoding='utf-8'))


def read(tarball):
    with open(tarball, 'rb') as stream:
        return stream.read()


def parse_arguments():
    parser = argparse.ArgumentParser(description='Serve npm tarballs as a local registry.')
    where = parser.add_mutually_exclusive_group(required=True)
    where.add_argument('--port', type=int)
    where.add_argument('--port-file')
    parser.add_argument('--fresh', nargs='+', default=[], help='tarballs dated now')
    parser.add_argument('--old', nargs='*', default=[], help='tarballs dated years back')
    parser.add_argument('--upstream', action='store_true', help='answer every other request from npmjs')
    parser.add_argument('--npmrc', help='the .npmrc to write the scope line into once the port is bound')
    parser.add_argument('--scope', help='the scope the .npmrc routes here, such as @airness')
    return parser.parse_args()


def main():
    arguments = parse_arguments()
    now = datetime.now(timezone.utc).isoformat(timespec='milliseconds').replace('+00:00', 'Z')
    tarballs = [(path, now) for path in arguments.fresh] + [(path, OLD_RELEASE) for path in arguments.old]

    # Every version given for a name is served; the first one given is the latest.
    def packages():
        served = {}
        for path, published in tarballs:
            if not os.path.isfile(path):
                continue
            content = read(path)
            manifest = manifest_of(content)
            versions = served.setdefault(manifest['name'], {})
            versions.setdefault(manifest['version'], {'manifest': manifest, 'content': content, 'published': published})
        return served

    def tarball_path(name, version):
        return f'/{name}/-/{name.split("/")[-1]}-{version}.tgz'

    class Registry(BaseHTTPRequestHandler):
        def do_GET(self):
            served = packages()
            requested = self.path.replace('%2f', '/').replace('%2F', '/')
            for name, versions in served.items():
                if requested == f'/{name}':
                    self.document(name, versions)
                    return
                for version, package in versions.items():
                    if requested == tarball_path(name, version):
                        self.reply(200, 'application/octet-stream', package['content'])
                        return
            if arguments.upstream:
                self.proxy()
                return
            self.reply(404, 'application/json', b'{"error":"not found"}')

        def proxy(self):
            headers = {'Accept': self.headers.get('Accept', 'application/json'), 'Accept-Encoding': 'identity'}
            request = urllib.request.Request(UPSTREAM + self.path, headers=headers)
            try:
                with urllib.request.urlopen(request, timeout=UPSTREAM_TIMEOUT_SECONDS) as answer:
                    content_type = answer.headers.get('Content-Type', 'application/octet-stream')
                    if 'json' in content_type:
                        local = f'http://{self.headers.get("Host")}'
                        body = answer.read().replace(UPSTREAM.encode(), local.encode())
                        self.reply(answer.status, content_type, body)
                        return
                    self.send_response(answer.status)
                    self.send_header('Content-Type', content_type)
                    length = answer.headers.get('Content-Length')
                    if length is not None:
                        self.send_header('Content-Length', length)
                    self.end_headers()
                    while chunk := answer.read(CHUNK):
                        self.wfile.write(chunk)
            except urllib.error.HTTPError as error:
                self.reply(error.code, 'application/json', error.read())
            except (urllib.error.URLError, TimeoutError) as error:
                self.reply(502, 'application/json', json.dumps({'error': str(error)}).encode())

        def document(self, name, versions):
            latest = next(iter(versions))
            published = versions[latest]['published']
            document = {
                'name': name,
                'dist-tags': {'latest': latest},
                'time': {
                    **{version: package['published'] for version, package in versions.items()},
                    'created': published,
                    'modified': published,
                },
                'versions': {
                    version: {
                        **package['manifest'],
                        'dist': {
                            'tarball': f'http://{self.headers.get("Host")}{tarball_path(name, version)}',
                            'shasum': hashlib.sha1(package['content']).hexdigest(),
                            'integrity': 'sha512-'
                            + base64.b64encode(hashlib.sha512(package['content']).digest()).decode(),
                        },
                    }
                    for version, package in versions.items()
                },
            }
            self.reply(200, 'application/json', json.dumps(document).encode())

        def reply(self, status, content_type, body):
            self.send_response(status)
            self.send_header('Content-Type', content_type)
            self.send_header('Content-Length', str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def log_message(self, *arguments):
            pass

    try:
        server = ThreadingHTTPServer(('127.0.0.1', arguments.port or 0), Registry)
    except OSError as error:
        sys.exit(f'the local npm registry could not bind port {arguments.port}: {error}')
    parent = os.getppid()

    def stop_with_parent():
        while os.getppid() == parent:
            time.sleep(PARENT_POLL_SECONDS)
        server.shutdown()

    threading.Thread(target=stop_with_parent, daemon=True).start()
    if arguments.npmrc:
        with open(arguments.npmrc, 'w', encoding='utf-8') as stream:
            key = f'{arguments.scope}:registry' if arguments.scope else 'registry'
            stream.write(f'{key}=http://127.0.0.1:{server.server_address[1]}/\n')
    if arguments.port_file:
        with open(arguments.port_file + '.partial', 'w', encoding='utf-8') as stream:
            stream.write(str(server.server_address[1]))
        os.replace(arguments.port_file + '.partial', arguments.port_file)
    server.serve_forever()


if __name__ == '__main__':
    main()
