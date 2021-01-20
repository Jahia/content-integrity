# Content Integrity
Jahia module that provides an extensible service to test the integrity of the content
* [How to use it](#how-to-use)
    * [jcr:integrity-check](#jcr-integrity-check) 
    * [jcr:integrity-printChecks](#jcr-integrity-printChecks) 
    * [jcr:integrity-printTestResults](#jcr-integrity-printTestResults) 
    * [jcr:integrity-configureCheck](#jcr-integrity-configureCheck) 
* [How to extend it](#how-to-extend) 

## <a name="how-to-use"></a>How to use?

### Basic usage
The content integrity service is available through the [Karaf console](https://academy.jahia.com/documentation/digital-experience-manager/7.2/technical/configuration-and-fine-tuning/configuring#OSGi_SSH_Console).

Use `jcr:cd {path}` to position yourself on the node from which you want to start the scan.

Use `jcr:workspace {workspace}` if you want to change the workspace to scan.

Use `jcr:integrity-check` to run a content integrity test.

    jahia@dx()> jcr:cd /sites/mySite/
    /sites/mySite
    jahia@dx()> jcr:integrity-check
    Content integrity tested in 128 ms
    No error found
    jahia@dx()>
    
### Commands
#### <a name="jcr-integrity-check"></a>jcr:integrity-check  
Runs a scan of the current tree and current workspace.

**Options:**  

Name | alias | Value | Mandatory | Multiple | Description
 --- | --- | :---: | :---: | :---: | ---
 -l | --limit | positive integer, [20] | | | Specifies the maximum number of errors to print out
 -x | --exclude | string | | x | Specifies one or several subtrees to exclude
 
**Examples:**

    jcr:cd /sites/mySite/
    jcr:workspace live
    jcr:integrity-check -l 10   
    
    jcr:cd /sites
    jcr:integrity-check -x /sites/aHugeSite
    jcr:integrity-check -x /sites/aHugeSite -x /sites/anotherHugeSite/files 
      
#### <a name="jcr-integrity-printChecks"></a>jcr:integrity-printChecks 
Prints out the currently registered checks. 
                         
**Options:**

Name | alias | Value | Mandatory | Multiple | Description
 --- | --- | :---: | :---: | :---: | ---
 -l | --outputLevel | [simple] , full | | | Specifies the output level to use  
 
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

Name | alias | Value | Mandatory | Multiple | Description
 --- | --- | :---: | :---: | :---: | ---
 -l | --limit | positive integer, [20] | | | Specifies the maximum number of errors to print out
 -d | --dump , --dumpToCSV | | | | Dumps the errors into a CSV file in temp/content-integrity/ if used. The limit option is ignored when dumping
 -nh | --noCSVHeader | | | | Generates a CSV file without header. This option is ignored when not generating a CSV file
 -ef | --excludeFixedErrors | | | | Coming soon 

**Example:**

    jahia@dx()> jcr:integrity-check
    Content integrity tested in 141 ms
    No error found
    jahia@dx()> jcr:integrity-printTestResults -d
    Dumped into C:\DigitalExperienceManager-EnterpriseDistribution-7.2.3.0\tomcat\temp\content-integrity\2018_05_23-12_36_34_385-full.csv
 
#### <a name="jcr-integrity-configureCheck"></a>jcr:integrity-configureCheck   
Allows to configure a registered integrity check. Please note that for the moment, the configuration is reset when restarting the module implementing the check,
or when restarting the server.   
                         
**Options:**

Name | alias | Value | Mandatory | Multiple | Description
 --- | --- | :---: | :---: | :---: | ---
 -id |  | positive integer | x | | Specifies the identifier of the integrity check to configure
 -e | --enabled | true, false | | | Enables the integrity check if `true`, disable it if `false`. Do not change the current status if not defined 
 -p | --param | string | | | Name of the parameter to configure. Depends on the integrity check specified with `-id`. If no value is specified, the current value is printed out 
 -v | --value | string | | | Value of the parameter to configure. Depends on the parameter specified with `-p`. Depends on the integrity check specified with `-id` 
 -rp | --resetParam | string | | | Name of the parameter to reset to its default value 
 -pc | --printConfigs | string | | | Print all the configurations of the specified check 
 
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
Coming soon    
 

## <a name="how-to-extend"></a>How to extend?

If you want to develop your own tests, for example in order to do some specific tests related to your own data model,
you can develop them in a custom module, and register them into the content integrity service along with the generic ones.

### pom.xml

You need to declare a Maven dependency to the core module.

    <dependencies>
        <dependency>
            <groupId>org.jahia.modules</groupId>
            <artifactId>content-integrity</artifactId>
            <version>[2.0,3.0)</version>
            <scope>provided</scope>
        </dependency>
    </dependencies>

    <repositories>
        <repository>
            <id>jahia-ps-public</id>
            <url>https://devtools.jahia.com/nexus/content/repositories/jahia-professional-services-public-repository</url>
        </repository>
    </repositories>
    
You need to declare a Jahia level dependency to the core module. If your module is dedicated to content integrity extension,
then it is relevant to flag it as a system module as enabling it on a website would not carry any additional effect out.
    
    <properties>
        <jahia-depends>default,content-integrity</jahia-depends>
        <jahia-module-type>system</jahia-module-type>
    </properties>    
    
You need as well to configure the BND plugin to scan the OSGi declarative services annotations.

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.felix</groupId>
                <artifactId>maven-bundle-plugin</artifactId>
                <extensions>true</extensions>
                <configuration>
                    <instructions>
                        <_dsannotations>*</_dsannotations>
                    </instructions>
                </configuration>
            </plugin>
        </plugins>
    </build> 
    
### Implementation of the check

#### java code
You have to write a java class to implement your custom check.

    public class FailedSiteDeletionCheck extends AbstractContentIntegrityCheck {
    
        @Override
        public ContentIntegrityErrorList checkIntegrityBeforeChildren(JCRNodeWrapper node) {
            try {
                JCRSessionFactory.getInstance().getCurrentSystemSession(Constants.LIVE_WORKSPACE, null, null).getNode(node.getPath());
            } catch (RepositoryException e) {
                return createSingleError(node, "The site has been partially deleted");
            }
            return null;
        }
    }       
    
The most convenient way is to extend `AbstractContentIntegrityCheck`.
Then you will want to overwrite `checkIntegrityBeforeChildren(JCRNodeWrapper node)` and/or 
`checkIntegrityAfterChildren(JCRNodeWrapper node)`. 

Most of the time, you will implement `checkIntegrityBeforeChildren`. Implement `checkIntegrityAfterChildren` 
when you need to test the integrity of a node after having tested the integrity of its subtree.

If one (or several) integrity error is detected on the scanned node, return an instance of `ContentIntegrityErrorList`, `null` otherwise.

#### Registration into the service

In order to get your custom check registered into the content integrity service, you need to use the `@Component` annotation.
You need to specify as well the java interface that every integrity checks implement and the immediate
injection of your Component: `@Component(service = ContentIntegrityCheck.class, immediate = true`. 

**Example:**

    import org.jahia.modules.contentintegrity.api.ContentIntegrityCheck;
    import org.osgi.service.component.annotations.Component;     
    
    @Component(service = ContentIntegrityCheck.class, immediate = true)
    public class FailedSiteDeletionCheck extends AbstractContentIntegrityCheck {
    
    
#### Priority

Each integrity check has a priority, and they are executed sequentially according to that priority, in ascending order. 
If an integrity check needs to be executed before/after some other, it is possible to ensure that, configuring a priority
on the integrity check.  
If not configured, the default priority will be used (100)

**Example:**

    @Component(service = ContentIntegrityCheck.class, immediate = true, property = {
            ContentIntegrityCheck.PRIORITY + ":Float=50"
    })
    
    
#### Enabled

Each integrity check can be flaged as disabled. If not configured, the check will be enabled by default.
It can be relevant to disable by default a check that would time consuming. Such a check can be dynamically enabled
at any time, a faulty one can be disabled. See [jcr:integrity-configureCheck](#jcr-integrity-configureCheck)

**Example:**

    @Component(service = ContentIntegrityCheck.class, immediate = true, property = {
            ContentIntegrityCheck.ENABLED + ":Boolean=false"
    })
    
#### Minimum Jahia version

Each integrity check can declare a minimum Jahia version. If the Jahia version is lower than the declared one, the check will
be skipped at registration time. If not configured, the check will be registered no matter the version of the Jahia server.  

Two possibilities are offered:
- **ContentIntegrityCheck.ValidityCondition.APPLY_ON_VERSION_GTE** : runs on the specified version or newer
- **ContentIntegrityCheck.ValidityCondition.APPLY_ON_VERSION_GT** : runs on versions newer than the specified one

**Example**

    @Component(service = ContentIntegrityCheck.class, immediate = true, property = {
            ContentIntegrityCheck.ValidityCondition.APPLY_ON_VERSION_GTE + "=7.2.3.1"
    })
    public class WipSanityCheck extends AbstractContentIntegrityCheck {         

    
#### Execution Conditions

If you don't want your check to be run on every node, then you can define some execution conditions, as some properties
of the component.                     

**Example:**

    import org.jahia.api.Constants;
    
    @Component(service = ContentIntegrityCheck.class, immediate = true, property = {
            ContentIntegrityCheck.ExecutionCondition.APPLY_ON_WS + "=" + Constants.EDIT_WORKSPACE,
            ContentIntegrityCheck.ExecutionCondition.APPLY_ON_NT + "=" + Constants.JAHIANT_VIRTUALSITE,
            ContentIntegrityCheck.ExecutionCondition.APPLY_ON_SUBTREES + "=" + "/sites"
    })
    public class FailedSiteDeletionCheck extends AbstractContentIntegrityCheck {

##### Apply on node types / skip on node types

Specifies on which node types the check has to be executed. Several node types can be listed, comma separated.

    ContentIntegrityCheck.ExecutionCondition.APPLY_ON_NT + "=" + Constants.JAHIANT_VIRTUALSITE  

##### Apply on workspace / skip on workspace

Specifies on which workspace the check has to be executed.

    ContentIntegrityCheck.ExecutionCondition.APPLY_ON_WS + "=" + Constants.EDIT_WORKSPACE   

##### Apply on subtrees / skip on subtrees

Specifies on which subtrees the check has to be executed. The root node of a specified subtree is checked. Several subtrees can be listed, comma separated.

    ContentIntegrityCheck.ExecutionCondition.APPLY_ON_SUBTREES + "=" + "/sites"
    
    

## Changelog
Version | Required Jahia version | Changes
------------ | -------------| -------------
2.0 | 7.2.0.0 | Initial version
2.1 | 7.2.0.0 | Implemented BinaryPropertiesSanityCheck
2.2 | 7.2.0.0 | Implemented TemplatesIndexationCheck
2.3 | 7.2.0.0 | Implemented a sanity check for the "work in progress" related properties<br>Implemented the possibility to define a minimum Jahia version for an integrity check<br>[bugfix] Fixed the execution conditions on MarkedForDeletionCheck
2.4 | 7.2.0.0 | Implemented a sanity check on the system groups 
2.5 | 7.2.0.0 | Implemented an option to exclude a subtree from the scan<br>Implemented an "estimated time of arrival".
2.6 | 7.2.0.0 | [bugfix] Move operation that can't be published: do not test on the translation nodes<br>Added an optional header to the CSV dump (generated by default)
2.7 | 7.3.0.0 | Added the possibility to configure some integrity checks<br>Implemented a sanity check to detect nodes with too many versions
2.8 | 7.3.0.0 | Implemented a sanity check on the ACE nodes, in order to detect ACE related to deleted users or groups<br>[bugfix] Fixed the calculation of the number of nodes to scan when scanning the whole repository