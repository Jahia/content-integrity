<a href="https://store.jahia.com/contents/modules-repository/org/jahia/modules/content-integrity.html">
    <img src="https://www.jahia.com/modules/jahiacom-templates/images/jahia-3x.png" alt="Jahia logo" title="Jahia" align="right" height="60" />
</a>

# <a name="summary"></a>Content Integrity
Jahia module that provides an extensible service to test the integrity of the content
* [How to use it](#how-to-use)
    * [Configuration](#configuration)
    * [UI](#how-to-use-ui)
    * [Karaf Shell commands](#karaf-shell-commands)
        * [jcr:integrity-check](#jcrintegrity-check) 
        * [jcr:integrity-printError](#jcr-integrity-printError) 
        * [jcr:integrity-printChecks](#jcr-integrity-printChecks) 
        * [jcr:integrity-printTestResults](#jcr-integrity-printTestResults) 
        * [jcr:integrity-configureCheck](#jcr-integrity-configureCheck) 
* [Embedded tests](docs/embedded-tests.md#summary)
* [FAQ](#faq)
* [How to extend it](docs/how-to-extend.md#summary) 
* [Groovy scripts](docs/groovy-scripts.md#summary)
* [Release notes](docs/release-notes.md#summary) 
* [Cypress tests](tests/README.md)

## <a name="how-to-use"></a>How to use?

### Configuration

Two permissions, granted at server level, control the feature:

* `adminContentIntegrity` allows to run an integrity scan, to configure the checks and to read the results.
* `adminContentIntegrityFix` allows to fix the errors, one at a time or all the errors matching the filters. A fix writes to the repository with a system session, whatever the ACL of the fixed node, so this permission is not granted by `adminContentIntegrity`. Without it, the administration screen offers no fix, and the GraphQL fields `fixError` and `fixAllErrors` are refused.

By default, both permissions are added to the role `server-administrator`, thus any server administrator is able to use the feature and to fix the errors. To delegate the scans only, grant a server role holding `adminContentIntegrity`, plus `administrationAccess` to open the administration.

The command `jcr:integrity-fix` of the Karaf console is not controlled by these permissions, since the access to the console already requires to be a system administrator.

### <a name="how-to-use-ui"></a>UI

The module adds a page to the administration, under **Server > System > Content Integrity**. The page displays the last scan and the scan results. Use **New scan** to run a scan.

![Content Integrity page](./docs/img/ui-overview.png)

#### Run a scan

**New scan** opens a dialog to configure the scan. The dialog keeps the selection and the parameters from one scan to the next.

![New scan dialog](./docs/img/ui-new-scan.png)

All the available checks, provided by the module itself or by extensions, are listed. Those configured as active are preselected. Use **Select all** and **Unselect all** to change the selection quickly.

Each check has a help button that opens its documentation. A configurable check also has a configure button, which opens a dialog to edit its parameters. The dialog can also reset them to their default values.

The scan is executed on a tree, by default on the whole JCR, starting from its root node. To scan a single site, a specific section, the files hierarchy of a site, ... , specify the related root node as the root of the scan.

All the nodes under the specified one will be scanned, unless some subtrees are specified to be excluded. If needed, add the path of the root nodes of those trees to skip, then click on a path to remove it from the list.

The scan can be run on a single workspace, or on both.

Virtual nodes (e.g. exposed by an EDP connector) can be excluded from the scan. This is useful when processing those nodes involves a lot of connections to a 3rd party system, with an important impact on the duration of the scan.

#### Follow the scan

While a scan runs, its logs are displayed above the results, and **Stop** interrupts it. The scan is run in background, and leaving the page will have no impact on its execution: when coming back to the page, the running scan is displayed again.

When the scan is over, its results are displayed, and its reports can be downloaded from the JCR (uploaded under `/sites/systemsite/files/content-integrity-reports`). A report describes the whole scanned repository, so the reports folder is readable by the server administrators only: the role `privileged`, which every editor of every site holds on the system site, is denied on it. The page always displays the last scan, with its status, its logs and its reports.

#### Explore the results

Select the scan to display, and choose the columns to display: the check name, the error type, the workspace, the path and the message are displayed by default. The filters are always displayed, and filter the errors on the check, the error type, the workspace, the site, the node type, the locale, the message or the impact on the XML import. Each filter value shows its number of errors. By default, only the errors which make the XML import fail are displayed: **Clear the filters** displays all of them.

**Fix all** runs the fix of every error matching the filters, after a confirmation, then displays how many errors were fixed, not fixed, skipped and already fixed. The errors whose check provides no fix, and the ones fixed with a typed value, are skipped. Some fixes remove content, so narrow the filters to the errors to fix first.

Click on the path or the UUID of a node to open it in the JCR browser of the `tools` area. The actions on an error are in the menu of the 3 dots at the end of its row: **Error details** displays all its information, including the extra information provided by the check, and **Fix** runs the fix of the check which has detected it, when the check provides one. **Fix…** opens the details of an error which is fixed with a value to type, such as a missing mandatory property. The outcome of a fix stays displayed in the row.

The results are kept in memory: they are lost when the module or the server restarts. The reports uploaded to the JCR remain available.

### Karaf Shell commands
The content integrity service is available through the [Karaf console](https://academy.jahia.com/documentation/system-administrator/jahia/8/installing-and-configuring-jahia/installing-configuring-and-troubleshooting-jahia/configuring-jahia-features#osgi-ssh-console).

Use `jcr:cd {path}` to position yourself on the node from which you want to start the scan.

Use `jcr:workspace {workspace}` if you want to change the workspace to scan.

Use `jcr:integrity-check` to run a content integrity test.

    jahia@dx()> jcr:cd /sites/mySite/
    /sites/mySite
    jahia@dx()> jcr:integrity-check
    Content integrity tested in 128 ms
    No error found
    jahia@dx()>
    
#### jcr:integrity-check  
Runs a scan of the current tree and current workspace.

**Options:**  

| Name     | alias     |         Value          | Mandatory | Multiple | Description                                                                                                                                                                                                                                                                                                                                                                             |
|----------|-----------|:----------------------:|:---------:|:--------:|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| -l       | --limit   | positive integer, [20] |           |          | Specifies the maximum number of errors to print out                                                                                                                                                                                                                                                                                                                                     |
| -x       | --exclude |         string         |           |    x     | Specifies one or several subtrees to exclude                                                                                                                                                                                                                                                                                                                                            |
| -c       | --checks  |         string         |           |    x     | Specifies the checks to execute, specified by their ID. <br/> Only the specified checks will be executed during the current scan, no matter the global configuration.<br/> The check IDs can also be prefixed with ':' to specify a check to be skipped. In such case, the scan will execute all the currently active checks but those specified to be skipped during the current scan. |
| --skipMP |           |                        |           |          | If specified, the virtual nodes are excluded from the scan                                                                                                                                                                                                                                                                                                                              |

**Examples:**

    jcr:cd /sites/mySite/
    jcr:workspace live
    jcr:integrity-check -l 10   
    
    jcr:cd /sites
    jcr:integrity-check -x /sites/aHugeSite
    jcr:integrity-check -x /sites/aHugeSite -x /sites/anotherHugeSite/files 
                                                              
A scan can be interrupted before the end of its execution. Refer to the [FAQ section](#scan-interruption) for more details.

#### <a name="jcr-integrity-printError"></a>jcr:integrity-printError
Prints out some extended information about an error.

**Argument:**

| Value  | Mandatory | Multiple | Description                     |
|:------:|:---------:|:--------:|---------------------------------|
| string |     x     |    x     | ID of the error(s) to print out |

**Options:**

| Name | alias  | Value  | Mandatory | Multiple | Description                                                                  |
|------|--------|:------:|:---------:|:--------:|------------------------------------------------------------------------------|
| -t   | --test | string |           |          | ID of the test from which to load the error. Latest test used if not defined |

**Example:**

    jcr:cd /sites/mySite/
    jcr:integrity-check 
    Content integrity tested in 44 seconds (44609 ms)
    ID | Fixed | Error                          | Workspace | UUID                                 | Node type         | Locale | Message
    --------------------------------------------------------------------------------------------------------------------------------------------------------
    0  |       | PropertyConstraintsSanityCheck | default   | c847913d-64f1-4c23-a6f6-1b7833f8024f | jnt:page          | fr     | Missing mandatory property
    1  |       | PropertyConstraintsSanityCheck | default   | a60dc57a-0bd3-4908-9b58-7e60dc558a34 | jnt:page          | fr     | Missing mandatory property
    2  |       | PropertyConstraintsSanityCheck | default   | dbb81015-5cac-4887-90d5-68c9be704ac8 | mynt:internalLink | fr     | Missing mandatory property
    
    jahia@dx()> jcr:integrity-printError 2
    ID             | 0
    Check name     | PropertyConstraintsSanityCheck
    Check ID       | 8
    Fixed          | false
    Workspace      | default
    Locale         | fr
    Path           | /sites/mySite/home/missions/internalLink-3
    UUID           | dbb81015-5cac-4887-90d5-68c9be704ac8
    Node type      | mynt:internalLink
    Mixin types    |
    Message        | Missing mandatory property
    property-name  | node
    error-type     | EMPTY_MANDATORY_PROPERTY
    declaring-type | mynt:internalLink

#### <a name="jcr-integrity-printChecks"></a>jcr:integrity-printChecks 
Prints out the currently registered checks. 
                         
**Options:**

| Name | alias         |      Value      | Mandatory | Multiple | Description                       |
|------|---------------|:---------------:|:---------:|:--------:|-----------------------------------|
| -l   | --outputLevel | [simple] , full |           |          | Specifies the output level to use |

**Example:**

    jahia@dx()> jcr:integrity-printChecks
    Integrity checks (8):
       FlatStorageCheck (id: 1, priority: 0.0, enabled: true)
       HomePageDeclaration (id: 2, priority: 100.0, enabled: true)
       JCRLanguagePropertyCheck (id: 3, priority: 100.0, enabled: true)
       LockSanityCheck (id: 4, priority: 100.0, enabled: true)
       MarkForDeletionCheck (id: 5, priority: 100.0, enabled: true)
       PublicationSanityDefaultCheck (id: 6, priority: 100.0, enabled: true)
       PublicationSanityLiveCheck (id: 7, priority: 100.0, enabled: true)
       UndeployedModulesReferencesCheck (id: 8, priority: 100.0, enabled: true)
 
#### <a name="jcr-integrity-printTestResults"></a>jcr:integrity-printTestResults   
Allows to reprint the result of a previous test.   
                         
**Options:**

| Name | alias                |         Value          | Mandatory | Multiple | Description                                                                                                                      |
|------|----------------------|:----------------------:|:---------:|:--------:|----------------------------------------------------------------------------------------------------------------------------------|
| -l   | --limit              | positive integer, [20] |           |          | Specifies the maximum number of errors to print out                                                                              |
| -d   | --dump               |                        |           |          | Dumps the errors into report files in temp/content-integrity/ if used. The limit option is ignored when dumping                  |
| -u   | --upload             |                        |           |          | Uploads the report files in the JCR instead of writing in on the filesystem. This option has no effect if not combined with `-d` |
| -ef  | --excludeFixedErrors |                        |           |          | Coming soon                                                                                                                      |

**Example:**

    jahia@dx()> jcr:integrity-check
    Content integrity tested in 141 ms
    No error found
    jahia@dx()> jcr:integrity-printTestResults -d
    Dumped into C:\DigitalExperienceManager-EnterpriseDistribution-7.2.3.0\tomcat\temp\content-integrity\default_2023_04_21-13_34_15_786-full.csv
    Dumped into C:\DigitalExperienceManager-EnterpriseDistribution-7.2.3.0\tomcat\temp\content-integrity\default_2023_04_21-13_34_15_786-full.xlsx
 
#### <a name="jcr-integrity-configureCheck"></a>jcr:integrity-configureCheck   
Allows to configure a registered integrity check. Please note that for the moment, the configuration is reset when restarting the module implementing the check,
or when restarting the server.   
                         
**Options:**

| Name | alias          |    Value    | Mandatory | Multiple | Description                                                                                                                                         |
|------|----------------|:-----------:|:---------:|:--------:|-----------------------------------------------------------------------------------------------------------------------------------------------------|
| -id  |                |   string    |     x     |          | Specifies the identifier of the integrity check to configure                                                                                        |
| -e   | --enabled      | true, false |           |          | Enables the integrity check if `true`, disable it if `false`. Do not change the current status if not defined                                       |
| -p   | --param        |   string    |           |          | Name of the parameter to configure. Depends on the integrity check specified with `-id`. If no value is specified, the current value is printed out |
| -v   | --value        |   string    |           |          | Value of the parameter to configure. Depends on the parameter specified with `-p`. Depends on the integrity check specified with `-id`              |
| -rp  | --resetParam   |   string    |           |          | Name of the parameter to reset to its default value                                                                                                 |
| -pc  | --printConfigs |   string    |           |          | Print all the configurations of the specified check                                                                                                 |

**Example:**

    jahia@dx()> jcr:integrity-printChecks
    Integrity checks (11):
       FlatStorageCheck (id: 2, priority: 0.0, enabled: true)
       [...]
    jahia@dx()> jcr:integrity-configureCheck -id 2 -e false
    jahia@dx()> jcr:integrity-printChecks
    Integrity checks (11):
       FlatStorageCheck (id: 2, priority: 0.0, enabled: false)
       [...]
    jahia@dx()> jcr:integrity-configureCheck -id 2 -pc
    FlatStorageCheck:
        threshold = 500 (Number of children nodes beyond which an error is raised)
    jahia@dx()> jcr:integrity-configureCheck -id 2 -p threshold
    FlatStorageCheck: threshold = 500
    jahia@dx()> jcr:integrity-configureCheck -id 2 -p threshold -v 200
    FlatStorageCheck: threshold = 200
    jahia@dx()> jcr:integrity-configureCheck -id 2 -rp threshold
    FlatStorageCheck: threshold = 500

 
#### jcr:integrity-fix
Fixes some errors of a scan, using the fix provided by the check which has detected them. The errors are specified by their ID, as printed by `jcr:integrity-printTestResults`, or `*` for all the errors which are not fixed yet.

**Options:**

| Name | alias  | Value  | Mandatory | Multiple | Description                                                                      |
|------|--------|:------:|:---------:|:--------:|----------------------------------------------------------------------------------|
| -t   | --test | string |           |          | ID of the scan from which to load the errors. Latest scan used if not defined    |

**Example:**

    jahia@dx()> jcr:integrity-fix 3 7
    Fixed the error id=3
    Impossible to fix the error id=7
    jahia@dx()> jcr:integrity-fix *

Not every error type can be fixed automatically. When a check provides no fix for an error, the error is reported as impossible to fix.

## FAQ

### Scan interruption

You can interrupt a running scan from the UI, or by defining a system property named `modules.contentIntegrity.interrupt`. 
You can for example run the following script in a groovy console:

    System.setProperty("modules.contentIntegrity.interrupt", "true")

### Clustered environment

Most of the checks are purely related to the JCR, and do not need to be executed on every server in the cluster. The scan can be run on any server, not just the processing server.

The following checks rely on some local resources, and should be run on every server in a cluster:
* TemplatesIndexationCheck

In the UI, the checks to execute can be easily selected. If using the Karaf Shell, refer to [jcr:integrity-check](#jcrintegrity-check) to run a scan with a specified list of checks to execute.
