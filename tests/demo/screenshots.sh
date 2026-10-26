#!/bin/bash
# Takes the screenshots of the documentation of the checks, docs/img/checks/<check>.png, from the scans of the demo: run
# generate.sh first. docs/img/checks/<check>.png displays the errors of a check, docs/img/checks/<check>/<error type>.png
# the errors of a type, each in the results of the scan of the demo which shows the most of them, with the details of
# the first error open.
#
# JAHIA_URL (default http://localhost:8080) and SUPER_USER_PASSWORD are the ones of the Cypress tests.
DEMO=$(cd "$(dirname "$0")" && pwd)
TESTS=$(cd "$DEMO/.." && pwd)
DOCS=$(cd "$TESTS/../docs" && pwd)/img/checks
SHOTS=$TESTS/results/screenshots/checkScreenshots.cy.ts

rm -rf "$SHOTS"
(cd "$TESTS" && npx cypress run --config-file cypress.screenshots.config.ts --browser chrome --headless) || exit 1
mkdir -p "$DOCS"
# The screenshots of the error types are in a folder per check
cp -R "$SHOTS"/. "$DOCS"/
# The screenshots are reduced to 256 colors, which they look the same with, at a quarter of their size
if command -v pngquant > /dev/null; then
  find "$DOCS" -name '*.png' -exec pngquant --quality=80-95 --speed 1 --strip --skip-if-larger --force --ext .png {} +
else
  echo "pngquant is not installed: the screenshots are not compressed (brew install pngquant)" >&2
fi
find "$DOCS" -name '*.png' | wc -l
