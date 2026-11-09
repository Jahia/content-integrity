#!/bin/bash
# The environment variables of the tests container and of the scripts of tests/, as the CI sets
# them: JAHIA_URL and SUPER_USER_PASSWORD point at the docker-compose stack, NEXUS_USERNAME and
# NEXUS_PASSWORD are replaced by the CI in the provisioning manifest. Locally, source this file to
# run the scripts by hand against the stack.

if [[ -f .env ]]; then
  source .env
  export $(cat .env | sed 's/=.*//g'| xargs)
else
  source .env.example
  export $(cat .env.example | sed 's/=.*//g'| xargs)
fi
