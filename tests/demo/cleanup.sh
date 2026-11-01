#!/bin/bash
# Removes the demo: the data the fixtures create outside the sites (users, mount points, templates of the module, node
# types), the four demo sites, and the configuration of every check, since the thresholds that generate.sh sets stay
# configured until a redeploy of the module or a restart, and make the tests of the default configuration fail.
#
# The stored scan reports are deleted only when asked: with --all-reports, EVERY stored report, the ones of other scans
# included (on a disposable Jahia which holds only the demo, as regenerate.sh does), or the reports of the results ids
# given as arguments, such as the scans of the demo on a server which holds other content:
#   demo/cleanup.sh
#   demo/cleanup.sh <results id> <results id> ...
#   demo/cleanup.sh --all-reports
#
# JAHIA_URL (default http://localhost:8080) and SUPER_USER_PASSWORD (default root) are the ones of the Cypress tests.
DEMO=$(cd "$(dirname "$0")" && pwd)
TESTS=$(cd "$DEMO/.." && pwd)
BASE=${JAHIA_URL:-http://localhost:8080}
AUTH=root:${SUPER_USER_PASSWORD:-root}
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT

P() { r=$(cd "$OUT" && curl -s -u "$AUTH" -H "Origin: $BASE" "$BASE/modules/api/provisioning" -F "script=[{\"executeScript\":\"$1\"}];type=application/json" -F "file=@$1"); echo "$r $1"; }

DELETE=$(find "$TESTS/node_modules/@jahia/cypress" -name deleteSite.groovy | head -1)
[ -n "$DELETE" ] || { echo "Run yarn install in $TESTS first" >&2; exit 1; }

for f in "$TESTS"/cypress/fixtures/groovy/checks/*-cleanup.groovy; do
  n=$(basename "$f"); sed -e "s/SITEKEY/integrityDemo/g" "$f" > "$OUT/$n"; P "$n"
done
sed -e "s/MOUNT_NAME/integrityDemo/g" "$TESTS/cypress/fixtures/groovy/virtualNodes/vfsMount-cleanup.groovy" > "$OUT/vfsMount-cleanup.groovy"; P vfsMount-cleanup.groovy

for site in integrityDemo integrityDemoNoHome integrityDemoFallback integrityDemoWrongType; do
  sed -e "s/SITEKEY/$site/g" "$DELETE" > "$OUT/delete-$site.groovy"; P "delete-$site.groovy"
done

BASE=$BASE AUTH=$AUTH python3 - "$@" <<'PY' || exit 1
import json, os, subprocess, sys

def graphql(query):
    out = subprocess.run(['curl', '-s', '-u', os.environ['AUTH'], '-H', 'Origin: ' + os.environ['BASE'], '-H', 'Content-Type: application/json',
                          os.environ['BASE'] + '/modules/graphql', '-d', json.dumps({'query': query})], capture_output=True, text=True).stdout
    return json.loads(out)

sites = graphql('{ jcr { nodesByQuery(query: "select * from [jnt:virtualsite] where localname() like \'integrityDemo%\'") { nodes { path } } } }')
sites = sites['data']['jcr']['nodesByQuery']['nodes']
if sites:
    print('Demo sites not deleted:', [s['path'] for s in sites], file=sys.stderr)
    sys.exit(1)

# resetAllConfigurations resets the check it is called on only: it is called on each configurable check, the other
# ones having no configuration
checks = graphql('{ contentIntegrity { integrityChecks { id configurable } } }')['data']['contentIntegrity']['integrityChecks']
checks = [c for c in checks if c['configurable']]
reset = graphql('mutation { contentIntegrity { ' + ' '.join('c%d: integrityCheckById(id: %s) { resetAllConfigurations }' % (i, json.dumps(c['id'])) for i, c in enumerate(checks)) + ' } }')
print(reset.get('errors') or 'configuration of the %d configurable checks reset' % len(checks))

args = sys.argv[1:]
if args == ['--all-reports']:
    reports = graphql('{ jcr { nodesByQuery(query: "select * from [integrity:scanReport]") { nodes { path } } } }')['data']['jcr']['nodesByQuery']['nodes']
    paths = [r['path'] for r in reports if r['path'].startswith('/sites/systemsite/files/content-integrity-reports/')]
    if paths:
        deletion = graphql('mutation { jcr { ' + ' '.join('d%d: deleteNode(pathOrId: %s)' % (i, json.dumps(p)) for i, p in enumerate(paths)) + ' } }')
        print(deletion.get('errors') or '%d scan reports deleted' % len(paths))
elif args:
    deletion = graphql('mutation { contentIntegrity { ' + ' '.join('d%d: deleteScanResults(id: %s)' % (i, json.dumps(a)) for i, a in enumerate(args)) + ' } }')
    print(deletion.get('errors') or '%d scan reports deleted' % len(args))
PY
