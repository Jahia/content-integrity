#!/bin/bash
# Removes the demo with cleanup.sh, then generates it again with generate.sh: the data the fixtures create outside the
# sites (users, mount points, templates of the module, node types), the four demo sites, the configuration of the checks,
# and EVERY stored scan report, the ones of other scans included.
#
# JAHIA_URL (default http://localhost:8080) and SUPER_USER_PASSWORD (default root) are the ones of the Cypress tests.
DEMO=$(cd "$(dirname "$0")" && pwd)

# The demo is generated again only once its sites are deleted
bash "$DEMO/cleanup.sh" --all-reports || exit 1
bash "$DEMO/generate.sh"
