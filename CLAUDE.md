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
7. **Screenshots**: regenerate all of them, see below. Add the screenshot line of step 3 only once the image exists.

## Regenerating the screenshots

**Regenerate ALL the screenshots every time**: after any change of a check, and after any change of the administration
screen, since every screenshot displays it. Then build the PDF again (`yarn docs:pdf`, see below). Do it once the change
is built, deployed and its tests pass, and commit the images separately from the code.

How to do it is the skill `regenerate-screenshots` (`.claude/skills/regenerate-screenshots/SKILL.md`). Its safety rules
apply even without it: run `demo/regenerate.sh` only on a disposable Jahia (it deletes EVERY stored scan report), never
pass the password on the command line, and never keep an image which shows a site, a group, a user or a principal of a
server holding other content, such as a customer dataset.

If the screenshots can't be regenerated in the session, say so in the summary, and list the error types whose image is
missing (an error type documented in `docs/embedded-tests.md` without its `![<TYPE> ...]` line).

## PDF of the documentation of the checks

`yarn docs:pdf` (at the root) builds `docs/embedded-tests.pdf` from `docs/embedded-tests.md` and its screenshots, with
a headless Chrome (`CHROME_PATH` if not found in its default locations). The PDF is git-ignored: build it again after a
change of the documentation or of the screenshots, never commit it.

## Reading property values in a check

Read the values from the real node (`node.getRealNode()`), not through the `JCRNodeWrapper`: the wrapper runs the
interceptors, which rewrite the stored values (the `URLInterceptor` replaces each `##ref:linkN##` of a text, by `#` when
it is broken, and logs a warning for each one). A check must see the stored values.
