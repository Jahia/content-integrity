#!/bin/bash
# Generates the demo of the module: the site integrityDemo with the faulty content of every check fixture, three sites for
# the home page and site group scenarios which exclude each other, the thresholds which make the threshold checks report,
# then four scans, which store their results with their CSV and XLSX reports.
#
# Run it once, on a server without the demo: several fixtures fail when they are applied again to an existing site. To
# generate the demo again, run regenerate.sh. Needs the module deployed and `yarn install` run in tests/.
#
# JAHIA_URL (default http://localhost:8080) and SUPER_USER_PASSWORD (default root) are the ones of the Cypress tests.
# EXCLUDED_PATHS, a comma-separated list of paths, are left out of the scans, for example the sites of a server which holds
# other content than the demo. VERSION_STORAGE_ROOT (default /jcr:system/jcr:versionStorage) is the root of the scan of the
# version histories: on such a server, /jcr:system/jcr:versionStorage/0c/1e/57 holds the histories of the fixtures only.
TESTS=$(cd "$(dirname "$0")/.." && pwd)
BASE=${JAHIA_URL:-http://localhost:8080}
AUTH=root:${SUPER_USER_PASSWORD:-root}
SITE=integrityDemo
# The fixtures, with their placeholders replaced, are uploaded from a temporary folder
OUT=$(mktemp -d)
trap 'rm -rf "$OUT"' EXIT

P() { r=$(cd "$OUT" && curl -s -u "$AUTH" -H "Origin: $BASE" "$BASE/modules/api/provisioning" -F "script=[{\"executeScript\":\"$1\"}];type=application/json" -F "file=@$1"); echo "$r $1"; }
G() { curl -s -u "$AUTH" -H "Origin: $BASE" -H 'Content-Type: application/json' "$BASE/modules/graphql" -d "$1"; }

if ! G '{"query":"{ contentIntegrity { integrityChecks { id } } }"}' | grep -q '"integrityChecks"'; then
  echo "No content-integrity module answers on $BASE: it is not deployed, or the root password is wrong" >&2; exit 1
fi
if G "{\"query\":\"{ jcr { nodeByPath(path: \\\"/sites/$SITE\\\") { uuid } } }\"}" | grep -q '"uuid"'; then
  echo "The demo already exists on $BASE: run regenerate.sh to generate it again" >&2; exit 1
fi

CREATE=$(find "$TESTS/node_modules/@jahia/cypress" -name createSite.groovy | head -1)
[ -n "$CREATE" ] || { echo "Run yarn install in $TESTS first" >&2; exit 1; }
site() { # $1 site key, $2 languages
  sed -e "s/SITEKEY/$1/g;s/SERVERNAME/localhost/g;s/TEMPLATES_SET/templates-system/g;s/LOCALE/en/g;s/LANGUAGES/$2/g" "$CREATE" > "$OUT/create-$1.groovy"; P "create-$1.groovy"
}
fixture() { # $1 fixture path under groovy/, $2 output name, $3 sed expression
  sed -e "$3" "$TESTS/cypress/fixtures/groovy/$1" > "$OUT/$2"; P "$2"
}

site $SITE en,fr
for f in "$TESTS"/cypress/fixtures/groovy/checks/*.groovy; do
  case $f in *-cleanup.groovy) continue;; esac
  n=$(basename "$f")
  case $n in
    HomePageDeclarationCheck.groovy) fixture "checks/$n" "$n" "s/SITEKEY/$SITE/g;s/\"SCENARIO\"/\"MULTIPLE_HOMES\"/g";;
    SiteLevelSystemGroupsCheck.groovy) fixture "checks/$n" "$n" "s/SITEKEY/$SITE/g;s/\"SCENARIO\"/\"MISSING_MEMBERSHIP\"/g";;
    *) fixture "checks/$n" "$n" "s/SITEKEY/$SITE/g";;
  esac
done
fixture nodeErrors/frameWithoutSize.groovy frameWithoutSize.groovy "s/SITEKEY/$SITE/g"
fixture virtualNodes/vfsMount.groovy vfsMount.groovy "s/MOUNT_NAME/$SITE/g"

# The scenarios of the home page and of the site groups which can't share a site with the ones of integrityDemo
extra() { # $1 site key, $2 home page scenario, $3 site group scenario (optional)
  site "$1" en
  fixture checks/HomePageDeclarationCheck.groovy "home-$1.groovy" "s/SITEKEY/$1/g;s/\"SCENARIO\"/\"$2\"/g"
  if [ -n "$3" ]; then fixture checks/SiteLevelSystemGroupsCheck.groovy "groups-$1.groovy" "s/SITEKEY/$1/g;s/\"SCENARIO\"/\"$3\"/g"; fi
}
extra integrityDemoNoHome NO_HOME GROUP_DOES_NOT_EXIST
extra integrityDemoFallback FALLBACK_ON_NAME
extra integrityDemoWrongType FALLBACK_ON_NAME_WRONG_TYPE

# The configuration of the checks is kept in memory: a redeploy of the module, or a restart, resets it
conf() { G "{\"query\":\"mutation { contentIntegrity { integrityCheckById(id: \\\"$1\\\") { configure(name: \\\"$2\\\", value: \\\"$3\\\") } } }\"}" | grep -q '"configure":true' && echo "configured $1 $2=$3"; }
conf FlatStorageCheck threshold 3
conf VersionHistoryCheck threshold 2
conf AceSanityCheck ace-count-threshold 5
conf PublicationSanityLiveCheck deep-compare-published-nodes true
conf WorkspaceSpecificDefinitionsCheck ws-live-specific-props j:liveProperties
conf ReferencesSanityCheck validate-back-refs true
conf UndeclaredNodeTypesCheck types-to-fix jnt:ciTestUndeclared,jmix:ciTestUndeclaredMixin,jnt:ciTestGhost,jnt:ciTestOrphan,jnt:ciTestDeclaredAgain

# Every check runs, the ones disabled by default included
checks=$(G '{"query":"{ contentIntegrity { integrityChecks { id } } }"}' | python3 -c 'import json,sys;print(json.dumps([c["id"] for c in json.load(sys.stdin)["data"]["contentIntegrity"]["integrityChecks"]]))')
scan() { # $1 root node, $2 workspace
  q=$(python3 -c "import json,os,sys;print(json.dumps({'query':'mutation(\$c:[String], \$s:String, \$x:[String]){ contentIntegrity { integrityScan { scan(workspace: %s, startNode: \$s, excludedPaths: \$x, checksToRun: \$c) } } }' % sys.argv[2],'variables':{'c':$checks,'s':sys.argv[1],'x':['/sites/systemsite/files/content-integrity-reports'] + [p for p in os.environ.get('EXCLUDED_PATHS', '').split(',') if p]}}))" "$1" "$2")
  id=$(G "$q" | python3 -c 'import json,sys;print(json.load(sys.stdin)["data"]["contentIntegrity"]["integrityScan"]["scan"])')
  for _ in $(seq 1 1800); do s=$(G "{\"query\":\"{ contentIntegrity { integrityScan(id: \\\"$id\\\") { status resultsID } } }\"}"); echo "$s" | grep -q '"running"' || break; sleep 2; done
  echo "scan $1 $2 -> $s"
}
# The version histories: TOO_MANY_VERSIONS, ORPHANED_HISTORY and the other errors of the version storage
scan "${VERSION_STORAGE_ROOT:-/jcr:system/jcr:versionStorage}" EDIT
# A node moved in live only: its errors in the default workspace
scan /sites/$SITE/contents/publication-default/moved-in-live EDIT
# The whole repository: the errors outside the sites, such as the users, the groups and the modules
scan / BOTH
# The demo site
scan /sites/$SITE BOTH
