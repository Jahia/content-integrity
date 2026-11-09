#!/bin/bash
# This script can be used to warmup the environment and execute the tests
# It is used by the docker image at startup

BASEDIR=$(dirname $(readlink -f $0))

source $BASEDIR/set-env.sh

if [[ -z "$JAHIA_PROCESSING_URL" ]]; then
  echo "JAHIA_PROCESSING_URL not set, defaulting to $JAHIA_URL"
  export JAHIA_PROCESSING_URL="$JAHIA_URL"
else
  echo "JAHIA_PROCESSING_URL is already set ($JAHIA_PROCESSING_URL)"
fi

#!/usr/bin/env bash
START_TIME=$SECONDS

echo "$(date +'%d %B %Y - %k:%M') == env.provision.sh == Printing the most important environment variables"
echo " MANIFEST: ${MANIFEST}"
echo " NEXUS_USERNAME: ${NEXUS_USERNAME:0:3}***${NEXUS_USERNAME:(-6)}"
echo " TESTS_IMAGE: ${TESTS_IMAGE}"
echo " JAHIA_IMAGE: ${JAHIA_IMAGE}"
echo " JAHIA_CLUSTER_ENABLED: ${JAHIA_CLUSTER_ENABLED}"
echo " MODULE_ID: ${MODULE_ID}"
echo " JAHIA_URL: ${JAHIA_URL}"
echo " JAHIA_PROCESSING_URL: ${JAHIA_PROCESSING_URL}"
echo " JAHIA_HOST: ${JAHIA_HOST}"
echo " JAHIA_PORT: ${JAHIA_PORT}"
echo " JAHIA_USERNAME: ${JAHIA_USERNAME}"
echo " JAHIA_PASSWORD: ${JAHIA_PASSWORD}"
echo " JAHIA_USERNAME_TOOLS: ${JAHIA_USERNAME_TOOLS}"
echo " JAHIA_PASSWORD_TOOLS: ${JAHIA_PASSWORD_TOOLS}"
echo " SUPER_USER_PASSWORD: ${SUPER_USER_PASSWORD}"
echo " TIMEZONE: ${TIMEZONE}"
echo " MAILPIT_URL: ${MAILPIT_URL}"
echo " SMTP_SERVER_URL: ${SMTP_SERVER_URL}"

echo "$(date +'%d %B %Y - %k:%M') ==  Using Node version: $(node -v)"
echo "$(date +'%d %B %Y - %k:%M') ==  Using yarn version: $(yarn -v)"

echo "$(date +'%d %B %Y - %k:%M') ==  Waiting for Jahia to startup"

while [[ $(curl -s -o /dev/null -w ''%{http_code}'' ${JAHIA_PROCESSING_URL}/cms/login) -ne 200 ]];
do
  echo "$(date +'%d %B %Y - %k:%M') == Jahia is not available at ${JAHIA_PROCESSING_URL}/cms/login, will try in 5 seconds"
  ELAPSED_TIME=$(($SECONDS - $START_TIME))
  if [[ ELAPSED_TIME -gt 300 ]]; then
    echo "$(date +'%d %B %Y - %k:%M') == Exiting, Jahia failed to start after 300 seconds"
    exit 1
  fi
  sleep 5;
done

ELAPSED_TIME=$(($SECONDS - $START_TIME))
echo "$(date +'%d %B %Y - %k:%M') == Jahia became alive in ${ELAPSED_TIME} seconds"

mkdir -p ./run-artifacts
mkdir -p ./results

# Copy manifest file
# If the file doesn't exist, we assume it is a URL and we download it locally
if [[ -e ${MANIFEST} ]]; then
  cp ${MANIFEST} ./run-artifacts
else
  echo "$(date +'%d %B %Y - %k:%M') == Downloading: ${MANIFEST}"
  curl ${MANIFEST} --output ./run-artifacts/curl-manifest
  MANIFEST="curl-manifest"
fi
sed -i -e "s/NEXUS_USERNAME/$(echo ${NEXUS_USERNAME} | sed -e 's/\\/\\\\/g; s/\//\\\//g; s/&/\\\&/g')/g" ./run-artifacts/${MANIFEST}
sed -i -e "s/NEXUS_PASSWORD/$(echo ${NEXUS_PASSWORD} | sed -e 's/\\/\\\\/g; s/\//\\\//g; s/&/\\\&/g')/g" ./run-artifacts/${MANIFEST}

echo "$(date +'%d %B %Y - %k:%M') == Executing manifest: ${MANIFEST} =="
curl -u root:${SUPER_USER_PASSWORD} -X POST ${JAHIA_PROCESSING_URL}/modules/api/provisioning --form script="@./run-artifacts/${MANIFEST};type=text/yaml" $(find assets -type f | sed -E 's/^(.+)$/--form file=\"@\1\"/' | xargs)
echo
if [[ $? -eq 1 ]]; then
  echo "$(date +'%d %B %Y - %k:%M') == PROVISIONING FAILURE - EXITING SCRIPT, NOT RUNNING THE TESTS"
  echo "failure" > ./results/test_failure
  exit 1
fi

if [[ -d artifacts/ ]]; then
  cd artifacts/
  echo "$(date +'%d %B %Y - %k:%M') == Content of the artifacts/ folder"
  ls -lah
  echo "$(date +'%d %B %Y - %k:%M') [MODULE_INSTALL] == Will start submitting files"
  for file in $(ls -1 *-SNAPSHOT.jar | sort -n)
  do
    echo "$(date +'%d %B %Y - %k:%M') [MODULE_INSTALL] == Submitting module from: $file =="
    curl -u root:${SUPER_USER_PASSWORD} -X POST ${JAHIA_PROCESSING_URL}/modules/api/provisioning --form script='[{"installOrUpgradeBundle":"'"$file"'", "forceUpdate":true}]' --form file=@$file
    echo
    echo "$(date +'%d %B %Y - %k:%M') [MODULE_INSTALL] == Module submitted =="
  done

  # This is done after classic .jar module to ensure Javascript modules are installed after engine module
  for file in $(ls -1 *-SNAPSHOT.tgz | sort -n)
    do
      echo "$(date +'%d %B %Y - %k:%M') [MODULE_INSTALL] == Submitting Javascript module from: $file =="
      curl -u root:${SUPER_USER_PASSWORD} -X POST ${JAHIA_PROCESSING_URL}/modules/api/provisioning --form script='[{"installOrUpgradeBundle":"'"$file"'", "forceUpdate":true}]' --form file=@$file
      echo
      echo "$(date +'%d %B %Y - %k:%M') [MODULE_INSTALL] == Javascript Module submitted =="
    done
  cd ..
fi

# Submits provisioning scripts from a given directory
submit_provisioning_scripts() {
  local dir="$1"
  if [[ -d "$dir" ]]; then
    (
      cd "$dir"
      for file in $(ls -1 script-* 2>/dev/null | sort -n)
      do
        echo "$(date +'%d %B %Y - %k:%M') [SCRIPT] == Submitting script: $file (from $dir) =="
        curl -u root:${SUPER_USER_PASSWORD} -X POST ${JAHIA_PROCESSING_URL}/modules/api/provisioning --form script='[{"executeScript":"'"$file"'"}]' --form file=@$file
        echo "$(date +'%d %B %Y - %k:%M') [SCRIPT] == Script executed =="
      done
    )
  fi
}

# Bundled scripts, shipped with @jahia/cypress itself (e.g. script-setup-smtp.groovy), run
# first, so a consuming project's own scripts can rely on that baseline already being in place.
submit_provisioning_scripts "$BASEDIR/scripts"

# Then the consuming project's own scripts/ folder, relative to the current working directory
# — skipped if it resolves to the same directory as the bundled one above (e.g. when this
# runs from inside the @jahia/cypress repo's own checkout), so a script isn't submitted twice.
if [[ "$(cd "$BASEDIR/scripts" 2>/dev/null && pwd)" != "$(cd ./scripts 2>/dev/null && pwd)" ]]; then
  submit_provisioning_scripts ./scripts
fi
