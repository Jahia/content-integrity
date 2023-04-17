# <a name="summary"></a>Content Integrity - How to extend
* [How to use it](../README.md#summary)
* [Embedded tests](embedded-tests.md#summary)
* [How to extend it](#how-to-extend)
  * [pom.xml](#pomxml)
  * [Implementation of the check](#implementation-of-the-check)
  * [Java code](#java-code)
  * [Registration into the service](#registration-into-the-service)
  * [Priority](#priority)
  * [Enabled](#enabled)
  * [Minimum Jahia version](#minimum-jahia-version)
  * [Execution conditions](#execution-conditions)
* [Release notes](release-notes.md#summary)

## <a name="how-to-extend"></a>How to extend?

If you want to develop your own tests, for example in order to do some specific tests related to your own data model,
you can develop them in a custom module, and register them into the content integrity service along with the generic ones.

### pom.xml

You need to declare a Maven dependency to the core module.

    <dependencies>
        <dependency>
            <groupId>org.jahia.modules</groupId>
            <artifactId>content-integrity</artifactId>
            <version>3.0</version>
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

#### Java code
You have to write a java class to implement your custom check.

    public class FailedSiteDeletionCheck extends AbstractContentIntegrityCheck {
    
        @Override
        public ContentIntegrityErrorList checkIntegrityBeforeChildren(JCRNodeWrapper node) {
            try {
                JCRUtils.getSystemSession(Constants.LIVE_WORKSPACE, false).getNode(node.getPath());
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

    ContentIntegrityCheck.ExecutionCondition.APPLY_ON_SUBTREES + "=/sites"

#### Apply if has properties / skip if has properties  

Specifies some properties which the node must have (or not have) to be checked. Several property names can be listed, comma separated.

    ContentIntegrityCheck.ExecutionCondition.APPLY_IF_HAS_PROP + "=j:workInProgress,j:workInProgressStatus"
