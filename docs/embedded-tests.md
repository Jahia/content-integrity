# <a name="summary"></a>Content Integrity - Embedded tests
* [How to use it](../README.md#summary)
* Embedded tests
  * [AceSanityCheck](#acesanitycheck)
  * [BinaryPropertiesSanityCheck](#binarypropertiessanitycheck)
  * [ChildNodeDefinitionsSanityCheck](#childnodedefinitionssanitycheck)
  * [FlatStorageCheck](#flatstoragecheck)
  * [HomePageDeclarationCheck](#homepagedeclarationcheck)
  * [JCRLanguagePropertyCheck](#jcrlanguagepropertycheck)
  * [LockSanityCheck](#locksanitycheck)
  * [MarkForDeletionCheck](#markfordeletioncheck)
  * [NodeNameInfoSanityCheck](#nodenameinfosanitycheck)
  * [PagesSanityCheck](#pagessanitycheck)
  * [PropertyDefinitionsSanityCheck](#propertydefinitionssanitycheck)
  * [PublicationSanityDefaultCheck](#publicationsanitydefaultcheck)
  * [PublicationSanityLiveCheck](#publicationsanitylivecheck)
  * [ReferencesSanityCheck](#referencessanitycheck)
  * [SiteLevelSystemGroupsCheck](#sitelevelsystemgroupscheck)
  * [StaticInternalLinksCheck](#staticInternalLinksCheck)
  * [TemplatesIndexationCheck](#templatesindexationcheck)
  * [UndeclaredNodeTypesCheck](#undeclarednodetypescheck)
  * [UndeployedModulesReferencesCheck](#undeployedmodulesreferencescheck)
  * [UserAccountSanityCheck](#useraccountsanitycheck)
  * [VersionHistoryCheck](#versionhistorycheck)
  * [VersionsSanityCheck](#versionsanitycheck)
  * [WipSanityCheck](#wipsanitycheck)
  * [WorkspaceSpecificDefinitionsCheck](#workspacespecificdefinitionscheck)
* [How to extend it](how-to-extend.md#summary)
* [Groovy scripts](groovy-scripts.md#summary)
* [Release notes](release-notes.md#summary)

Fixing integrity issues can be a complex operation. This page will help you to deal with the various issues you might identify, giving a general way to solve those issues. But some project specificities might need to be considered to build a tailored made answer. If you need some assistance, do not hesitate to reach out to the [Jahia Professional Services Team](services@jahia.com).

## AceSanityCheck

_work in progress_

## BinaryPropertiesSanityCheck

Detects the properties of type `binary` for which the value can't be loaded. 

### Configuration

| Name                      |  Type   | Default Value | Description                                                                                                                                                                               |
|---------------------------|:-------:|:-------------:|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| download-stream           | boolean |     false     | If `true`, each binary property is validated by reading its value as a stream (time consuming operation). Otherwise, only the length of the binary is read                                |
| accept-zero-byte-binaries | boolean |     true      | If `true`, the binary properties with a valid zero byte length value will not be reported as errors. Otherwise, the binary is considered as valid only if its length is greater than zero |

### Dealing with errors

If the number of errors is limited, it should be analyzed on a node by node basis.  
Usually, the missing binary will be the value of the `jcr:data` property of the `jcr:content` sub-node of a `jnt:file` node. In such case, you should first make the users aware of the issue, since they might be able to re-upload the file or accept to delete the whole file node.

If the number of errors is important, then it is probably the consequence of an inappropriate system operation. Then, a generic solution should be evaluated.  
Since the binary data can be store either in the database or on the filesystem (choice done at Jahia installation time), the way to deal with the errors differs depending on the type of storage.

#### Database storage

With this storage type, there is no possibility to fix the issue directly in the database. If the binaries are missing as a consequence of a recent operation, then the Jahia environment should be restored from a backup taken before the faulty operation. If you are not sure of the time of the operation, and need to confirm that the backup to be restored will solve the issue, the only solution is to restore this backup on another environment and run an integrity check there.  

If restoring the production from a backup is not an option, then you can restore the backup on another server and extract the missing binaries from this environment to fix the data on production. For nodes of type `jnt:file`, the files can be downloaded from this environment and re-uploaded on production. To avoid breaking references to the files, do not delete/recreate the file. You can upload a new binary on an existing file node.  
If the production is clustered, this temporary environment can be simply restored as a standalone environment. 

#### Filesystem storage 

With this storage type, the errors can be the consequence of an incorrect manipulation on the filesystem in the `datastore` folder.  
It can also be faced when restoring a backup, if the backup was taken while Jahia was running, and the `full readonly mode` not enabled. The issue happens when a file is uploaded while the backup is taken, if the `datastore` folder is already saved at the time the file is uploaded, but the file node declaration written in the database is part of the database dump.

Since each binary property value is saved as a unique file in the `datastore` folder, the issue can be easily fixed by restoring the missing file, with the correct name and path. You can recover the missing files from a backup of the `datastore` folder and copy them onto the production filesystem. A binary file can be referenced by several properties, so the number of missing files can be lower than the number of errors.  
If the number of errors is important, you can also merge the `datastore` folder from your backup into the one of your production environment. Doing so, you might restore some useless binaries as well (e.g. referenced by no property). If you proceed that way, you can then run the `Datastore garbage collector` in the tools, to detect and purge those orphan binaries. 

## ChildNodeDefinitionsSanityCheck

Detects the nodes which are not allowed by the definition of their parent node.

### Dealing with errors

Usually, such issue appears after a modification of some definitions. If the name of the child node has been modified in the definitions, then the related nodes should be renamed accordingly in the JCR.

If the child node is of a type that is not allowed anymore by its parent, then such node should be deleted or moved to a new parent.

## FlatStorageCheck

Detects the nodes having too many direct sub-nodes (500 by default, can be configured).   
The JCR is not designed to handle flat storage, and such structure has an impact on the performance.

### Configuration

| Name      |       Type        | Default Value  | Description                                              |
|-----------|:-----------------:|:--------------:|----------------------------------------------------------|
| threshold | positive integer  |      500       | Number of children nodes beyond which an error is raised |

### Dealing with errors

You need to refactor your data model, usually splitting those nodes into some subtrees. One possible solution involves the Jahia built-in [auto split feature](https://academy.jahia.com/documentation/developer/jahia/8/working-with-our-apis/manipulating-content-with-apis/using-the-jcr-api#autosplitting-nodes).

If the problem is related to regular nodes (as opposed to UGC), then it has to be fixed in the `default` workspace and then propagated to the `live` workspace through publication.

## HomePageDeclarationCheck 

Each site must have a unique home page node.  
The home page node is a node of type `jnt:page`, with a boolean property named `j:isHomePage` having `true` as a value, and is a direct child node of the site node. If there's no node matching those conditions, but the site has a direct child node of type `jnt:page` named `home`, then this one will be used as the home page of the site.  

### Dealing with errors

Since the home page is usually defined by the template set, you should also review this one, all the more if you see the error appear again every time you create a new site. 

#### No home page

You need to decide which page has to be used as the home page. Then, simply open the `repository explorer`, activate the display of the hidden properties, and check the option `j:isHomePage` on your desired home page.  
The page node should be republished to propagate the fix to the `live` workspace. 

The issue can have been introduced by moving the home page under another one, so that it is not anymore a direct child of the site node. You can check that by running the below SQL-2 query. If a page is identified, you need to decide if you prefer moving it back under the site, or if you prefer to keep this page at its current location. In such case, you should uncheck the property `j:isHomePage`

    select * from [jnt:page] where isdescendantnode('/sites/mysite') and [j:isHomePage]='true'

#### Multiple home pages

You need to decide which page has to be used as the home page. Then, simply open the `repository explorer`, activate the display of the hidden properties, and uncheck the option `j:isHomePage` on every page wrongly flagged as the home page.  
Every updated page node should be republished to propagate the fix to the `live` workspace.

#### Fallback on the page named `home`

It is preferable to explicitly flag the home page as such. Simply open the `repository explorer`, activate the display of the hidden properties, and check the option `j:isHomePage` on your home page.  
The page node should be republished to propagate the fix to the `live` workspace.

If no page is flagged as home, but a direct sub-node of the site node is named `home`, then it is critical to flag a page as the home.

## JCRLanguagePropertyCheck

Translation nodes (nodes of type `jnt:translation`) are named after a naming convention. The node must be named `j:translation_xx`, where `xx` is the language code. The node also holds a property named `jcr:language`, which has the language code as a value.  
The language code must be the same in the node name and the value of the property.

Example of valid translation node:

    Name: j:translation_en
    Path: /sites/digitall/home/about/area-main/rich-text/j:translation_en
    Type: jnt:translation
        
    Properties: 
        jcr:language:  en
        jcr:primaryType:  jnt:translation

### Dealing with errors

Errors are usually located under `/modules`, in a node being part of a template. The root cause is usually an uncontrolled copy/paste in the `repository.xml` file. In such case, just fix the issue in the XML file, and redeploy the module.

Otherwise, you will need to fix the node in the JCR, updating the property value to match the node name.  
If the impacted nodes are automatically created, for example from some custom java code, it is important as well to identify & fix the faulty code, in order to avoid reintroducing the issue.

## LockSanityCheck

Detects the inconsistencies related to the JCR locks.

### Dealing with errors

#### Inconsistent lock

`Error code: INCONSISTENT_LOCK`

When a node is locked, several properties are written on it. When the lock is released, all those properties should be removed.  
A node should have all those properties or none.

First, check in the UI if the node is shown as locked. If it is, try to unlock it. Otherwise, try to lock it and then unlock it. Most of the time, this will be enough to clean the lock related properties on the node.

If the above actions fail, you will need to write a script to clean the node.

#### Remaining deletion lock on a translation node

`Error code: DELETION_LOCK_ON_I18N`

When a node is marked for deletion, a lock is set on the node, and its translation sub-nodes as well. If the node is undeleted, all those locks are supposed to be removed.  
This error is raised when a deletion lock is detected on a translation node, but the parent node is not marked for deletion.

To clear this lock, open the page composer in the language of the detected translation node, delete the piece of content, and undelete it. 

## MarkForDeletionCheck

When deleting a piece of content from the UI, the node is not technically deleted from the `default` workspace, but marked for deletion. This results in adding some mixins on the node as well as on its sub-nodes.
* `jmix:markedForDeletionRoot` is added on the node which you have deleted
* `jmix:markedForDeletion` is added on every node in the subtree, including the root node.

A node flagged as deleted, but without deletion root, is inconsistent. From the UI, you can trigger the publication or the undeletion of the tree only on the root node of the deletion. 

### Dealing with errors

If a few pieces of content are impacted, you can delete again the root node of the deletion from the UI. This will add back the missing mixins, allowing then to publish or undelete the tree, still from the UI.

If an important number of nodes are impacted, you will need to write a script to clean the mixins on the inconsistent nodes.

## NodeNameInfoSanityCheck

Each node has a name and a path. This information is copied to properties on the nodes: `j:nodename` and `j:fullpath`.
Renaming or moving a node, or one of its parents updates one or both properties. They are supposed to be automatically synchronized.

The property `j:fullpath` is deprecated and will be removed from Jahia in a future version. 
- If your code leverages this property, you should rewrite it. But you might want to enable the validation of the property in the meantime
- Otherwise, you should not enable its validation, since no feature in the product leverages it, and so an invalid value has no consequence 

### Configuration

| Name           |  Type   | Default Value | Description                                     |
|----------------|:-------:|:-------------:|-------------------------------------------------|
| check-fullpath | boolean |     false     | If `true`, the property `j:fullpath` is checked |

### Dealing with errors

#### Missing j:fullpath property

`Error code: MISSING_FULLPATH`

**Description**: `j:fullpath` is managed by the publication process. Therefore, it must be missing in the workspace `default` only on nodes which have never been published, and in the workspace `live` only on UGC nodes. But it must be present on every node which has been published at least once (no matter the current publication status).

Republishing the node should be enough to add the missing property to the node, in `default` and `live`.

#### Unexpected j:fullpath property

`Error code: UNEXPECTED_FULLPATH`

**Description**: `j:fullpath` is managed by the publication process. Therefore, it must be missing in the workspace `default` on nodes which have never been published, and in the workspace `live` on UGC nodes.

Fixing this error is not critical, but identifying when and how this property has been created is important.
The property can be deleted from the node to fix the error.

#### Invalid j:fullpath property

`Error code: INVALID_FULLPATH`

**Description**: `j:fullpath` is managed by the publication process. Therefore, it must have as a value the path of the node at the time of the last publication. As a consequence, in each workspace the value must match the current path of the node in the `live` workspace.

Republishing the node should be enough to fix the value of the property, in `default` and `live`.

#### Missing j:nodename property

`Error code: MISSING_NODENAME`

**Description**: `j:nodename` is set when the node is created or renamed. It should be defined on any node.

In the `default` workspace, renaming the node and then renaming it back to its current name should be sufficient to fix the error.
Then, the node needs to be republished to propagate the fix to the `live`, if relevant.
On UGC nodes, the property needs to be added using a script.

#### Invalid j:nodename property

`Error code: INVALID_NODENAME`

**Description**: `j:nodename` is set when the node is created or renamed. It should always be consistent with the node name.

In the `default` workspace, renaming the node and then renaming it back to its current name should be sufficient to fix the error.
Then, the node needs to be republished to propagate the fix to the `live`, if relevant.
On UGC nodes, the property needs to be modified using a script.

## PagesSanityCheck

### Dealing with errors

#### Missing template

`Error code: MISSING_TEMPLATE`

**Description**: a page is using a template which can't be resolved from the various modules enabled on the site.

If the template is implemented in a module which is mistakenly missing, then installing this module back will fix the error.  
Otherwise, the page has to be edited to select another template. Note: the new template might use some different areas, and those related to the former template might need to be renamed or deleted.

## PropertyDefinitionsSanityCheck

Checks the validity of the content against the property definitions. 

The content must fit the definitions at the time it is written. But if the definitions are updated afterwards, then some pieces of content might not fit the new definitions. This can impact some features, such as updating some existing content through the UI, publishing, importing, ...

### Configuration

| Name                  |  Type   | Default Value | Description                                                                                                   |
|-----------------------|:-------:|:-------------:|---------------------------------------------------------------------------------------------------------------|
| site-langs-only       | boolean |     false     | If true, only the translation sub-nodes related to an active language are checked when the node is in a site  |
| check-node-validators | boolean |     true      | If false, the node validators are not checked (property constraints are checked no matter this configuration) |

### Dealing with errors
                                                                 
#### Missing mandatory property

`Error code: EMPTY_MANDATORY_PROPERTY`

**Description**: A property is flagged as `mandatory` in the definitions, but has been detected with no value.
                                     
If a few nodes are impacted, you should provide the list of nodes to the editors, and ask them to fill the invalid properties. Content should be updated in the `default` workspace, and the errors should be fixed in `live` through publication.

If an important number of nodes are impacted, then you should consider writing a script. The main difficulty is to define the appropriate value to set on those properties, without altering the output of the pages (a unique value might not be enough to cover all the cases). If the definitions file declares a default value, then this one is usually a good candidate, but you should nevertheless evaluate if switching from a `null` value to this default value will have an impact on the pages output.  
The script should be run with the listeners deactivated, and should be run on each workspace.

#### Invalid value type

`Error code: INVALID_VALUE_TYPE`

**Description**: A property has been detected with a value that does not match the declared type for the property.

Changing the type of a property is not a supported operation.  

Nevertheless, it is something pretty usual during the initial development phase. In such case, you should consider deleting every node based on the type holding your property, in each workspace, and recreate some fresh test content, based on the updated definitions.  
                                                                        
If you need to change the type of a property on some production content, you should make the property `hidden` (and not indexed if of type `string`), and declare a new property of the desired type. Then, if you need to recover the content from the former property, you will need to write a script to copy the value (after a conversion if required).  
The script should be run with the listeners deactivated, and should be run on each workspace.

#### Invalid single/multi valued status

`Error code: INVALID_MULTI_VALUE_STATUS`

**Description**: A property has been detected with a value that does not match the declared single-value/multi-value type for the property.

Changing the type of a property is not a supported operation.

Nevertheless, it is something pretty usual during the initial development phase. In such case, you should consider deleting every node based on the type holding your property, in each workspace, and recreate some fresh test content, based on the updated definitions.

If you need to change the type of a property on some production content, you should make the property `hidden` (and not indexed if of type `string`), and declare a new property of the desired type. Then, if you need to recover the content from the former property, you will need to write a script to copy the value (after a conversion if required).  
The script should be run with the listeners deactivated, and should be run on each workspace.

#### Node validator constraint violation 

`Error code: INVALID_NODE_VALIDATION`

**Description**: A node validator raises a constraint violation when validating the node.

The value of the properties have to be updated to get rid of the validation errors.

**Warning**: Those validators are not limited to validate a single property. A non internationalized property can be validated along with an internationalized one, and as a consequence the result of the validation might differ from one language to another one, even though the value of the non internationalized property remains the same. As a consequence, non internationalized properties are validated for each available language. This will usually lead to duplicated errors. Fixing it once will of course remove all the occurrences of error on the next scan. 

_work in progress_

## PublicationSanityDefaultCheck

### Dealing with errors

#### No live node

`Error code: NO_LIVE_NODE`

**Description**: When a node is flagged as published in the `default` workspace or is of type `jmix:autoPublish`, then a node with the same identifier must exist in the `live` workspace.  

If the node is flagged as published, but there's no live node with the same identifier, then you need to remove the property `j:published` from the node in the default workspace. Then you can republish the node if needed.

If the node is of type `jmix:autoPublish`, but there's no live node with the same identifier, then you can do any modification on the node in `default`, what should trigger the publication of the node.

#### Different path in live

`Error code: DIFFERENT_PATH, DIFFERENT_PATH_POTENTIAL_FP`

**Description**: If the node has no pending modification, then the path must be the same in the two workspaces.

If the node has no pending modification but its path differs in the `live` workspace, then you can do a fake modification on the node (for example, adding some blank at the end of a richtext, or changing the value of a property, and then setting back the initial value) in order to get back the possibility to publish the node.

#### Live node with the same path but a different uuid

`Error code: PATH_CONFLICT`

**Description**: A live has the same path but a different uuid. As a consequence, publishing the current node is not possible.

Errors require a case by case analysis.

If the `live` node is a UGC node: either the `live` node or the `default` node has to be renamed to not conflict anymore.

If the `live` node is not a UGC node, it must have a different path in the `default` workspace. Usually, such conflict is a consequence of a renaming or move operation which has not been published yet. Publishing this second node will update its path in `live`, ending the conflict up. 

## PublicationSanityLiveCheck

In the `live` workspace, nodes are either published nodes or UGC nodes.   
UGC nodes are flagged as such. All the other nodes are expected to exist as well in the `default` workspace, with the same identifier.  
For the published nodes, if the node with the same identifier in the `default` workspace has no pending modification, then the various properties set on the nodes must have the same value in the two workspaces (optional check).

### Configuration

| Name                                 |  Type   | Default Value | Description                                                                                                                                                                               |
|--------------------------------------|:-------:|:-------------:|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| deep-compare-published-nodes         | boolean |     false     | If true, the value of every property will be compared between default and live on the nodes without pending modification                                                                  |
| report-live-only-undefined-ugc-nodes | boolean |     false     | If true, the nodes which exist only in live, and for which we don't track if they are UGC nodes, will be reported as errors. Most of those errors should be considered as false positives |

### Dealing with errors

#### Missing node in the `default` workspace for a non UGC node

`Error code: NO_DEFAULT_NODE`

**Description**: The node is not flagged as UGC, but no node exists in the `default` workspace with the same identifier

Errors require a case by case analysis.

If the node should be flagged as a UGC node, you need to set the property `j:originWS` to `live` on the node.

If the node is the remaining of a failed deletion, which has been completed only in the `default` workspace, then you need to delete the remaining `live` node.

If the node is incorrectly missing in `default`, you need to delete the `live` node, recreate the `default` node, and republish it.

_work in progress_

## ReferencesSanityCheck

When properties of type `weakreference` or `reference` have a value, this value is a reference to another node in the same workspace.
Properties of type `weakreference` can hold a broken reference by design (this should not be possible with `reference`), mostly when you delete a node which is referenced from another one.

### Configuration

| Name                     |  Type   | Default Value | Description                                                                                                                                                  |
|--------------------------|:-------:|:-------------:|--------------------------------------------------------------------------------------------------------------------------------------------------------------|
| validate-refs            | boolean |     true      | If true, the value of every property of type `reference` / `weakreference` will be inspected                                                                 |
| validate-back-refs       | boolean |     false     | If true, the references pointing to the current node will be evaluated. This operation is time consuming and will increase the scan duration                 |
| validate-version-history | boolean |     false     | If true, the references used to link the node to its version history will be evaluated. This operation is time consuming and will increase the scan duration |

### Dealing with errors

#### Broken references

`Error code: BROKEN_REF`

**Description**: the property has a not `null` value, but the referenced node is missing.

If the property is single-valued, then its value should be set to null, or another node should be referenced. If the property is mandatory, only the latter option is possible.

If the property is multi-valued, the invalid value should be removed from the list if some other values are valid, or the values should be updated.

`BROKEN_REF_TO_VN`

**Description**: the missing referenced node is a virtual node.

If the virtual node is temporarily missing, for example because of a connectivity issue at `DataSource` level, the error has to be considered as false positive.

If the virtual node is gone forever, then the property should be fixed the same way as for a regular node.

#### Invalid back references

`Error code: INVALID_BACK_REF`

**Description**: the node is referenced by a node which is missing.

This can happen only when the node holding the reference is a virtual node, the property is not virtual, and this node is not exposed by the `DataSource` at the time of the scan.

If the virtual node is temporarily missing, for example because of a connectivity issue at `DataSource` level, the error has to be considered as false positive.

If the virtual node is gone forever, then the extension node should be deleted from the JCR, what will clean the reference as well.

## SiteLevelSystemGroupsCheck

A Jahia server must have a server level group named `privileged`, and each site must have a site level group named `site-privileged`.  
Each `site-privileged` group must be member of the `privileged` group.

### Dealing with errors

If a `site-privileged` group is not member of the `privileged` group, you need to restore this membership.

If the `privileged` group is missing, you will need to recreate it, and then add every `site-privileged` group as a member.

If the `site-privileged` group is missing, you will need to recreate it. Then, it will be required to identify the users/groups which need to be member of this group. 

## StaticInternalLinksCheck

In the rich text editor, one should use the "link" feature to create internal links, select a page, and let the software create the internal link correctly.
But it happens that users copy the URL of a page from their browser's address bar, and paste it in the editor. As a consequence, the internal link is not considered as such, and is handled as an external link. As a consequence, the link gets broken if the target page is renamed or moved. And no warning is displayed if one deletes the target page.

This check iterates the properties of type `string` and reports any occurrence of a domain declared on one of the websites (primary domain or alternative one).

This check is disabled by default since the effect of the reported errors has a functional impact more than is a technical inconsistency, and because there's a high risk of false positives. 

### Configuration

| Name                     |  Type   | Default Value | Description                                                          |
|--------------------------|:-------:|:-------------:|----------------------------------------------------------------------|
| ignore-localhost         | boolean |     true      | If true, only the domains different from `localhost` will be scanned |

### Dealing with errors

If a reported error is qualified as legit, then the content has to be fixed manually and republished if the error is present in live as well.

## TemplatesIndexationCheck

If a template is not correctly indexed, you can simply reindex it from the `JCR browser` in the tools. If several templates are identified, you can also reindex the whole subtree of the module.  
Then, you need to flush the cache, or at least the cache named `RenderService.TemplatesCache`.

If you suspect that the issue is not related to a module deployment error, then you should consider a full re-indexation of the JCR.

## UndeclaredNodeTypesCheck

Each node has a primary type, and can have been added some mixin types. If a type assigned to a node can't be loaded, then you might encounter some issues when manipulating the node.

### Dealing with errors

`Error code: UNDECLARED_NODE_TYPE`

**Description**: the node type is missing from the JCR.

First, check the reason why some node types / mixin types are missing. The module which defines them might be missing or not correctly installed. In such case, you need to restore the module in a correct state to fix the issue.

Otherwise, if the missing types have been decommissioned, then no node should refer to them anymore.

**Primary type**: you will probably need to delete every related node. If you need to recover some data from the properties of the node, you will need to write a script to copy the values to a new node, of another type. It is not possible to change the primary type of a node. You might need to introduce back the removed node type definition for the good execution of the script, and then remove it forever.

**Mixin type**: you can remove the mixin from the nodes. If a few nodes are impacted, you can do it from the JCR browser. Otherwise, you will need to write a script. If the mixin used to define some properties, then you should clean them from the node as well. In such case, writing a script will be the best option. You might need to introduce back the removed mixin type definition for the good execution of the script, and then remove it forever.

`Error code: GHOST_NODE_TYPE`

**Description**: the node is still declared in the JCR, but is missing from the definitions file of the module which is supposed to declare it.

If the type has actually been deleted, in addition to the cleanup to be done on the JCR nodes (see `Error code: UNDECLARED_NODE_TYPE`), the type has to be purged from the `NodeTypeRegistry`. This can be achieved manually in the tools, using the "Definitions browser" (warning: this deletes the related nodes as well).

If the type has been moved from a module to another one, but is still bound to the first one, then the type has to be purged from the `NodeTypeRegistry`, and the second module has to be reinstalled to redeclare the type, but bound to the correct module. In this case, the related nodes are likely to be preserved. As a consequence, prefer using a Groovy script to undeclare the node type from the `NodeTypeRegistry`:

```
NodeTypeRegistry.getInstance().unregisterNodeType("mynt:movedType");
```

## UndeployedModulesReferencesCheck

When you enable a module on a site, then a property is written on the site node to track this information.  
Only currently deployed modules should be referred to in this list.  
                                                                   
### Dealing with errors

If the module should be deployed, then deploying it back on the server will fix the issue on the site.

If the module was undeployed for good reasons, then you need to clean the site node. You can easily achieve it from the Karaf shell.

    root@dx()> jcr:cd /sites/digitall
    root@dx()> jcr:prop-set -multiple -op remove j:installedModules myOldModule 

## UserAccountSanityCheck

Detect the cased when the user is not granted the role `owner` on his own account

### Dealing with errors
                             
User accounts are auto published. If the error is detected on the same user node in both workspaces, then fixing it in the `default` workspace will actually fix the issue in both.

You can run a groovy script to grant the missing role:

```
def userName = "john";
def site = "digitall";
def userNode = JahiaUserManagerService.getInstance().lookupUser(userName, site);
Set roles = ["owner"];
userNode.grantRoles("u:" + userName, roles);
session.save(); 
```

## VersionHistoryCheck

Detects the nodes having a too long linear version history (100 versions by default, can be configured).  
Such nodes will impact the performance.

### Configuration

| Name      |       Type        | Default Value | Description                                                           |
|-----------|:-----------------:|:-------------:|-----------------------------------------------------------------------|
| threshold | positive integer  |      100      | Number of versions for a single node beyond which an error is raised  |

### Dealing with errors

You can purge the version history of every node in the tools, keeping only the most recent versions. Otherwise, you can write a script to purge the complete history of some specific nodes. 

Example:

```groovy
import org.jahia.services.history.NodeVersionHistoryHelper

NodeVersionHistoryHelper.purgeVersionHistoryForNodes(Collections.singleton(nodeIdentifier))
```

## VersionsSanityCheck
    
Analyzes the versions tree, and checks the consistency of the version nodes.  
The versions tree is not scanned by default. To have this test executed, the scan root node must be `/jcr:system`, `/jcr:system/jcr:versionStorage` or any other node under.  
In addition to the identified errors, the check will compute some statistics about the scanned tree. To analyze a specific subtree of the version storage, just run the scan from this tree.

## WipSanityCheck

The `work in progress` feature has been redesigned in Jahia 7.2.3.1 .  
As a consequence, the data model of the technical information stored on the nodes flagged as `work in progress` has been updated. If your project has started before Jahia 7.2.3.1 , the data related to the `work in progress` is supposed to have been converted to the new model while migrating, and no property related to the legacy model should remain on your nodes.

### Dealing with errors

If some properties related to the legacy model are identified on some nodes, you should write a script to clean them up. Those properties are completely ignored after the refactoring, so deleting them will have no functional impact.

## WorkspaceSpecificDefinitionsCheck

_work in progress_
