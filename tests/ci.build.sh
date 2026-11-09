#!/bin/bash
# Builds the image of the tests container from the Dockerfile of this folder, as the CI does. The
# tag comes from TESTS_IMAGE (the CI names it module-tests); the module jar of artifacts/ and the
# manifest are picked up at runtime, not at build time.

set -e
cd "$(dirname "$0")"
docker build -t "${TESTS_IMAGE:-jahia/content-integrity:latest}" .
