FROM cypress/browsers:node-22.21.0-chrome-141.0.7390.107-1-ff-144.0.3537.92-1

# pngquant compresses the screenshots of the documentation; jq and curl are used by the scripts of
# @jahia/cypress and of demo/; python3 is used by demo/generate.sh and demo/cleanup.sh
RUN apt-get update && apt-get install -y --no-install-recommends pngquant python3 jq curl \
    && rm -rf /var/lib/apt/lists/*

# Yarn 4 comes from the packageManager field of package.json; the shims must be installed by root,
# the installation then runs as the unprivileged user
RUN corepack enable

RUN adduser --disabled-password jahians

USER jahians
WORKDIR /home/jahians

COPY --chown=jahians:jahians package.json yarn.lock .yarnrc.yml /home/jahians/

RUN mkdir -p /home/jahians/run-artifacts /home/jahians/results /home/jahians/cypress/plugins

RUN CI=true yarn install

COPY --chown=jahians:jahians . /home/jahians

RUN CI=true /home/jahians/node_modules/.bin/cypress install

CMD ["/bin/bash", "-c", "/home/jahians/env.run.sh"]
