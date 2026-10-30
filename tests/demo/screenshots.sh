#!/bin/bash
# Takes the screenshots of the documentation of the checks, docs/img/checks/<check>.png, from the scans of the demo: run
# generate.sh first. docs/img/checks/<check>.png displays the errors of a check, docs/img/checks/<check>/<error type>.png
# the errors of a type, each in the results of the scan of the demo which shows the most of them, with the details of
# the first error open. docs/img/ui-<name>.png, in the README, display the administration page (see uiScreenshots.cy.ts).
#
# JAHIA_URL (default http://localhost:8080) and SUPER_USER_PASSWORD are the ones of the Cypress tests. The arguments are
# passed to Cypress, for example to take only some screenshots, from some scans (see checkScreenshots.cy.ts):
#   demo/screenshots.sh --env RESULTS=<results id>,CHECKS=ReferencesSanityCheck,ERROR_TYPES=BROKEN_REF_IN_FIELD
# Cypress splits --env on the commas: several values of a variable are passed as JSON, for example
#   demo/screenshots.sh --env '{"RESULTS":"<results id>,<results id>"}'
DEMO=$(cd "$(dirname "$0")" && pwd)
TESTS=$(cd "$DEMO/.." && pwd)
DOCS=$(cd "$TESTS/../docs" && pwd)/img/checks
SHOTS=$TESTS/results/screenshots/checkScreenshots.cy.ts
# The screenshots of the administration page in the README, docs/img/ui-<name>.png (see uiScreenshots.cy.ts)
UI_SHOTS=$TESTS/results/screenshots/uiScreenshots.cy.ts

rm -rf "$SHOTS" "$UI_SHOTS"
(cd "$TESTS" && npx cypress run --config-file cypress.screenshots.config.ts --browser chrome --headless "$@") || exit 1
mkdir -p "$DOCS"
# The screenshots are reduced to 256 colors, which they look the same with, at a quarter of their size. Only the new
# ones: compressing again the screenshots already in the documentation would change them
if command -v pngquant > /dev/null; then
  find "$SHOTS" -name '*.png' -exec pngquant --quality=80-95 --speed 1 --strip --skip-if-larger --force --ext .png {} +
  if [ -d "$UI_SHOTS" ]; then pngquant --quality=80-95 --speed 1 --strip --skip-if-larger --force --ext .png "$UI_SHOTS"/*.png; fi
fi
# The screenshots of the error types are in a folder per check
cp -R "$SHOTS"/. "$DOCS"/
if [ -d "$UI_SHOTS" ]; then cp "$UI_SHOTS"/*.png "$DOCS"/..; fi
if ! command -v pngquant > /dev/null; then
  echo "pngquant is not installed: the screenshots are not compressed (brew install pngquant)" >&2
fi
find "$DOCS" -name '*.png' | wc -l
