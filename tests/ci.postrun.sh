#!/usr/bin/env bash
# Executed by the CI on the host after the screenshots job: copies the images the tests container
# produced into the sources of the documentation, ready to be committed. The container wrote them
# to docs/img (see ci.screenshots.sh); the CI copies that folder out first.

set -e
cd "$(dirname "$0")"

if [ ! -d results/docs-img/img ]; then
  echo "No screenshots were produced: results/docs-img/img does not exist" >&2
  exit 1
fi

rm -rf ../docs/img/checks
mkdir -p ../docs/img
cp -R results/docs-img/img/. ../docs/img/
echo "Screenshots copied to docs/img"
