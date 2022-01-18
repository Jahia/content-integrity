# <a name="summary"></a>Content Integrity - Embedded tests
* [How to use it](../README.md#summary)
* Embedded tests
  * [AceSanityCheck](#acesanitycheck)
  * [BinaryPropertiesSanityCheck](#binarypropertiessanitycheck)
  * [FlatStorageCheck](#flatstoragecheck)
  * [HomePageDeclaration](#homepagedeclaration)
  * [JCRLanguagePropertyCheck](#jcrlanguagepropertycheck)
  * [LockSanityCheck](#locksanitycheck)
  * [MarkForDeletionCheck](#markfordeletioncheck)
  * [PropertyDefinitionsSanityCheck](#propertydefinitionssanitycheck)
  * [PublicationSanityDefaultCheck](#publicationsanitydefaultcheck)
  * [PublicationSanityLiveCheck](#publicationsanitylivecheck)
  * [SiteLevelSystemGroupsCheck](#sitelevelsystemgroupscheck)
  * [TemplatesIndexationCheck](#templatesindexationcheck)
  * [UndeclaredNodeTypesCheck](#undeclarednodetypescheck)
  * [UndeployedModulesReferencesCheck](#undeployedmodulesreferencescheck)
  * [VersionHistoryCheck](#versionhistorycheck)
  * [WipSanityCheck](#wipsanitycheck)
* [How to extend it](how-to-extend.md#summary)
* [Release notes](release-notes.md#summary)

## AceSanityCheck

_work in progress_

## BinaryPropertiesSanityCheck

Detects the properties of type `binary` for which the value can't be loaded. 

### Dealing with errors

If the number of errors is limited, it should be analyzed on a node by node basis.  
Usually, the missing binary will be the value of the `jcr:data` property of the `jcr:content` sub-node of a `jnt:file` node. In such case, you should first make the users aware of the issue, since they might be able to re-upload the file or accept to delete the whole file node.

If the number of errors is important, then it is probably the consequence of an inappropriate system operation. Then, a generic solution should be evaluated.  
Since the binary data can be store either in the database or on the filesystem (choice done at Jahia installation time), the way to deal with the errors differs depending on the type of storage.

#### Database storage

With this storage type, there is no possibility to fix the issue directly in the database. If the binaries are missing as a consequence of a recent operation, then the Jahia environment should be restored from a backup taken before the faulty operation. If you are not sure of the time of the operation, and need to confirm that the backup to be restored will solve the issue, the only solution is to restore this backup on another environment and run an integrity check.  

If restoring the production from a backup is not an option, then you can restore the backup on another server and extract the missing binaries from this environment to fix the data on production. For nodes of type `jnt:file`, the files can be downloaded from this environment and re-uploaded on production. To avoid breaking references to the files, do not delete/recreate the file. You can upload a new binary on an existing file node.  
If the production is clustered, this temporary environment can be simply restored as a standalone environment. 

#### Filesystem storage 

With this storage type, the errors can be the consequence of an incorrect manipulation on the filesystem in the `datastore` folder.  
It can also be faced when restoring a backup, if the backup was taken while Jahia was running, and the `full readonly mode` not enabled. The issue happens when a file is uploaded while the backup is taken, if the `datastore` folder is already saved at the time the file is uploaded, but the file node declaration written in the database is part of the database dump.

Since each binary property value is saved as a unique file in the `datastore` folder, the issue can be easily fixed by restoring the missing file, with the correct name and path. You can recover the missing files from a backup of the `datastore` folder and copy them onto the production filesystem. A binary file can be referenced by several properties, so the number of missing files can be lower than the number of errors.  
If the number of errors is important, you can also merge the `datastore` folder from your backup into the one of your production environment. Doing so, you might restore some useless binaries as well (e.g. referenced by no property). If you proceed that way, you can then run the `Datastore garbage collector` in the tools, to detect and purge those orphan binaries. 

## FlatStorageCheck

Detects the nodes having too many direct sub-nodes (500 by default, can be configured).   
The JCR is not designed to handle flat storage, and such structure has an impact on the performance.

### Dealing with errors

You need to refactor your data model, usually splitting those nodes into some subtrees. One possible solution involves the Jahia built-in [auto split feature](https://academy.jahia.com/documentation/developer/jahia/8/advanced-guide-v8/manipulating-content-with-apis/jcr-api#Auto_splitting_nodes).

If the problem is related to regular nodes (as opposed to UGC), then it has to be fixed in the `default` workspace and then propagated to the `live` workspace through publication.

## HomePageDeclaration 

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

## JCRLanguagePropertyCheck

Translation nodes (nodes of type `jnt:translation`) are named after a naming convention. The node must be `j:translation_xx`, where `xx` is the language code. The node also holds a property named `jcr:language`, which has the language code as a value.  
The language code must be the same in the node name and the value of the property.

Example of valid translation node:

    Name: j:translation_en
    Path: /sites/digitall/home/about/area-main/rich-text/j:translation_en
    Type: jnt:translation
        
    Properties: 
        jcr:language:  en
        jcr:primaryType:  jnt:translation

### Dealing with errors

Errors are usually located under `/modules`, in a node part of a template. The root cause is usually an uncontrolled copy/paste in the `repository.xml` file. In such case, just fix the issue in the XML file, and redeploy the module.

Otherwise, you will need to fix the node in the JCR, updating the property value to match the node name.  
If the impacted nodes are automatically created, for example from some custom java code, it is important as well to identify & fix the faulty code, in order to avoid reintroducing the issue.

## LockSanityCheck

When a node is locked, several properties are written on it. When the lock is released, all those properties should be removed.

A node should have all those properties or none.

### Dealing with errors

First, check in the UI if the node is shown as locked. If it is, try to unlock it. Otherwise, try to lock it and then unlock it. Most of the time, this will be enough to clean the lock related properties on the node.

If the above actions fail, you will need to write a script to clean the node.

## MarkForDeletionCheck

When deleting a piece of content from the UI, the node is not technically deleted from the `default` workspace, but marked for deletion. This results in adding some mixins on the node as well as on its sub-nodes.
* `jmix:markedForDeletionRoot` is added on the node which you have deleted
* `jmix:markedForDeletion` is added on every node in the subtree, including the root node.

A node flagged as deleted, but without deletion root, is inconsistent. From the UI, you can trigger the publication or the undeletion of the tree only on the root node of the deletion. 

### Dealing with errors

If a few pieces of content are impacted, you can delete again the root node of the deletion from the UI. This will add back the missing mixins, allowing then to publish or undelete the tree, still from the UI.

If an important number of nodes are impacted, you will need to write a script to clean the mixins on the inconsistent nodes.

## PropertyDefinitionsSanityCheck

_work in progress_

## PublicationSanityDefaultCheck

When a node is flagged as published in the `default` workspace, then a node with the same identifier must exist in the `live` workspace.  
If the node has no pending modification, then the path must be the same in the two workspaces.

### Dealing with errors

If the node is flagged as published, but there's no live node with the same identifier, then you need to remove the property `j:published` from the node in the default workspace. Then you can republish the node if needed.

If the node has no pending modification but its path differs in the `live` workspace, then you can do a fake modification on the node (for example, adding some blank at the end of a richtext, or changing the value of a property, and then setting back the initial value) in order to get back the possibility to publish the node. 

## PublicationSanityLiveCheck

In the `live` workspace, UGC nodes are flagged as such. All the other nodes are expected to exist as well in the `default` workspace, with the same identifier.

### Dealing with errors

Errors require a case by case analysis.

If the node should be flagged as a UGC node, you need to set the property `j:originWS` to `live` on the node.

If the node is the remaining of a failed deletion, which has been completed only in the `default` workspace, then you need to delete the remaining `live` node.

If the node is incorrectly missing in `default`, you need to delete the `live` node, recreate the `default` node, and republish it.

## SiteLevelSystemGroupsCheck

A Jahia server must have a server level group named `privileged`, and each site must have a site level group named `site-privileged`.  
Each `site-privileged` group must be member of the `privileged` group.

### Dealing with errors

If a `site-privileged` group is not member of the `privileged` group, you need to restore this membership.

If the `privileged` group is missing, you will need to recreate it, and then add every `site-privileged` group as a member.

If the `site-privileged` group is missing, you will need to recreate it. Then, it will be required to identify the users/groups which need to be member of this group. 

## TemplatesIndexationCheck

If a template is not correctly indexed, you can simply reindex it from the `JCR browser` in the tools. If several templates are identified, you can also reindex the whole subtree of the module.  
Then, you need to flush the cache, or at least the cache named `RenderService.TemplatesCache`.

If you suspect that the issue is not related to a module deployment error, then you should consider a full re-indexation of the JCR.

## UndeclaredNodeTypesCheck

Each node has a primary type, and can have been added some mixin types. If a type assigned to a node can't be loaded, then you might encounter some issues when manipulating the node. 

### Dealing with errors

First, check the reason why some node types / mixin types are missing. The module which define them might be missing or not correctly installed. In such case, you need to restore the module in a correct state to fix the issue.

Otherwise, if the missing types have been decommissioned, then no node should refer to them anymore. 

**Primary type**: you will probably need to delete every related node. If you need to recover some data from the properties of the node, you will need to write a script to copy the values to a new node, of another type. It is not possible to change the primary type of a node. You might need to introduce back the removed node type definition for the good execution of the script, and then remove it forever.

**Mixin type**: you can remove the mixin from the nodes. If a few nodes are impacted, you can do it from the JCR browser. Otherwise, you will need to write a script. If the mixin used to define some properties, then you should clean them from the node as well. In such case, writing a script will be the best option. You might need to introduce back the removed mixin type definition for the good execution of the script, and then remove it forever. 

## UndeployedModulesReferencesCheck

When you enable a module on a site, then a property is written on the site node to track this information.  
Only currently deployed modules should be referred to in this list.  
                                                                   
### Dealing with errors

If the module should be deployed, then deploying it back on the server will fix the issue on the site.

If the module was undeployed for good reasons, then you need to clean the site node. You can easily achieve it from the Karaf shell.

    root@dx()> jcr:cd /sites/digitall
    root@dx()> jcr:prop-set -multiple -op remove j:installedModules myOldModule 

## VersionHistoryCheck

Detects the nodes having a too long linear version history (100 versions by default, can be configured).  
Such nodes will impact the performance.

You can purge the version history of every node in the tools, keeping only the most recent versions. Otherwise, you can write a script to purge the complete history of some specific nodes. Example:

```groovy
import org.jahia.services.history.NodeVersionHistoryHelper

NodeVersionHistoryHelper.purgeVersionHistoryForNodes(Collections.singleton(nodeIdentifier))
```

## WipSanityCheck

_work in progress_

