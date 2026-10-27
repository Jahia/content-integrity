# content-integrity

Jahia module which scans the JCR for integrity errors. The checks are in
`src/main/java/org/jahia/modules/contentintegrity/services/checks`, the documentation of their errors in
`docs/embedded-tests.md`, with a screenshot of each check and of each error type in `docs/img/checks`.

## Changing a check: what goes with it

When an error type is added, renamed or removed, or when the way an error is displayed changes (message, extra infos,
fix), update in the same change:

1. **Fixture** `tests/cypress/fixtures/groovy/checks/<Check>.groovy`: the content which reproduces the error. The demo
   is built from these fixtures, so an error without a fixture gets no screenshot.
2. **Spec** `tests/cypress/e2e/checks/<Check>.cy.ts`: the error is detected, a valid case is not reported, and the fix
   (or `expectNoFix`).
3. **Documentation** `docs/embedded-tests.md`, in the section of the check: a `#### <title>` with
   `` `Error code: <TYPE>` ``, the screenshot line `![<TYPE> in the results of a scan](img/checks/<Check>/<TYPE>.png)`,
   the description (with the extra infos), what to do, and `**Fix**:`.
4. **Fix description** `src/main/resources/javascript/locales/en.json` and `fr.json`, only for a fixable error.
5. **Release notes** `docs/release-notes.md`: an item in the row of the current version.
6. **Coverage count** in `tests/README.md` ("They cover N of the M error types").
7. **Screenshots**: regenerate them, see below. Add the screenshot line of step 3 only once the image exists.

## Regenerating the screenshots

`docs/img/checks/<Check>.png` and `docs/img/checks/<Check>/<TYPE>.png` are taken from stored scans by
`tests/demo/screenshots.sh` (Chrome needed, `pngquant` compresses the new images). `JAHIA_URL` and
`SUPER_USER_PASSWORD` come from the environment: set them in `.claude/settings.local.json` (`env`, git-ignored), never
on the command line (a hook blocks commands which contain a password).

**Only the changed error types** (the usual case, on any server, the current one included):

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

**All the screenshots**: `demo/regenerate.sh` (or `demo/generate.sh` on a server without the demo), then
`demo/screenshots.sh` without arguments. **Only on a disposable Jahia**: `regenerate.sh` deletes EVERY stored scan
report, the fixtures create users, sites, mount points and node types, and the screenshots are taken from all the
stored scans. A redeploy of the module resets the check thresholds: generate the demo again after a redeploy.

Check the diff of `docs/img/checks`: only the expected images are added or changed. Commit them with the change of the
check.

If the screenshots can't be regenerated in the session, say so in the summary, and list the error types whose image is
missing (an error type documented in `docs/embedded-tests.md` without its `![<TYPE> ...]` line).

## Reading property values in a check

Read the values from the real node (`node.getRealNode()`), not through the `JCRNodeWrapper`: the wrapper runs the
interceptors, which rewrite the stored values (the `URLInterceptor` replaces each `##ref:linkN##` of a text, by `#` when
it is broken, and logs a warning for each one). A check must see the stored values.
