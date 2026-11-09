#!/bin/bash
# This script can be used to warmup the environment and execute the tests
# It is used by the docker image at startup

BASEDIR=$(dirname $(readlink -f $0))

bash $BASEDIR/env.provision.sh

source $BASEDIR/set-env.sh

MODE=${MODE:-ci}

echo "$(date +'%d %B %Y - %k:%M') == env.run.sh == Printing the most important environment variables"
echo "$(date +'%d %B %Y - %k:%M') == NEXUS_USERNAME: ${NEXUS_USERNAME:0:3}***${NEXUS_USERNAME:(-6)}"
echo "$(date +'%d %B %Y - %k:%M') == DEBUG: ${DEBUG}"
echo "$(date +'%d %B %Y - %k:%M') == CYPRESS MODE: ${MODE}"

if [[ "${DEBUG}" == "true" ]]; then
  touch /tmp/debug
fi

echo "$(date +'%d %B %Y - %k:%M') == Fetching the list of installed modules =="
bash -c "unset npm_config_package; npx --yes @jahia/jahia-reporter@latest utils:modules \
  --moduleId=\"${MODULE_ID}\" \
  --jahiaUrl=\"${JAHIA_URL}\" \
  --jahiaPassword=\"${SUPER_USER_PASSWORD}\" \
  --filepath=\"results/installed-jahia-modules.json\""
echo "$(date +'%d %B %Y - %k:%M') == Modules fetched =="
INSTALLED_MODULE_VERSION=$(cat results/installed-jahia-modules.json | jq '.module.version')
if [[ $INSTALLED_MODULE_VERSION == "UNKNOWN" ]]; then
  echo "$(date +'%d %B %Y - %k:%M') ERROR: Unable to detect module: ${MODULE_ID} on the remote system "
  echo "$(date +'%d %B %Y - %k:%M') ERROR: The Script will exit"
  echo "$(date +'%d %B %Y - %k:%M') ERROR: Tests will NOT run"
  echo "failure" > ./results/test_failure
  exit 1
fi

echo "$(date +'%d %B %Y - %k:%M') == Run tests =="
mkdir -p ./results/reports
rm -rf ./results/reports

if [[ -z "${CYPRESS_CONFIGURATION_FILE}" ]]; then
  CYPRESS_CONFIGURATION_FILE=cypress.config.ts
fi

if [[ "${TESTS_PROFILE}" != "" ]]; then
  CYPRESS_CONFIGURATION_FILE=${TESTS_PROFILE}
else
  CYPRESS_CONFIGURATION_FILE="cypress.config.ts"
fi

echo "$(date +'%d %B %Y - %k:%M') == Running Cypress with configuration file ${CYPRESS_CONFIGURATION_FILE} =="

NO_COLOR=1 yarn e2e:${MODE} --config-file "${CYPRESS_CONFIGURATION_FILE}"

if [[ $? -eq 0 ]]; then
  echo "$(date +'%d %B %Y - %k:%M') == Full execution successful =="
  echo "success" > ./results/test_success
  yarn report:merge; yarn report:html
else
  echo "$(date +'%d %B %Y - %k:%M') == One or more failed tests =="
  echo "failure" > ./results/test_failure
  yarn report:merge; yarn report:html
fi

WAIT_DURATION=0
while [[ -e /tmp/debug ]]; do
  echo "Debug file present - $(( ++ WAIT_DURATION ))s - waiting for file removal or expiration of GitHub Actions timeout..."
  sleep 1;
done

if [[ -e ./results/test_success ]]; then
  exit 0
else
  exit 1
fi
