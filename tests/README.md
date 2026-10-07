# Content Integrity - Cypress tests

End-to-end tests of the content-integrity module, run against a Jahia server where the module is deployed.

* `cypress/e2e/checks`: one spec per integrity check. Each spec creates the faulty content, scans it, asserts every error
  type of the check, then fixes the errors when the check provides a fix and scans again to verify it.
* `cypress/e2e/api`: the GraphQL API: the catalog and the configuration of the checks, the lifecycle of a scan, the fix of
  all the errors matching filters, and the access to the scan reports, which only the server administrators may download.
* `cypress/e2e/ui`: the administration page: a new scan, the default columns and filter of the results table, the fix of
  an error, the fix of all the filtered errors, the fix with a typed value.

## Run the tests

The tests need Jahia 8.2.3 or later with the module deployed, and a root password. They create their own sites, named
`ci<Something>`, and their own users, named `ci-<site>-*`, then delete them.

```bash
yarn install
JAHIA_URL=http://localhost:8080 SUPER_USER_PASSWORD=root yarn e2e:local
```

* `yarn e2e:checks` runs the specs of the checks only, `yarn e2e:ui` the specs of the administration page only.
* `yarn e2e:debug` opens the Cypress UI.
* `yarn lint` type-checks the specs.

The specs must run one at a time, never in parallel: a single scan can run at a time on a server.

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

✅ detected, 🔧 fixed and verified by a new scan, ➖ the check provides no fix, ❌ the fix of the check does not fix it,
⏸ not reproducible, so the test is skipped.

| Check                            | Error type                                                                                                                     | Detection | Fix |
|----------------------------------|--------------------------------------------------------------------------------------------------------------------------------|:---------:|:---:|
| AceSanityCheck                   | NO_PRINCIPAL, NO_ACE_TYPE_PROP, INVALID_PRINCIPAL, MISSING_EXTERNAL_ACE                                                        |    ✅     | 🔧  |
|                                  | INVALID_ACE_TYPE_PROP, NO_SOURCE_ACE_PROP, EMPTY_SOURCE_ACE_PROP, SOURCE_ACE_BROKEN_REF                                         |    ✅     | 🔧  |
|                                  | SOURCE_ACE_NOT_TYPE_GRANT, INVALID_EXTERNAL_ACE_PATH, ROLES_DIFFER_ON_SOURCE_ACE                                               |    ✅     | 🔧  |
|                                  | INVALID_NODENAME, NO_ROLES_PROP, ROLE_DOESNT_EXIST, MISSING_SITE_PRIVILEGED_GRP_MEMBER, ACE_NON_GRANT_WITH_EXTERNAL_ACE        |    ✅     | ❌  |
|                                  | INVALID_EXTERNAL_PERMISSIONS, DUPLICATED_REF_SRC_ACE, INVALID_ROLES_PROP, TOO_MANY_ACE                                         |    ✅     | ❌  |
| BinaryPropertiesSanityCheck      | INVALID_BINARY                                                                                                                 |    ✅     | 🔧  |
| ChildNodeDefinitionsSanityCheck  | NOT_ALLOWED_BY_PARENT_DEF                                                                                                      |    ✅     | 🔧  |
| FlatStorageCheck                 | TOO_MANY_CHILD_NODES                                                                                                           |    ✅     | ➖  |
| HomePageDeclarationCheck         | MULTIPLE_HOMES, FALLBACK_ON_NAME, NO_HOME                                                                                      |    ✅     | 🔧  |
|                                  | FALLBACK_ON_NAME_WRONG_TYPE                                                                                                    |    ✅     | ❌  |
| JCRLanguagePropertyCheck         | MISSING_JCR_LANGUAGE_PROP, INCONSISTENT_JCR_LANGUAGE_PROP                                                                      |    ✅     | 🔧  |
| LivePropertiesCheck              | LIVE_PROPERTIES, EMPTY_LIVE_PROPERTIES                                                                                         |    ✅     | 🔧  |
| LockSanityCheck                  | INCONSISTENT_LOCK, DELETION_LOCK_ON_I18N                                                                                       |    ✅     | 🔧  |
| MarkForDeletionCheck             | NO_ROOT_DELETION, DELETION_MARK_IN_LIVE, DELETION_MARK_UNDER_USERS                                                             |    ✅     | 🔧  |
| NodeNameInfoSanityCheck          | INVALID_FULLPATH, MISSING_NODENAME, INVALID_NODENAME                                                                           |    ✅     | 🔧  |
| PagesSanityCheck                 | MISSING_TEMPLATE                                                                                                               |    ✅     | ➖  |
| PropertyDefinitionsSanityCheck   | EMPTY_MANDATORY_PROPERTY (with a typed value), UNDECLARED_PROPERTY                                                             |    ✅     | 🔧  |
|                                  | INVALID_VALUE_TYPE, INVALID_MULTI_VALUE_STATUS, INVALID_VALUE_CONSTRAINT, INVALID_NODE_VALIDATION                              |    ✅     | ❌  |
| PublicationSanityDefaultCheck    | NO_LIVE_NODE                                                                                                                   |    ✅     | 🔧  |
|                                  | DIFFERENT_PATH, DIFFERENT_PATH_POTENTIAL_FP, PATH_CONFLICT, DIFFERENT_PT                                                       |    ✅     | ❌  |
| PublicationSanityLiveCheck       | NO_DEFAULT_NODE, UNEXPECTED_UGC, INCONSISTENT_UGC                                                                              |    ✅     | 🔧  |
|                                  | MISSING_PROP_LIVE, MISSING_PROP_DEFAULT, DIFFERENT_PROP_VAL, DIFFERENT_MIXINS                                                  |    ✅     | ❌  |
| ReferencesSanityCheck            | BROKEN_REF                                                                                                                     |    ✅     | 🔧  |
|                                  | BROKEN_REF_TO_VN                                                                                                               |    ✅     | ❌  |
|                                  | INVALID_BACK_REF                                                                                                               |    ⏸     |     |
| SiteLevelSystemGroupsCheck       | GROUP_DOES_NOT_EXIST, MISSING_MEMBERSHIP                                                                                       |    ✅     | ➖  |
| StaticInternalLinksCheck         | HARDCODED_DOMAIN                                                                                                               |    ✅     | ➖  |
| TemplatesIndexationCheck         | NOT_INDEXED_TEMPLATE                                                                                                           |    ✅     | ➖  |
| UndeclaredNodeTypesCheck         | UNDECLARED_NODE_TYPE (primary type and mixin), GHOST_NODE_TYPE                                                                 |    ✅     | 🔧  |
| UndeployedModulesReferencesCheck | UNDEPLOYED_MODULE_ON_SITE                                                                                                      |    ✅     | 🔧  |
| UnreadablePublicationStatusCheck | UNREADABLE_PUBLICATION_STATUS                                                                                                  |    ✅     | ➖  |
| UserAccountSanityCheck           | NOT_OWNER                                                                                                                      |    ✅     | ➖  |
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
