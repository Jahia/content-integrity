# Content Integrity - Cypress tests

End-to-end tests of the content-integrity module, run against a Jahia server where the module is deployed.

* `cypress/e2e/checks`: one spec per integrity check. Each spec creates the faulty content, scans it, asserts every error
  type of the check, then fixes the errors when the check provides a fix and scans again to verify it.
* `cypress/e2e/api`: the GraphQL API: the catalog and the configuration of the checks, a scan and its parameters, the
  pages, filters and column values of its results, the execution of a scan (one at a time, and its stop), the fix of
  all the errors matching filters, the permission to fix the errors (in the API and in the administration screen), and
  the access to the scan reports, which only the server administrators may download, and the errors of the virtual nodes
  of a mount point, which are not fixed, and which a scan with `skipMountPoints` skips. `karafCommands.cy.ts` runs the
  Karaf shell commands `jcr:integrity-*`, with each of their options, through `shell/karafCommand.groovy`, which
  captures what they print.
* `cypress/e2e/ui`: the administration page: the new scan dialog (its defaults, the excluded paths, the parameters sent,
  the configuration and the documentation of the checks), the scan in progress and its stop, the results table (its
  columns, filters, refresh, reports and link to the JCR browser), the fix of an error, the fix of all the filtered
  errors, the fix with a typed value.

## Run the tests

The tests need Jahia 8.1.5 or later with the module deployed, and a root password. They create their own sites, named
`ci<Something>`, and their own users, named `ci-<site>-*`, then delete them.

* On Jahia 8.2 or later, deploy the module `legacy-default-components` (1.2.0 or later 1.x,
  [Jahia/legacy-default-components](https://github.com/Jahia/legacy-default-components)): the fixtures of
  `PropertyDefinitionsSanityCheck` and of the fixes of a node create `jnt:frame` nodes, a type which the module `default`
  no longer provides. Without it, these fixtures fail with `NoSuchNodeTypeException`, and so do 5 specs.
* The specs of the administration page expect its English labels: the UI language of root (`preferredLanguage` of
  `/users/root`) must be English, or they time out on the title of the page.

Yarn 4 is pinned by the `packageManager` field of `package.json`: run `corepack enable` once, with Node 18.12 or later,
before the first `yarn install`.

```bash
yarn install
JAHIA_URL=http://localhost:8080 SUPER_USER_PASSWORD=root yarn e2e:ci
```

Set both `JAHIA_URL` and `SUPER_USER_PASSWORD`: without any of them, `@jahia/cypress` uses `http://localhost:8080` and
the password `root1234`, and with only one of them, the other one is undefined. Every spec fails at its first request
when the module is not deployed on the server.

* `yarn e2e:checks` runs the specs of the checks only, `yarn e2e:ui` the specs of the administration page only.
* `yarn e2e:debug` opens the Cypress UI.
* `yarn lint` type-checks the specs.

The specs must run one at a time, never in parallel: a single scan can run at a time on a server.

The license of the server can limit the number of users logged in at once: the Jahia login page then answers
"Authentication impossible as max number of concurrent users limit is reached". A spec which logs in other users than
root logs them out after each test, so that their sessions do not hold the slots until they expire.

## Generate the demo

`demo/generate.sh` builds a demo of the module from the fixtures of the tests, to check the errors and their fixes by
hand: the site `integrityDemo` with the faulty content of every check, three sites `integrityDemo*` for the home page and
site group scenarios which exclude each other, the thresholds which make the threshold checks report, then four scans
(the version storage, a node moved in live, the whole repository, the demo site). They cover 85 of the 87 error types:
`HISTORY_WITHOUT_NODE_ID` and `INVALID_BACK_REF` can't be reproduced.

```bash
JAHIA_URL=http://localhost:8080 SUPER_USER_PASSWORD=root demo/generate.sh
```

* It runs once, on a server without the demo: several fixtures fail when they are applied again to an existing site.
* A fix changes the demo content, and the specs of the tests remove some fixture data shared with it, such as the node
  types of `PropertyDefinitionsSanityCheck`. `demo/regenerate.sh` deletes the demo, its data outside the sites and every
  stored scan report, then generates it again.
* A redeploy of the module resets the thresholds, and removes the template of `TemplatesIndexationCheck`: generate the
  demo again after a redeploy.

`demo/screenshots.sh` then takes the screenshots of the documentation of the checks, with the details of the first
displayed error open: `docs/img/checks/<check>.png`, the results of the demo scan with the most error types of the check,
filtered on it, and `docs/img/checks/<check>/<error type>.png`, the results of the demo scan with the most errors of the
type, filtered on the check and the type. When `pngquant` is installed, the screenshots are reduced to 256 colors, at a quarter of
their size.

```bash
demo/screenshots.sh
```

## How a long scan is simulated

The tests of the execution of a scan need a scan which lasts long enough to be stopped, or to be running while another one
is started. `scan/slowCheck.groovy` registers `CiSlowCheck`, a check which finds no error but waits on every node it scans:
a scan of `/sites/systemsite` with it lasts about 15 seconds. The module does not export its classes to the scripts, so
the check is a proxy of the `ContentIntegrityCheck` interface, loaded from the class loader of the service.
`scan/slowCheck-cleanup.groovy` unregisters it. A redeploy of the module unregisters it too.

## How the faulty content is created

Each spec runs the Groovy script of its check, from `cypress/fixtures/groovy/checks`, through the provisioning API, with the
key of its site as `SITEKEY`. Some scripts also take a `SCENARIO`. A script named `<Check>-cleanup.groovy` removes what
deleting the site does not remove.

The Jahia API refuses to create inconsistent content, so the scripts write below it:

* **With the listeners disabled**, on the Jackrabbit node (`getRealNode()`): no rule, no ACL propagation and no
  auto-publication reacts to the change, and the Jahia validation of the mandatory properties is skipped.
* **By changing a definition**: a test CND is registered at runtime with `NodeTypeRegistry.addDefinitionsFile()` and
  `JCRStoreService.deployDefinitions()`, the content is created, then the definition is replaced or unregistered. This
  makes the undeclared types, the undeclared properties, the values of another type and the values out of a choice list.
  Jackrabbit refuses to write them directly.
* **In the workspace item states**, for a value that Jackrabbit refuses even on save (`WipSanityCheck`).
* **In the search index**, for a template removed from the index only (`TemplatesIndexationCheck`).

## Coverage

✅ detected, 🔧 fixed and verified by a new scan, ➖ the check provides no fix,
⏸ not reproducible, so the test is skipped.

| Check                            | Error type                                                                                                                     | Detection | Fix |
|----------------------------------|--------------------------------------------------------------------------------------------------------------------------------|:---------:|:---:|
| AceSanityCheck                   | NO_PRINCIPAL, NO_ACE_TYPE_PROP, INVALID_PRINCIPAL, MISSING_EXTERNAL_ACE                                                        |    ✅     | 🔧  |
|                                  | INVALID_ACE_TYPE_PROP, NO_SOURCE_ACE_PROP, EMPTY_SOURCE_ACE_PROP, SOURCE_ACE_BROKEN_REF                                         |    ✅     | 🔧  |
|                                  | SOURCE_ACE_NOT_TYPE_GRANT, INVALID_EXTERNAL_ACE_PATH, ROLES_DIFFER_ON_SOURCE_ACE                                               |    ✅     | 🔧  |
|                                  | INVALID_NODENAME, ROLE_DOESNT_EXIST, INVALID_ROLES_PROP, NO_ROLES_PROP and NO_PRINCIPAL (with a chosen value)                  |    ✅     | 🔧  |
|                                  | MISSING_SITE_PRIVILEGED_GRP_MEMBER, ACE_NON_GRANT_WITH_EXTERNAL_ACE, INVALID_EXTERNAL_PERMISSIONS, DUPLICATED_REF_SRC_ACE      |    ✅     | ➖  |
|                                  | TOO_MANY_ACE                                                                                                                   |    ✅     | ➖  |
| BinaryPropertiesSanityCheck      | INVALID_BINARY                                                                                                                 |    ✅     | 🔧  |
| ChildNodeDefinitionsSanityCheck  | NOT_ALLOWED_BY_PARENT_DEF                                                                                                      |    ✅     | 🔧  |
| FlatStorageCheck                 | TOO_MANY_CHILD_NODES                                                                                                           |    ✅     | ➖  |
| HomePageDeclarationCheck         | MULTIPLE_HOMES, FALLBACK_ON_NAME, NO_HOME                                                                                      |    ✅     | 🔧  |
|                                  | FALLBACK_ON_NAME_WRONG_TYPE                                                                                                    |    ✅     | ➖  |
| JCRLanguagePropertyCheck         | MISSING_JCR_LANGUAGE_PROP, INCONSISTENT_JCR_LANGUAGE_PROP                                                                      |    ✅     | 🔧  |
| LivePropertiesCheck              | LIVE_PROPERTIES, EMPTY_LIVE_PROPERTIES                                                                                         |    ✅     | 🔧  |
| LockSanityCheck                  | INCONSISTENT_LOCK, DELETION_LOCK_ON_I18N, LOCK_OF_UNKNOWN_PROCESS, LOCK_PROPERTIES_IN_LIVE                                     |    ✅     | 🔧  |
| MarkForDeletionCheck             | NO_ROOT_DELETION, DELETION_MARK_IN_LIVE, DELETION_MARK_UNDER_USERS                                                             |    ✅     | 🔧  |
| NodeNameInfoSanityCheck          | INVALID_FULLPATH, MISSING_NODENAME, INVALID_NODENAME                                                                           |    ✅     | 🔧  |
| PagesSanityCheck                 | MISSING_TEMPLATE on a page and on the default template of a site (with a chosen template)                                      |    ✅     | 🔧  |
| PropertyDefinitionsSanityCheck   | EMPTY_MANDATORY_PROPERTY and INVALID_VALUE_CONSTRAINT (with a chosen or typed value), UNDECLARED_PROPERTY                      |    ✅     | 🔧  |
|                                  | INVALID_VALUE_TYPE, INVALID_MULTI_VALUE_STATUS, INVALID_NODE_VALIDATION                                                        |    ✅     | ➖  |
| PublicationSanityDefaultCheck    | NO_LIVE_NODE                                                                                                                   |    ✅     | 🔧  |
|                                  | DIFFERENT_PATH, DIFFERENT_PATH_POTENTIAL_FP, PATH_CONFLICT, DIFFERENT_PT                                                       |    ✅     | ➖  |
| PublicationSanityLiveCheck       | NO_DEFAULT_NODE, UNEXPECTED_UGC, INCONSISTENT_UGC                                                                              |    ✅     | 🔧  |
|                                  | MISSING_PROP_LIVE, MISSING_PROP_DEFAULT, DIFFERENT_PROP_VAL, DIFFERENT_MIXINS                                                  |    ✅     | ➖  |
| ReferencesSanityCheck            | BROKEN_REF                                                                                                                     |    ✅     | 🔧  |
|                                  | BROKEN_REF_TO_VN                                                                                                               |    ✅     | ➖  |
|                                  | INVALID_BACK_REF                                                                                                               |    ⏸     |     |
| SiteLevelSystemGroupsCheck       | GROUP_DOES_NOT_EXIST (group site-privileged), MISSING_MEMBERSHIP                                                               |    ✅     | 🔧  |
| StaticInternalLinksCheck         | HARDCODED_DOMAIN                                                                                                               |    ✅     | ➖  |
| TemplatesIndexationCheck         | NOT_INDEXED_TEMPLATE                                                                                                           |    ✅     | ➖  |
| UndeclaredNodeTypesCheck         | UNDECLARED_NODE_TYPE (primary type and mixin), GHOST_NODE_TYPE                                                                 |    ✅     | 🔧  |
| UndeployedModulesReferencesCheck | UNDEPLOYED_MODULE_ON_SITE                                                                                                      |    ✅     | 🔧  |
| UnreadablePublicationStatusCheck | UNREADABLE_PUBLICATION_STATUS                                                                                                  |    ✅     | ➖  |
| UserAccountSanityCheck           | NOT_OWNER                                                                                                                      |    ✅     | 🔧  |
| VersionHistoryCheck              | TOO_MANY_VERSIONS                                                                                                              |    ✅     | 🔧  |
| VersionSanityCheck               | ORPHANED_HISTORY, ORPHAN_IN_SUBTREE                                                                                            |    ✅     | ➖  |
|                                  | HISTORY_WITHOUT_NODE_ID                                                                                                        |    ⏸     |     |
| WipSanityCheck                   | WIP_ON_TRANSLATION_NODE, WIP_LEGACY_FORMAT, WIP_UNEXPECTED_LANG, WIP_MISSING_PROP, WIP_UNEXPECTED_PROP                         |    ✅     | 🔧  |
|                                  | WIP_INCONSISTENT_STATUS_PROP, WIP_IN_LIVE                                                                                      |    ✅     | 🔧  |
| WorkspaceSpecificDefinitionsCheck | UNEXPECTED_TYPE, UNEXPECTED_PROP, UNEXPECTED_PROP_VALUE                                                                       |    ✅     | 🔧  |

Not reproducible:

* `INVALID_BACK_REF`: the back references are read with the system session of the scan, which can always read the
  referencing node. Jackrabbit leaves out the references from a node which does not exist anymore.
* `HISTORY_WITHOUT_NODE_ID`: `jcr:versionableUuid` is a protected property, set by the version manager.
