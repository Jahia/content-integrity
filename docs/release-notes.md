# <a name="summary"></a>Content Integrity - Release notes
* [How to use it](../README.md#summary)
* [Embedded tests](embedded-tests.md#summary)
* [How to extend it](how-to-extend.md#summary)
* [Groovy scripts](groovy-scripts.md#summary)
* [Release notes](#release-notes)
  * [Jahia 8](#jahia-8) ([latest](#latest))
  * [Jahia 7](#jahia-7) ([latest](#latest-v7))

## <a name="release-notes"></a>Release notes

### Jahia 8

#### <a name="latest"></a><a name="v4-0-0"></a>4.0.0

Required Jahia version: 8.1.5.0

* Jahia 8.1.5.0 or later is now required
* Build: the administration screen is built with Yarn 4, pinned by the `packageManager` field of `package.json` and run through corepack, instead of Yarn 1. The Maven build installs it with the `install-node-and-corepack` goal of `frontend-maven-plugin` 1.15.4. To build or run the tests outside of Maven, run `corepack enable` once, then `yarn install`
* UI
  * Rewrote the administration screen in React with the Jahia Moonstone design system. It is now a native administration route instead of an iframe
  * The scan tab and the results tab are replaced by a single page: the results of the stored scans are displayed, below the scan which runs if any, and a scan is configured in a dialog opened from the page header
  * The scan which runs is displayed with its start date and its logs, pushed by a GraphQL subscription, and its results are displayed when it is over. Each stored scan tells its status, finished, interrupted or failed, and keeps its log
  * The configuration dialog of a check validates the integer values and only saves the changed ones
  * Results: choose the displayed columns from a dropdown, the path, the workspace, the check name, the error type and the message by default, and display the full value of a truncated cell in its tooltip
  * Results: the filters are always displayed, and by default only the errors which make the XML import fail are displayed
  * Results: the Refresh button clears the results, without reloading the page: the list of the scans is read again, and no results are displayed until a scan is chosen
  * Results: **Delete**, next to the reports, deletes the results of the selected scan, with their CSV and XLSX reports, after a confirmation. It requires the new permission `adminContentIntegrityDelete`, granted to the role `server-administrator` only, and is offered by the GraphQL field `deleteScanResults`
  * Results: **Delete all**, in the menu after the title of the results, deletes the results of all the scans except the ones of a running scan, after a confirmation. It requires `adminContentIntegrityDelete` as well, and is offered by the GraphQL field `deleteAllScanResults`
  * Results: the page opens, and reopens once reloaded, without any results selected, as after **Refresh**, and displays the same message and **New scan** button whether no scan is stored or none is selected
  * Results: **Fix all** runs, after a confirmation, the fix of every error matching the filters. The errors whose check provides no fix, and the ones fixed with a typed value, are skipped. The errors not fixed are tagged in their row
  * Results: the errors are displayed as a tree, sorted by workspace then by path: a node with several errors has a row, which can be collapsed, above the rows of its errors, which repeat the columns of the node, such as its path and its workspace, and the only error of a node is displayed in a single row
  * Results: a click on a row displays the errors of its node in a side panel, whatever the filters, each with its fix and the fields of its values. The errors fixed with values are linked when the node has several of them: a node is saved only once all its mandatory properties are set, so their values are saved at once, with a single Fix button. **Fix all**, at the bottom of the panel, fixes the errors of the node which can be fixed. The outcome of a fix stays displayed in the last column of the row
  * Results: every fix, from **Fix all** or from the panel of a node, and the deletion of the results of all the scans, are run only once confirmed, after the box "Be careful, data are going to be altered in this environment" is ticked, unticked each time the confirmation opens
  * Results: the panel of a node describes, above the Fix button of each error, what its fix changes, for example the deletion of a node
  * Results: the panel tells when the node of an error has been removed since the scan, for example by the fix of another error, instead of flagging the error without fix. GraphQL: added the field `nodeExists` to the errors
  * Results: after **Fix all**, near the results or at the bottom of the panel of a node, a dialog tells how many errors are fixed, how many are skipped and why (already fixed, no fix, fixed with a typed value, virtual node, node removed since the scan), and how many are not fixed and why: the message of the failure, or else what the fix does
  * Results: a filter on the fix status, to display the errors fixed, the ones skipped, on which no fix has run, or the ones not fixed, whose fix has run without fixing them. The three statuses are always offered, with their number of errors. The failure of a fix is stored with the results, as their fixed status, and the row of the error tells it once the page is reloaded
  * The page fills the screen, with the margins of the administration: the results card reaches the bottom of the screen, with the pagination at its bottom
  * The icons of the screen are displayed in a single size
  * Added French labels
  * Removed the legacy jQuery screen, its JSP views and the `contentIntegrity.ui` configuration
* The command `jcr:integrity-fix` is now available, without the `modules.contentIntegrity.devMode` system property
* Checks
  * `AceSanityCheck`: the ACE related errors can be fixed with the `jcr:integrity-fix` command: deletion of the ACE without principal or without ACE type, deletion of the external ACE without valid source ACE or with an invalid ACE type, removal of the invalid values of `j:sourceAce`, recreation of the missing external ACE
  * Removed the hidden Groovy scripts dedicated to the cleanup of the ACE, replaced by the fixes of `AceSanityCheck`
  * The results of the scans are stored in the JCR, under `/sites/systemsite/files/content-integrity-reports`, instead of a cache lost at each restart: one report per scan, written when it starts, with its status (running, finished, interrupted or failed), its log and its errors. The reports older than 30 days are deleted by a background job, configured by `org.jahia.modules.contentintegrity.reports` (`retentionDays`, `cleanupIntervalHours`). The methods `storeErrorsInCache` and `removeErrorsFromCache` of `ContentIntegrityService` are removed
  * A fix reports an error already fixed as fixed, as the API declares it: a property or a mixin already removed, for example by the fix of another error or from other results, no longer ends as not fixed. It concerns `UNDECLARED_PROPERTY`, `UNEXPECTED_PROP`, `UNEXPECTED_TYPE`, the undeclared mixins of `UNDECLARED_NODE_TYPE`, `BROKEN_REF` and `NO_LIVE_NODE`. An error whose node does not exist anymore is not fixable
  * Only the errors which have a fix are offered one: a check tells which of its errors it fixes with the new method `SupportsIntegrityErrorFix.isFixable(error)`, true by default. Not fixable anymore: `DIFFERENT_PATH`, `DIFFERENT_PATH_POTENTIAL_FP`, `PATH_CONFLICT` and `DIFFERENT_PT` (`PublicationSanityDefaultCheck`), the differences of properties and mixins of `PublicationSanityLiveCheck`, `BROKEN_REF_TO_VN` and `INVALID_BACK_REF`, `FALLBACK_ON_NAME_WRONG_TYPE`, `INVALID_VALUE_TYPE`, `INVALID_MULTI_VALUE_STATUS` and `INVALID_NODE_VALIDATION`, and the ACL errors which need an analysis. The method `isFixWithValues` of `SupportsIntegrityErrorFixWithValues` is removed: an error is fixed with values when `getFixValuesDefinition` returns their description
  * The missing `j:roles` of an access control entry, reported by `PropertyDefinitionsSanityCheck` and by `NO_ROLES_PROP` of `AceSanityCheck`, is filled with roles chosen among the roles which can be given at its place, and the details tell to whom they are granted. Without a value, the right roles can't be guessed and the entry is not fixed
  * An access control entry without `j:principal`, reported by `PropertyDefinitionsSanityCheck` and by `NO_PRINCIPAL` of `AceSanityCheck`, is fixed with a user or a group chosen among the ones it can name, in a dropdown grouping the users and the groups, the one its name tells being suggested. From the administration screen, the entry is no longer deleted: the deletion remains the fix without value, as from `jcr:integrity-fix`
  * `PropertyDefinitionsSanityCheck`: `INVALID_VALUE_CONSTRAINT` is fixed in the administration screen with a value chosen among the values accepted by the definition, or typed when its constraints are not a list. On a multiple property, the other values are kept. Fix all skips it
  * `PagesSanityCheck`: `MISSING_TEMPLATE` is fixed in the administration screen with a template chosen in a dropdown, which lists the templates available to the page, as in the template selector of the editors
  * A check which fixes errors with values can fix several errors of a node together, with the new method `SupportsIntegrityErrorFixWithValues.fixErrors(node, errors, values)`, which fixes them one by one by default. `PropertyDefinitionsSanityCheck` and `AceSanityCheck` write all the values, then save the node once
  * New fixes
    * `SiteLevelSystemGroupsCheck`: `GROUP_DOES_NOT_EXIST` creates the missing group again, as Jahia creates it with a site: the group `site-privileged` is a member of `privileged`, is granted the role `privileged` on the site and on its folders `users` and `groups`, and its members are the principals granted a role with a privileged access in the site. The server group `privileged` is created again with the groups `site-privileged` of the sites. `MISSING_MEMBERSHIP` adds the group `site-privileged` to `privileged`
    * `UserAccountSanityCheck`: `NOT_OWNER` grants the role `owner` to the user on its account node
    * `AceSanityCheck`: `INVALID_NODENAME` renames the ACE after its type and its principal, unless an ACE already has this name, or the external ACE has an invalid role or external permissions. `ROLE_DOESNT_EXIST` removes the roles which don't exist, and deletes the ACE left without role, or the external ACE. `INVALID_ROLES_PROP` deletes the external ACE, then the ACL listener creates the right ones from its source ACE.
  * `LockSanityCheck` runs in the `live` workspace as well, and detects two new errors, both fixed by the check. `LOCK_OF_UNKNOWN_PROCESS`: a node locked by a workflow whose process does not exist anymore, for example removed without ending, which leaves the node locked; the fix releases the lock of the process. `LOCK_PROPERTIES_IN_LIVE`: the lock properties `j:lockTypes` and `j:locktoken` of a node of the `live` workspace which is not locked, copied by the first publication of a node that a workflow locks; the fix removes them
  * `ReferencesSanityCheck`: detects `BROKEN_REF_IN_FIELD`, a link of a text whose `##ref:linkN##` placeholder has no referenced node, as left by the deletion of the linked node. Such a link is rendered as `#`. The fix removes the link from the text, keeping its text, as when it is removed in CKEditor, and deletes its `jnt:referenceInField` node
  * [bugfix] `ReferencesSanityCheck`, `StaticInternalLinksCheck`: the values are read without the interceptors, which logged a warning `Cannot find referenced item` for each link of a text without a referenced node
  * [bugfix] `jcr:integrity-check`: the checks prefixed with `:` in `-c` are skipped, they were run anyway
  * [bugfix] `jcr:integrity-printError` and `jcr:integrity-printTestResults`: the descriptions of their arguments, an error to print and the ID of a scan, were wrong. The commands which load the results of a scan print the same message when they can't be found, and the completion of an option value no longer fails when the option is the last typed word
  * [bugfix] `PagesSanityCheck`: the default template of a site, `j:defaultTemplateName`, is checked against the page templates available to the site; it was never checked
  * [bugfix] `UndeployedModulesReferencesCheck`: the fix of a module installed but not started reports the error as not fixed, and tells to start the module; it reported it as fixed without any change
  * [bugfix] `PublicationSanityDefaultCheck`: the fix of `NO_LIVE_NODE` on the content of a module publishes it; it reported it as fixed without any change
  * [bugfix] UI: on Jahia 8.1.5, a dropdown of the values of a fix which groups its choices, such as the principal of an ACE, displays the chosen or suggested value
  * [bugfix] `NodeNameInfoSanityCheck`: `j:nodename` is no longer checked under `/modules`, where the files of the modules are exposed in development mode by an external provider which does not store it
  * [bugfix] `AceSanityCheck`: an ACE without principal or without type was reported as a framework error instead of `NO_PRINCIPAL` or `NO_ACE_TYPE_PROP`
  * [bugfix] `AceSanityCheck`: the message of `NO_ROLES_PROP` read "ACEExternal ACE without property j:roles"
  * [bugfix] `BinaryPropertiesSanityCheck`: the fix of `INVALID_BINARY` removes the file of the error, or the binary property of its translation node. When the path of the property was longer than 100 characters, it removed the folder of the file instead
  * [bugfix] `HomePageDeclarationCheck`: the fix of `MULTIPLE_HOMES` in the default workspace was not saved, and it also removed the flag of the page kept as home, which left the site without home page
  * [bugfix] `VersionHistoryCheck`: the fix of `TOO_MANY_VERSIONS` was reported as successful when the purge kept more versions than the threshold, since the versions used by the default and the live nodes can't be purged. It is now reported as not fixed
  * [bugfix] `AceSanityCheck`: the fix of `MISSING_EXTERNAL_ACE` was reported as successful when the ACL listener could not create the external ACE, for example because an external ACE for other permissions takes its place. It is now reported as not fixed, and the ACL is no longer published
  * [bugfix] `UndeclaredNodeTypesCheck`: the fix of an undeclared mixin failed, since Jackrabbit refuses to remove a mixin whose type is not registered
* [bugfix] GraphQL: the results of an unknown scan returned an error instead of null
* [bugfix] GraphQL: `possibleValues` returned an internal error when `withErrorsOnly` was omitted, now false by default, or when `names` was omitted or repeated a column. The arguments `offset` and `pageSize` of `errors` are now required, so a missing one is reported by name instead of an internal error
* GraphQL: removed the argument `uploadResults` of the field `scan`: the CSV and XLSX reports of every scan are written in the JCR, next to its results
* GraphQL: added the fields `choiceLabels`, `choiceGroups` and `description` to `fixValues`, the description of the values which fix an error
* GraphQL: the errors of the scan results are sorted by workspace then by path. Added the fields `nodeErrors`, the errors of a node whatever the filters, and `fixErrors`, which fixes errors together with their values, the filter `fixStatus`, whose values are `fixed`, `skipped` and `notFixed`, and the field `fixFailed` of an error, true when its fix has run without fixing it
* GraphQL: added the field `fixAllErrors` to the scan results, which fixes all the errors matching the filters and returns the number of errors fixed, not fixed, skipped and already fixed, the identifiers of the errors not fixed, the errors skipped counted by reason (`skippedReasons`), and the errors not fixed grouped by check, error type and message (`failures`). The field `fixFailureMessage` of an error tells why its last fix has failed, when it raised an exception
* GraphQL: the fields which change the server are mutations, under `mutation { contentIntegrity }`: `integrityScan { scan stopRunningScan }`, `integrityCheckById { configure resetConfiguration resetAllConfigurations }`, `scanResultsDetails(id) { fixError fixErrors fixAllErrors }`, `deleteScanResults` and `deleteAllScanResults`. The queries only read. A client of the previous versions sends `scan`, `stopRunningScan`, `configure` and the resets of the configuration in a mutation instead of a query. `scanResultsDetails` of the mutations requires the identifier of the results
* [breaking] The module exports the package `org.jahia.modules.contentintegrity.api` only: `AbstractContentIntegrityCheck`, `JCRUtils` and `Constants` move to it from `org.jahia.modules.contentintegrity.services.impl`, which is removed, so a custom check must update its imports. The unused methods `setConditions()`, `setDescription()` and `getDescription()` of `AbstractContentIntegrityCheck` are removed, as well as the deprecated API of the errors without error type: the methods `createError()` which take a message instead of a `ContentIntegrityErrorType`, and `ContentIntegrityError.setErrorType()`. Declare the error types with `createErrorType()`. See [How to extend](how-to-extend.md#how-to-extend)
* Documentation: the screenshots of the administration screen in the README are taken by `tests/demo/screenshots.sh`, from the demo, with the ones of the checks, and `yarn docs:pdf` builds `README.pdf` as well as `docs/embedded-tests.pdf`
* Documentation: the checks which rely on the search index or on the modules started on the server, local to each server of a cluster, are listed in the FAQ of the README, and their documentation tells to run them on every server, and not to fix an error reported on some servers only: `TemplatesIndexationCheck`, `PagesSanityCheck`, `AceSanityCheck`, `ReferencesSanityCheck`, `UndeclaredNodeTypesCheck` and `UndeployedModulesReferencesCheck`. Results: the panel of a node tells it as well, under **In a cluster**, for each error of these types, and for `GROUP_DOES_NOT_EXIST`, whose fix finds the members of the group with the search index
* Added Cypress tests covering every error type of every check, its fix, the GraphQL API, the Karaf shell commands with all their options and the administration screen
* Security
  * Security filter: the module provides the scope `content_integrity_tools_token`, which grants the view issuing the tool access token of the links to the JCR browser. Without it, the profile `default` of the security filter, the one of Jahia 8.2, refuses the view, and a click on a path or a UUID in the results opens nothing. The view still requires `adminContentIntegrity`
  * Pass the name and value of a check configuration as GraphQL variables instead of concatenating them into the query
  * New permission `adminContentIntegrityFix`, required to fix the errors from the administration screen or with the GraphQL fields `fixError`, `fixErrors` and `fixAllErrors`. It is added to the role `server-administrator`, and is not granted by `adminContentIntegrity`, so a user who may run the scans and read their results may not fix the errors. The GraphQL field `canFixErrors` tells if the current user may fix the errors
  * The scan reports, uploaded under `/sites/systemsite/files/content-integrity-reports`, are readable by the server administrators only. The role `privileged`, which every editor of every site holds on the system site, is denied on the reports folder, when a report is written and when the module starts, so the reports of the previous versions are protected as well
  * `AceSanityCheck`: new error `ACE_OUTSIDE_ACL`, an access control entry which is not in the `j:acl` of a node, as an editor can create directly under a page. It grants nothing, and has no fix: the other errors are not reported on it, and the fixes refuse it, since they acted on its parent as on an ACL and on its grandparent as on the secured node. The fix of `ROLE_DOESNT_EXIST` thus published as root the page of an editor who may not publish it
  * `AceSanityCheck`: the fixes which delete an external access control entry keep the ACL left empty when it breaks the inheritance (`j:inherit` is `false`). Deleting it restored the roles granted on the ancestors, for example to the editors of the site on a restricted section
  * `SiteLevelSystemGroupsCheck`: the fix of `GROUP_DOES_NOT_EXIST` makes members of the group `site-privileged` the principals of the entries of an ACL only, as the ACL listener does. The principal of an entry created outside of an ACL became a member, and could read the unpublished content of the site
  * `ReferencesSanityCheck`: the fix of `BROKEN_REF_IN_FIELD` finds the link in a time which grows linearly with the length of the text. On a text made of many unclosed tags, its pattern held the request for minutes. When the placeholder is in the text itself, outside of a tag, the fix fails and leaves the text unchanged; it was reported as fixed
  * The language of the errors of a translation node, and the translation their fixes change, are read from the name of the node, as Jahia does, and no longer from `jcr:language`, which a translator can change: a translator could make the fix of an error of their language change the translation of another one. `JCRUtils.getTranslationNodeLocale` returns the language of the node name

#### <a name="v3-35"></a>3.35

Required Jahia version: 8.0.2.0

* Framework
  * Added a factory method to create framework errors without a stacktrace
  * Framework errors now embed the last line executed in the check
  * When executing a scan, specify the current user in the JCRSessionFactory
* Checks
  * Added new `UnreadablePublicationStatusCheck`: detects nodes that would trigger the "Issue when reading workflow and delete status of node" warning in `ComplexPublicationServiceImpl`
  * `HomePageDeclarationCheck`: when multiple home pages are detected, embed their names in the error object
  * `JCRLanguagePropertyCheck`: improved documentation
  * [bugfix] `AceSanityCheck`: avoid NPE when an ACE grants a privileged role to an invalid principal
* Security
  * Prevent injection by keeping CSV report cell values as literal text
  * Bump JS dependencies

#### <a name="v3-34"></a>3.34

Required Jahia version: 8.0.2.0

* Framework
  * Added some factory methods to easily track execution errors from a check
* UI
  * Results screen: display the number of matching errors even on the filters which are already in use
  * Results screen: automatically switch to the results of the last scan
  * [bugfix] Do not escape/unescape non string objects

#### <a name="v3-33"></a>3.33

Required Jahia version: 8.0.2.0

* Framework
  * When a java error occur, create a framework level error to have it tracked in the report
* UI
  * Results screen: display the number of matching errors next to each filter
  * Results screen: when the selected value for a filter is not available for the current results set, add it to the filter option list
  * Results screen: display all the filter options, even if there's no matching error with the currently applied filters
  * Results screen: Add the site to the error details popup
  * [bugfix] Jahia 8: do not display the admin panel in the navigation if the current user is not allowed to browse it
  * [bugfix] Escape the content that can embed some HTML reserved characters
* AceSanityCheck
  * Detect the missing members in the `site-privileged` groups
  * Detect the sites with a high number of ACE nodes

#### <a name="v3-32"></a>3.32

Required Jahia version: 8.0.2.0

* UndeclaredNodeTypesCheck
  * Added the detection of the definitions which have been removed but are still present in the `NodeTypeRegistry`

#### <a name="v3-31"></a>3.31

Required Jahia version: 8.0.2.0

* Implemented a new check to detect `types / properties / property values` which are valid as per the definitions, but are unexpected in a specific workspace, and have an impact on the import feature: `WorkspaceSpecificDefinitionsCheck`
* UI
  * Configurable checks: always display the configurations in their declaration order

#### <a name="v3-30"></a>3.30

Required Jahia version: 8.0.2.0

* Framework
  * Dispatch the reports under intermediate folders based on the scan date
  * [bugfix] Ignore `null` scan results
* AceSanityCheck
  * Detect the duplicated references to the same source ACE
* PublicationSanityLiveCheck
  * Flag `NO_DEFAULT_NODE` errors (non UGC `live` nodes without `default` node) as impacting the import
* VersionHistoryCheck
  * Track in the error object if the node is auto-published

#### <a name="v3-29"></a>3.29

Required Jahia version: 8.0.2.0

* Compatibility with Jahia 8.2
* Implemented a new check to detect the pages which use an invalid template: `PagesSanityCheck`
* Framework
  * Log the module version when starting/finishing a scan
  * Flagged the errors which make the XML import fail, and log at the end of a scan the compatibility withthe XML import
  * Added the impact on the XML import to the reports (new column)
  * [bugfix] When scanning the 2 workspaces, merge the results even if not uploading the results
  * [bugfix] Fixed the calculation of the node existence in `live`
* UI
  * Added a column to filter the errors which have an impact on the XML import
  * [bugfix] Fixed the label of the filters
* PublicationSanityDefaultCheck
  * `PATH_CONFLICT` errors: add the conflicted `live` node primary type to the report
  * `PATH_CONFLICT` errors: add the path of the conflicted node in the `default` workspace to the report
  * Detect the nodes which have a different primary type in the `default` and `live` workspaces
* NodeNameInfoSanityCheck
  * [bugfix] When the node has no last publication related metadata, assume that its live path is correct
* JCRLanguagePropertyCheck
  * Add the parent node type to the report
* ChildNodeDefinitionsSanityCheck
  * Add the parent node type to the report

#### <a name="v3-28"></a>3.28

Required Jahia version: 8.0.2.0

* PropertyDefinitionsSanityCheck
  * [bugfix] JCRNodeValidator check: execute the JCRNodeValidator with the correct locale
  * [bugfix] Avoid logging an error when the node does not exist in the specified language
  * [bugfix] Fixed the `Locale` conversion to `String` when using a regional variant

#### <a name="v3-27"></a>3.27

Required Jahia version: 8.0.2.0

* Framework
  * Track the Jahia version in the report metadata
  * Reduced the memory footprint
  * [bugfix] Do not limit the size of the extra message
* PropertyDefinitionsSanityCheck
  * Execute the JCRNodeValidator (only the JCR constraints were checked)

#### <a name="v3-26"></a>3.26

Required Jahia version: 8.0.2.0

* Framework
  * Reduce the priority of the thread which executes the scan
* PublicationSanityDefaultCheck
  * Detect the conflicts between 2 nodes that use the same path (one in `default`, the other one in `live`)
  * Improved the documentation
  * [bugfix] Do not check the external nodes, what was generating a lot of false positives
* StaticInternalLinksCheck
  * Added the possibility to ignore `localhost`
  * Do not scan the properties if there's no domain to scan

#### <a name="v3-25"></a>3.25

Required Jahia version: 8.0.2.0

* Framework
  * When too many errors are identified, split the reports into several files to avoid OOM
* AceSanityCheck
  * Added the detection of ACE nodes with an invalid node name
* PublicationSanityDefaultCheck
  * Detect the module technical nodes which are missing in live
* PublicationSanityLiveCheck
  * Avoid false positives on UGC ACE nodes: since they are not tracked as UGC nodes, they can't beconsidered as non UGC and raise an error because of no node with same uuid in the default workspace

#### <a name="v3-24"></a>3.24

Required Jahia version: 8.0.2.0

* Framework
  * Track the content-integrity version used in the logs and on the report in the JCR
  * Track the scan summary data only once on the folder instead of on each report file node
* UI
  * Added a prefix to the log lines where the server logs contain a stacktrace
* AceSanityCheck
  * Error ACE_NON_GRANT_WITH_EXTERNAL_ACE : add the role names as error extra info to ease the report analysis
  * Sort the role names when added as error extra info to ease the errors aggregation in the report
  * [bugfix] Moved the external ace path from extra info to specific extra infos as it breaks the errors aggregation in the report
* StaticInternalLinksCheck
  * Track the locale when the property is found on a translation node
* VersionHistoryCheck
  * [bugfix] Moved the exact count from extra info to specific extra infos as it breaks the errors aggregation in the report
* PublicationSanityLiveCheck
  * [bugfix] Fixed the calculation of the locale on `jnt:referenceInField` nodes related to a non i18n property
  * [bugfix] Skip technical sub-nodes related to a locale for which the translation node is missing

#### <a name="v3-23"></a>3.23

Required Jahia version: 8.0.2.0

* Framework
  * [bugfix] Do not start the scan if the root node does not exist in the workspace
  * Improved the method to calculate the publication status so that it can be calculated for a technical Jahia node
* UI
  * When more than one workspace is scanned, print the number of errors per workspace in the logs when logging the total number of errors
  * [bugfix] Avoid error in the server logs when downloading the scan logs over GraphQL
* PublicationSanityLiveCheck
  * [bugfix] Improved the handling of the technical sub nodes (ex: an internal link is deleted from a richtext. The technical node holding the reference is immediately deleted from the `default` workspace, but will be deleted as well in the live `workspace` only when the content node is published. The technical node was reported as missing in `default`, and this was a false positive)
* LockSanityCheck
  * [bugfix] Avoid errors on translation nodes with lock related properties but no `j:lockTypes` property
* ReferencesSanityCheck
  * Reduced the memory footprint

#### <a name="v3-22"></a>3.22

Required Jahia version: 8.0.2.0

* Framework
  * Added the possibility to send additional errors during the finalization phase
  * Removed the possibility to generate a CSV file without header since the report is not limited to the CSV file anymore
  * Avoid storing too long texts in the errors extra infos
  * When merging error lists, return a new list instead of modifying a provided one
* UI
  * Report UI: display the uuid as a link to the JCR browser

#### <a name="v3-21"></a>3.21

Required Jahia version: 8.0.2.0

* Implemented a new check to detect the invalid references: `ReferencesSanityCheck`
* Framework
  * [bugfix] Fixed the calculation of the count ranges (used to aggregate errors returning a similar number, such as the number of versions)
  * [bugfix] Excel report: avoid crashing when the scan has identified more than 32767 errors
  * [bugfix] Improved the reset of the JCR sessions between 2 executions

#### <a name="v3-20"></a>3.20

Required Jahia version: 8.0.2.0

* Framework
  * Added the possibility to generate several report files
  * Added the generation of an Excel report, with a sheet containing the errors data, and a sheet with a predefined pivot table for errors analysis
  * Errors related to a module (under `/modules`) are now showing the module name in the "_site_" column of the reports
  * [bugfix] Avoid crashing when an error occurs while loading the next child node of a node (during the scan itself or during the calculation of the size of the tree)
* NodeNameInfoSanityCheck
  * Disabled the check of the fullpath by default, with the possibility to enable it back by configuration
* PublicationSanityDefaultCheck
  * Added the detection of the auto-published nodes which are missing from the `live` workspace

#### <a name="v3-19"></a>3.19

Required Jahia version: 8.0.2.0

* UI
  * Added a panel to analyze the errors raised by the past scans
  * Increased the size of the logs area in the scan panel
  * Log the intermediate errors count in the UI logs as well
* Framework
  * Increased the scan results cache duration to 5 weeks (time to idle)
  * Limited the size of the logs sent over GQL
  * [bugfix] Skip the nodes which do not have a correct path according to their node name and parent node path, to avoid `StackOverflowError` errors
  * [bugfix] Keep the declaration order of the check configurations, so that the order is the same in the UI when a check holds multiple configurations
* BinaryPropertiesSanityCheck
  * Added the possibility to consider the valid binaries with a length of zero byte as errors
  * Fully read the binary and compare it to its size when using the `download-stream` strategy
* NodeNameInfoSanityCheck
  * Improved the detection of the real errors
  * Excluded some false positives
  * The check is now active by default, since the error detection is finalized
* Implemented a new check to detect the hardcoded internal links: `StaticInternalLinksCheck`

#### <a name="v3-18"></a>3.18

Required Jahia version: 8.0.2.0

* Framework
  * Added a `Site` column in the generated CSV reports
  * Excluded paths are now tracked in the logs
  * Add the mixin `jmix:nolive` to the reports uploaded in the JCR, to avoid unwanted publications (this doesn't apply to the already uploaded reports)
  * Implemented the possibility to check if the conditions of an integrityCheck are reachable during a scan, and exclude those for which they are not
  * Do not start the scan if no integrity check can be run
  * [bugfix] Prevent scanning when starting a scan from a node in the subtree of an excluded path
* PublicationSanityLiveCheck
  * When different mixins are found on a published node in the 2 workspaces, add the mixin names to error data
  * Excluded some properties from the comparison to avoid false positives
* AceSanityCheck
  * [bugfix] Avoid false positives when processing an external ACE defined for a scope declared not by the role itself, but inherited from its parent role

#### <a name="v3-17"></a>3.17

Required Jahia version: 8.0.2.0

* UI
  * [bugfix] Do not generate a link to the documentation for the custom checks
  * [bugfix] When selecting no check, a scan was run using every active check
  * When receiving additional logs, keep the current position if not scrolled to the bottom
  * [bugfix] When scanning the 2 workspaces, do not start scanning the second workspace when stopping the process during the scan of the first one
* Framework
  * Separate in the CSV reports the extra information which can be used for grouping purpose from those which are specific to the current error

#### <a name="v3-16"></a>3.16

Required Jahia version: 8.0.2.0

* Framework
  * [bugfix] Fixed the execution of disabled checks when running a scan with a list of checks to execute
* UndeployedModulesReferencesCheck
  * Removed the site name from the errors to generate the same text for the same missing module
* AceSanityCheck
  * [bugfix] `ACE_NON_GRANT_WITH_EXTERNAL_ACE` : fixed the description of the error
* Script `scanRefsToSiteContent`
  * Added the possibility to track the external references in the references keeper

#### <a name="v3-15"></a>3.15

Required Jahia version: 8.0.2.0

* Implemented a new check on the properties defined by the mixin `jmix:nodenameInfo`: `NodeNameInfoSanityCheck`
* PublicationSanityLiveCheck
  * [bugfix] Avoid false positives related to the mixin `jmix:deletedChildren`

#### <a name="v3-14"></a>3.14

Required Jahia version: 8.0.2.0

* Framework
  * Execution duration summary: log the duration of the initial nodes count
  * Execution duration summary: improved the calculation of the duration of the scan of the tree
  * Execution duration summary: improved the formatting
  * Added the possibility to exclude the virtual nodes
* UI
  * Added a configuration to exclude the virtual nodes
  * Added a link to the documentation of each check
* Command `jcr:integrity-configureCheck`
  * Added a configuration to exclude the virtual nodes
* AceSanityCheck
  * Detect the `ACE` which are not of type `GRANT` but are referenced by an `external ACE`
* UndeployedModulesReferencesCheck
  * Improved the performances

#### <a name="v3-13"></a>3.13

Required Jahia version: 8.0.2.0

* Framework
  * Write the execution summary and some other information related to the scan on the report file node in the JCR
  * Execution duration summary: log the number of scanned nodes
  * Execution duration summary: log the number of errors found
  * Improved the formatting of the generated CSV file to ease the parsing by Excel (*we miss you Greg :)* )
* BinaryPropertiesSanityCheck
  * Reduced the duration of the check by default, using a light strategy. The former strategy can be enabled back by configuration
* PropertyDefinitionsSanityCheck
  * [bugfix] Fixed the validation of the properties declared as `unstructured`, which were wrongly reported as undeclared

#### <a name="v3-12"></a>3.12

Required Jahia version: 8.0.2.0

* Framework
  * [bugfix] Fixed the automated disabling of a check which throws too many errors
* PropertyDefinitionsSanityCheck
  * [bugfix] Fixed NPE

#### <a name="v3-11"></a>3.11

Required Jahia version: 8.0.2.0

* Implemented a new check to detect child nodes not allowed by their parent node definition: `ChildNodeDefinitionsSanityCheck`
* Framework
  * Added the possibility to disable a check for the rest of the duration of the scan
* PropertyDefinitionsSanityCheck
  * [bugfix] Fixed the detection of the `i18n` properties written on the main node (now reported as undeclared)
  * Performance improvement
* JCRLanguagePropertyCheck
  * Embed in the error the value of the `jcr:language` property when detected as inconsistent with the node name

#### <a name="v3-10"></a>3.10

Required Jahia version: 8.0.2.0

* Implemented a new check on the user accounts: `UserAccountSanityCheck`
  * Detect the cased when the user is not granted the role `owner` on his own account

#### <a name="v3-9"></a>3.9

Required Jahia version: 8.0.2.0

* [bugfix] Fixed the UI when not running Jahia under the ROOT context

#### <a name="v3-8"></a>3.8

Required Jahia version: 8.0.2.0

* AceSanityCheck
  * [bugfix] Avoid false positives with external ACE defined for a path that ends with a slash

#### <a name="v3-7"></a>3.7

Required Jahia version: 8.0.2.0

* UI
  * When refreshing the logs, scroll to the bottom
  * Added the possibility to exclude some paths from the scan

#### <a name="v3-6"></a>3.6

Required Jahia version: 8.0.2.0

* UI
  * [bugfix] Fixed the save of the configurations

#### <a name="v3-5"></a>3.5

Required Jahia version: 8.0.2.0

* UI
  * Moved the UI under system-health
  * Display the description of the configurations
  * Display a link to download the report at the end of a scan
  * [bugfix] Do not crash when an invalid root path is specified
  * [bugfix] Fixed the label in the navigation

#### <a name="v3-4"></a>3.4

Required Jahia version: 8.0.2.0

* Implemented a UI (server settings)
* Implemented a GraphQL API
* [bugfix] Prevent concurrent executions
* [bugfix] Catch any error thrown when scanning the children nodes, to let the scan continue properly

#### <a name="v3-3"></a>3.3

Required Jahia version: 8.0.2.0

* Command `jcr:integrity-printTestResults`
  * Added the possibility to upload the report in the JCR
* Command `jcr:integrity-check`
  * [bugfix] Do not skip the disabled checks when they are explicitly specified in the list of checks to execute
* PublicationSanityDefaultCheck
  * [bugfix] When the scan is not run from the root of the JCR, and a different path in default & live is identified on the root node of the scan, do not log an error on each sub-node
* PublicationSanityLiveCheck
  * Implemented a comparison on the property values between the workspaces default and live, on the nodes without pending modifications
* Implemented a [groovy script](groovy-scripts.md#scanrefstositecontent) to detect the references from outside from a site
* Improved the output of the errors list in the shell
* Refactored the check ID to use a string instead of a number
* Implemented a completer on the check ID

#### <a name="v3-2"></a>3.2

Required Jahia version: 8.0.2.0

* Implemented a new execution condition, based on some properties defined on the node
* SiteLevelSystemGroupsCheck
  * [bugfix] If the `privileged` group is missing, log it only once, instead of one time per scanned site
* WipSanityCheck
  * [bugfix] Fixed the execution conditions
* PublicationSanityDefaultCheck
  * When the scan is not run from the root of the JCR, and a different path in default & live is identified on the root node of the scan, consider the possibility that it is an inherited consequence from a parent node outside from the scope of the scan
* HomePageDeclarationCheck
  * When no page is flagged as the home page, but a sub-node of the site is named `home`, generate a different error depending on if this node is a page or not
* LockSanityCheck
  * Added the detection of the deletion locks remaining on a translation node after an undeletion
* Command `jcr:integrity-configureCheck`
  * Added a completer on the parameter name option (`-p`)

#### <a name="v3-1"></a>3.1

Required Jahia version: 8.0.2.0

* AceSanityCheck
  * Improved the detection of external ACE having a wrong path
  * [bugfix] Avoid false positives with external ACE defined for an ACE stored outside from a site and granting permissions on the current site
  * [bugfix] Avoid logging twice the external ACE defined for an ACE not of type GRANT
* VersionHistoryCheck
  * Reduced the scan duration in the live workspace. Only the UGC nodes will be considered when scanning this workspace, the other nodes will be skipped since they can be checked while scanning the default workspace

#### <a name="v3-0"></a>3.0

Required Jahia version: 8.0.2.0

* First version for the Jahia 8.x family
* Equivalent to `content-integrity 2.20`

### Jahia 7 (no longer maintained since v3.35)

#### <a name="latest-v7"></a><a name="v2-54"></a>2.54

Required Jahia version: 7.3.1.0

Same as [3.34](#v3-34)

#### <a name="v2-53"></a>2.53

Required Jahia version: 7.3.1.0

Same as [3.33](#v3-33)

#### <a name="v2-52"></a>2.52

Required Jahia version: 7.3.1.0

Same as [3.32](#v3-32)

#### <a name="v2-51"></a>2.51

Required Jahia version: 7.3.1.0

Same as [3.31](#v3-31)

#### <a name="v2-50"></a>2.50

Required Jahia version: 7.3.1.0

Same as [3.30](#v3-30)

#### <a name="v2-49"></a>2.49

Required Jahia version: 7.3.1.0

Same as [3.29](#v3-29)

#### <a name="v2-48"></a>2.48

Required Jahia version: 7.3.1.0

Same as [3.28](#v3-28)

#### <a name="v2-47"></a>2.47

Required Jahia version: 7.3.1.0

Same as [3.27](#v3-27)

#### <a name="v2-46"></a>2.46

Required Jahia version: 7.3.1.0

Same as [3.26](#v3-26)

#### <a name="v2-45"></a>2.45

Required Jahia version: 7.3.1.0

Same as [3.25](#v3-25)

#### <a name="v2-44"></a>2.44

Required Jahia version: 7.3.1.0

Same as [3.24](#v3-24)

#### <a name="v2-43"></a>2.43

Required Jahia version: 7.3.1.0

Same as [3.23](#v3-23)

#### <a name="v2-42"></a>2.42

Required Jahia version: 7.3.1.0

Same as [3.22](#v3-22)

#### <a name="v2-41"></a>2.41

Required Jahia version: 7.3.1.0

Same as [3.21](#v3-21)

#### <a name="v2-40"></a>2.40

Required Jahia version: 7.3.1.0

Same as [3.20](#v3-20)

#### <a name="v2-39"></a>2.39

Required Jahia version: 7.3.1.0

Same as [3.19](#v3-19)

#### <a name="v2-38"></a>2.38

Required Jahia version: 7.3.1.0

Same as [3.18](#v3-18)

#### <a name="v2-37"></a>2.37

Required Jahia version: 7.3.1.0

Same as [3.17](#v3-17)

#### <a name="v2-36"></a>2.36

Required Jahia version: 7.3.1.0

Same as [3.16](#v3-16)

#### <a name="v2-35"></a>2.35

Required Jahia version: 7.3.1.0

Same as [3.15](#v3-15)
* UI
  * [bugfix] Fixed GraphQL errors making the UI unresponsive

#### <a name="v2-34"></a>2.34

Required Jahia version: 7.3.1.0

Same as [3.14](#v3-14)

#### <a name="v2-33"></a>2.33

Required Jahia version: 7.3.1.0

Same as [3.13](#v3-13)

#### <a name="v2-32"></a>2.32

Required Jahia version: 7.3.1.0

Same as [3.12](#v3-12)

#### <a name="v2-31"></a>2.31

Required Jahia version: 7.3.1.0

Same as [3.11](#v3-11)

#### <a name="v2-30"></a>2.30

Required Jahia version: 7.3.1.0

Same as [3.10](#v3-10)

#### <a name="v2-29"></a>2.29

Required Jahia version: 7.3.1.0

Same as [3.9](#v3-9)

#### <a name="v2-28"></a>2.28

Required Jahia version: 7.3.1.0

Same as [3.8](#v3-8)

#### <a name="v2-27"></a>2.27

Required Jahia version: 7.3.1.0

Same as [3.7](#v3-7)

#### <a name="v2-26"></a>2.26

Required Jahia version: 7.3.1.0

Same as [3.6](#v3-6)

#### <a name="v2-25"></a>2.25

Required Jahia version: 7.3.1.0

Same as [3.5](#v3-5) (excepted the bugfix on the navigation in V8)

#### <a name="v2-24"></a>2.24

Required Jahia version: 7.3.1.0

Same as [3.4](#v3-4)

#### <a name="v2-23"></a>2.23

Required Jahia version: 7.3.0.0

Same as [3.3](#v3-3)

#### <a name="v2-22"></a>2.22

Required Jahia version: 7.3.0.0

Same as [3.2](#v3-2)

#### <a name="v2-21"></a>2.21

Required Jahia version: 7.3.0.0

Same as [3.1](#v3-1)

#### 2.20

Required Jahia version: 7.3.0.0

* Command `jcr:integrity-check`
  * Added the possibility to specify the checks to execute
  * Added the possibility to specify some checks to skip
  * Added the possibility to interrupt a scan before its end
  * [bugfix] Calculation of the number of nodes to scan: do not crash on non JCR exceptions
* WipSanityCheck
  * Detect nodes with an invalid value in j:workInProgressStatus
  * [bugfix] Fixed invalid error messages

#### 2.19

Required Jahia version: 7.3.0.0

* Implemented a check to detect nodes based on a missing type
* Reduce the memory footprint of the scan
* [bugfix] Fixed NPE when the error has no extra infos
* [bugfix] Avoid NPE on null errors

#### 2.18

Required Jahia version: 7.3.0.0

* PropertyDefinitionsSanityCheck
  * [bugfix] Consider Reference and Weakreference as the same type when comparing the value type with the property type
* AceSanityCheck
  * Added some more info to explain & ease troubleshooting the "invalid principal" errors

#### 2.17

Required Jahia version: 7.3.0.0

* Refactored the test results ID to embed the workspace name
* Log the intermediate counts while counting the nodes to be scanned
* Unified the output of the errors
* [bugfix] Fixed the calculation of the number of nodes to scan when there's an error while iterating the JCR
* PropertyDefinitionsSanityCheck
  * [bugfix] Fixed the calculation of the fact that the external permission path is relative to the current site
  * [bugfix] Fixed the creation of errors when an ACE and its related currentSite external ACE are in different sites
  * [bugfix] When calculating the effective type of the values of a multi-valued property, return `weakreference` instead of `undefined` when the values are a mix of `weakreference` and `reference`
* PublicationSanityLiveCheck
  * [bugfix] Fixed the execution condition to catch every node having the property `j:originWS`

#### 2.16

Required Jahia version: 7.3.0.0

* Implemented a check on the property definitions
  * Detection of the missing mandatory properties
  * Detection of the properties having their value not matching the declared constraint
  * Detection of the properties having their value not matching the declared type
  * Detection of the properties having a single-valued value and declared as multi-valued
  * Detection of the properties having a multi-valued value and declared as single-valued
  * Detection of the non declared properties
* Implemented a command to print out some extended information about an error
* Sum up of the check durations at the end of the scan: sort the checks by duration

#### 2.15

Required Jahia version: 7.3.0.0

* AceSanityCheck
  * Detect the external ACE defined for a role which does not exist
  * Detect the external ACE defined for external permissions which are not declared by the role
* VersionHistoryCheck
  * [bugfix] When the version history can't be loaded for a node, log the full stacktrace only when the log level is set to debug
  * [bugfix] Skip the external nodes with a custom decorator

#### 2.14

Required Jahia version: 7.3.0.0

* AceSanityCheck
  * Detect ACE / external ACE without ACE type
  * Detect external ACE with a type different from 'GRANT'
  * Detect external ACE with a reference to a source ACE having a type different from 'GRANT'
  * Detect the ACE having a type different from 'GRANT' and being referenced by an external ACE
  * [bugfix] Do not check the consistency of the external ACE for the ACE having a type different from 'GRANT'

#### 2.13

Required Jahia version: 7.3.0.0

* AceSanityCheck
  * [bugfix] Fixed the detection of ACE which are not in the same site as their related external ACE
  * [bugfix] Fixed the calculation of the false positives when dealing with a broken reference to a source ACE in live

#### 2.12

Required Jahia version: 7.3.0.0

* AceSanityCheck
  * Log external ACE with a property j:sourceAce having no value
  * [bugfix] Fixed the detection of ACE which are not in the same site as their related external ACE

#### 2.11

Required Jahia version: 7.3.0.0

* [bugfix] Fixed the comparison of the roles between an ACE and a related externalACE
* UndeployedModulesReferencesCheck
  * Return one error per missing module
* [bugfix] Do not report errors for nodes having a different path between default and live when the node is not the root of the move operation

#### 2.10

Required Jahia version: 7.3.0.0

* Improved the sanity check on the ACE nodes

#### 2.9

Required Jahia version: 7.3.0.0

* Implemented a factory method to create an empty errors list
* [bugfix] Fixed the calculation of the number of nodes to scan when scanning the whole repository
* [bugfix] Fixed the time calculation in case of error

#### 2.8

Required Jahia version: 7.3.0.0

* Implemented a sanity check on the ACE nodes, in order to detect ACE related to deleted users or groups

#### 2.7

Required Jahia version: 7.3.0.0

* Added the possibility to configure some integrity checks
* Implemented a sanity check to detect nodes with too many versions

#### 2.6

Required Jahia version: 7.2.0.0

* [bugfix] Move operation that can't be published: do not test on the translation nodes
* Added an optional header to the CSV dump (generated by default)

#### 2.5

Required Jahia version: 7.2.0.0

* Implemented an option to exclude a subtree from the scan
* Implemented an "estimated time of arrival"

#### 2.4

Required Jahia version: 7.2.0.0

* Implemented a sanity check on the system groups

#### 2.3

Required Jahia version: 7.2.0.0

* Implemented a sanity check for the "work in progress" related properties
* Implemented the possibility to define a minimum Jahia version for an integrity check
* [bugfix] Fixed the execution conditions on MarkedForDeletionCheck

#### 2.2

Required Jahia version: 7.2.0.0

* Implemented TemplatesIndexationCheck

#### 2.1

Required Jahia version: 7.2.0.0

* Implemented BinaryPropertiesSanityCheck

#### 2.0

Required Jahia version: 7.2.0.0

* Initial version
