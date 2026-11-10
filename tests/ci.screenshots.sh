#!/bin/bash
# Regenerates the screenshots of the documentation of the checks, docs/img/checks/<check>.png,
# <check>/<error type>.png and docs/img/ui-<name>.png, by hand when the documentation needs new
# images: rebuilds the demo (regenerate.sh, which deletes EVERY stored scan report: run it on a
# disposable Jahia which holds only the module under test), takes all the screenshots
# (screenshots.sh), then removes the demo again (cleanup.sh --all-reports), so the server is left
# as it was.
#
# Runs INSIDE the tests container (whose working directory is this tests folder), against the Jahia
# of the docker-compose stack: JAHIA_URL=http://jahia:8080 and SUPER_USER_PASSWORD come from the
# environment. screenshots.sh copies the images into ../docs/img: the folder is created here; copy
# it out of the container with docker cp (into results/docs-img), then run ci.postrun.sh on the
# host.

set -e
TESTS=$(cd "$(dirname "$0")" && pwd)
cd "$TESTS"

# The destination folder of screenshots.sh, which the CI copies out of the container
mkdir -p ../docs/img/checks

# The demo: the demo sites, the fixtures of every check, the thresholds, then the four scans whose
# results the screenshots are taken from
bash demo/regenerate.sh

# Takes all the screenshots from the scans of the demo, and compresses them with pngquant
bash demo/screenshots.sh

# Removes the demo sites, the fixture data and every stored scan report, and resets the
# configuration of every check: the server is left clean for the next run
bash demo/cleanup.sh --all-reports

echo "Screenshots regenerated in $TESTS/../docs/img"
