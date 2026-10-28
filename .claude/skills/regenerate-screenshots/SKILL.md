---
name: regenerate-screenshots
description: Regenerate the screenshots of the documentation of the checks (docs/img/checks) from the demo of the module, on a disposable Jahia or on a server holding other content such as a customer dataset, without exposing its data. Use after any change of a check or of the administration screen.
---

# Regenerating the screenshots of the checks

`docs/img/checks/<Check>.png` and `docs/img/checks/<Check>/<TYPE>.png` are taken from stored scans by
`tests/demo/screenshots.sh` (Chrome needed, `pngquant` compresses the new images). `JAHIA_URL` and
`SUPER_USER_PASSWORD` come from the environment: set them in `.claude/settings.local.json` (`env`, git-ignored), never
on the command line (a hook blocks commands which contain a password).

## All the screenshots (the rule)

`demo/regenerate.sh` (or `demo/generate.sh` on a server without the demo), then `demo/screenshots.sh` without
arguments, on a disposable Jahia: `regenerate.sh` deletes EVERY stored scan report, the fixtures create users, sites,
mount points and node types, and the screenshots are taken from all the stored scans. A redeploy of the module, or a
restart, resets the check thresholds: generate the demo again after it.

## On a server holding other content, such as a copy of a customer dataset

- Run `demo/generate.sh`, never `regenerate.sh`, with `EXCLUDED_PATHS` set to the other sites (comma-separated) and
  `VERSION_STORAGE_ROOT=/jcr:system/jcr:versionStorage/0c/1e/57`, where the fixtures of `VersionSanityCheck` are: a scan
  of all the version histories of a real dataset holds hundreds of thousands of errors, and reading them can make the
  server run out of memory.
- Take the screenshots from the demo scans only: `demo/screenshots.sh --env '{"RESULTS":"<id>,<id>,..."}'` (a list is
  passed as JSON: Cypress splits `--env` on the commas).
- Before keeping an image, check that the errors of its check / type, in the scan it is taken from, are demo data
  (`integrityDemo*`, `ci-*`, `/modules`, the fixture histories) or generic Jahia nodes, and look at the images whose
  scan holds other errors: an image which shows a site, a group, a user or a principal of the server keeps its
  committed version.
- Clean up afterwards, unless asked to keep the demo to check it by hand: the `*-cleanup.groovy` fixtures with
  `SITEKEY=integrityDemo`, `vfsMount-cleanup.groovy`, the four `integrityDemo*` sites, and the demo scans with
  `deleteScanResults`.

## Only some error types (fallback, when all the screenshots can't be regenerated)

On any server, the current one included:

1. Build and deploy the module.
2. Create the demo site and apply only the fixture of the check: `createSite.groovy` of `@jahia/cypress` with
   `SITEKEY=integrityDemo`, languages `en,fr`, templates `templates-system`, then
   `tests/cypress/fixtures/groovy/checks/<Check>.groovy` with `SITEKEY=integrityDemo`, both through the provisioning
   API (`/modules/api/provisioning`, `executeScript`), as `tests/demo/generate.sh` does.
3. Scan `/sites/integrityDemo` with this check only (GraphQL `integrityScan.scan`, `checksToRun`), and note the
   results id.
4. Take only these screenshots, from this scan only, so that no other scan of the server (customer data) ends up in
   the documentation:
   ```bash
   cd tests && demo/screenshots.sh --env RESULTS=<results id>,CHECKS=<Check>,ERROR_TYPES=<TYPE>[,<TYPE>...]
   ```
5. Clean up what was created: `<Check>-cleanup.groovy` if it exists, the site `integrityDemo` (`deleteSite.groovy` of
   `@jahia/cypress`), and the scan report of step 3 (under `/sites/systemsite/files/content-integrity-reports`). Keep
   the other scan reports.

## Afterwards

Check the diff of `docs/img/checks`: only the expected images are added or changed. Then build the PDF again
(`yarn docs:pdf`), and commit the images separately from the code.
