# verify-integrity
DX module that provides an extensible service to test the integrity of the content
* [How to use it](#how-to-use)
    * [jcr:integrity-check](#jcr-integrity-check) 
    * [jcr:integrity-printChecks](#jcr-integrity-printChecks) 
    * [jcr:integrity-printTestResults](#jcr-integrity-printTestResults) 
* [How to extend it](#how-to-extend) 

## <a name="how-to-use"></a>How to use?

### Basic usage
The content integrity service is available through the [Felix console](https://academy.jahia.com/documentation/digital-experience-manager/7.2/technical/configuration-and-fine-tuning/configuring#OSGi_SSH_Console).

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

##### Options:  
Name | alias | Mandatory | Value | Description
 --- | --- | :---: | :---: | ---
 -l | --limit | | positive integer, [20] | Specifies the maximum number of errors to print out
 
##### Examples:
    jcr:cd /sites/mySite/
    jcr:workspace live
    jcr:integrity-check -l 10   
      
#### <a name="jcr-integrity-printChecks"></a>jcr:integrity-printChecks 
Prints out the currently registered checks. 
                         
##### Options:
Name | alias | Mandatory | Value | Description
 --- | --- | :---: | :---: | ---
 -l | --outputLevel | | [simple] , full | Specifies the output level to use  
 
#### <a name="jcr-integrity-printTestResults"></a>jcr:integrity-printTestResults   
Allows to reprint the result of a previous test.   
                         
##### Options:
Name | alias | Mandatory | Value | Description
 --- | --- | :---: | :---: | ---
 -l | --limit | | positive integer, [20] | Specifies the maximum number of errors to print out
 -d | --dump , --dumpToCSV | | boolean, [false] | Dumps the errors into a CSV file in temp/content-integrity/ . The limit option is ignored when dumping
 -f | --showFixedErrors | | boolean, [false] | Coming soon 

**Example:**

    jahia@dx()> jcr:integrity-check
    Content integrity tested in 141 ms
    No error found
    jahia@dx()> jcr:integrity-printTestResults -d true
    Dumped into C:\DigitalExperienceManager-EnterpriseDistribution-7.2.3.0\tomcat\temp\content-integrity\2018_05_23-12_36_34_385-full.csv

 
#### jcr:integrity-fix
Coming soon    
 

## <a name="how-to-extend"></a>How to extend?

If you want to develop your own tests, for example in order to do some specific tests related to your own datamodel,
you can develop them in a custom module, and register them into the content integrity service along with the generic ones.

### pom.xml

You need to declare a dependency to the core module

    <dependencies>
        <dependency>
            <groupId>org.jahia.modules</groupId>
            <artifactId>verify-integrity</artifactId>
            <version>2.0</version>
            <scope>provided</scope>
        </dependency>
    </dependencies>
    
You need as well configure the BND plugin to scan the OSGi declarative services annotations

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
You have to write a java class to implement you custom check

    public class FailedSiteDeletionCheck extends AbstractContentIntegrityCheck {
    
        @Override
        public ContentIntegrityError checkIntegrityBeforeChildren(Node node) {
            try {
                JCRSessionFactory.getInstance().getCurrentSystemSession(Constants.LIVE_WORKSPACE, null, null).getNode(node.getPath());
            } catch (RepositoryException e) {
                return ContentIntegrityError.createError(node, null, "The site has been partially deleted", this);
            }
            return null;
        }
    }       
    
The most convenient way is to extend `AbstractContentIntegrityCheck`.
Then you will overwrite `checkIntegrityBeforeChildren(Node node)` and/or 
`checkIntegrityAfterChildren(Node node)`. 

Most of the time, you will implement implement `checkIntegrityBeforeChildren`. Implement `checkIntegrityAfterChildren` 
when you need to test the integrity of a node after having tested the integrity of its subtree.

If an integrity error is detected on the scanned node, return an instance of `ContentIntegrity`, `null` otherwise.

#### Registration into the service

In order to get you custom check registered into the content integrity service, you need to use the `@Component` annotation.
You need to specify as well the java interface that all integrity checks implement and the immediate
injection of your Component: `@Component(service = ContentIntegrityCheck.class, immediate = true`. 

**Example:**

    import org.jahia.modules.verifyintegrity.api.ContentIntegrityCheck;
    import org.osgi.service.component.annotations.Component;     
    
    @Component(service = ContentIntegrityCheck.class, immediate = true)
    public class FailedSiteDeletionCheck extends AbstractContentIntegrityCheck {
    
    
##### Priority #####

Each integrity check has a priority, and they are executed sequentially according to that priority, in ascending order. 
If an integrity check needs to be executed before/after some other, it is possible to ensure that, configuring a priority
on the integrity check.  
If not configured, the default priority will be used (100)

**Example:**

    @Component(service = ContentIntegrityCheck.class, immediate = true, property = {
            ContentIntegrityCheck.PRIORITY + ":Float=50"
    })

    
##### Execution Conditions #####

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

[TODO list of built in conditions]    

## Changelog
Version | Required DX version | Changes
------------ | -------------| -------------
2.0 | 7.2.0.0 | Initial version