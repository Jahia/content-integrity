#!/bin/bash
# This script can be used to warmup the environment and execute the tests
# It is used by the docker image at startup

echo "$(date +'%d %B %Y - %k:%M') == env.debug.sh == Run Cypress in interactive mode"

MODE=debug ./env.run.sh
