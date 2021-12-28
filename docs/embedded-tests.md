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

## BinaryPropertiesSanityCheck

Detects the properties of type `binary` for which the value can't be loaded. 

### Dealing with errors

If the number of errors is limited, it should be analyzed on a node by node basis.  
Usualy, the missing binary will be the value of the `jcr:data` property of the `jcr:content` subnode of a `jnt:file` node. In such case, you should first make the users aware of the issue, since they might be able to reupload the file or accept to delete the whole file node.

If the number of errors is important, then it is probably the consequence of an inappropriate system operation. Then, a generic solution should be evaluated.  
Since the binary data can be store either in the database or on the filesystem (choice done at Jahia installation time), the way to deal with the errors differs depending on the type of storage.

#### Database storage

With this storage type, there is no possibility to fix the issue directly in the database. If the binaries are missing as a consequence of a recent operation, then the Jahia environment should be restored from a backup taken before the faulty operation. If you are not sure of the time of the operation, and need to confirm that the backup to be restored will solve the issue, the only solution is to restore this backup on another environment and run an integrity check.  

If restoring the production from a backup is not an option, then you can restore the backup on another server and extract the missing binaries from this environment to fix the data on production. For nodes of type `jnt:file`, the files can be downloaded from this environment and reuploaded on production. To avoid breaking references to the files, do not delete/recreate the file. You can upload a new binary on an existing file node.  
If the production is clustered, this temporary environment can be simply restored as a standalone environment. 

#### Filesystem storage 

With this storage type, the errors can be the consequence of an incorrect manipulation on the filesystem in the `datastore` folder.  
It can also be faced when restoring a backup, if the backup was taken while Jahia was running, and the `full readonly mode` not enabled. The issue happens when a file is uploaded while the backup is taken, if the `datastore` folder is already saved at the time the file is uploaded, but the file node declaration written in the database is part of the database dump.

Since each binary property value is saved as a unique file in the `datastore` folder, the issue can be easily fixed by restoring the missing file, with the correct name and path. You can recover the missing files from a backup of the `datastore` folder and copy them onto the production filesystem. A binary file can be referenced by several properties, so the number of missing files can be lower than the number of errors.  
If the number of errors is important, you can also merge the `datastore` folder from your backup into the one of your production environment. Doing so, you might restore some useless binaries as well (e.g. referenced by no property). If you proceed that way, you can then run the `Datastore garbage collector` in the tools, to detect and purge those orphan binaries. 

## FlatStorageCheck

Detects the nodes having too many direct subnodes (500 by default, can be configured).   
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

It is preferable to explicitely flag the home page as such. Simply open the `repository explorer`, activate the display of the hidden properties, and check the option `j:isHomePage` on your home page.  
The page node should be republished to propagate the fix to the `live` workspace.

## JCRLanguagePropertyCheck

## LockSanityCheck

## MarkForDeletionCheck

## PropertyDefinitionsSanityCheck

## PublicationSanityDefaultCheck

## PublicationSanityLiveCheck

## SiteLevelSystemGroupsCheck

## TemplatesIndexationCheck

If a template is not correctly indexed, you can simply reindex it from the `JCR browser` in the tools. If several templates are identified, you can also reindex the whole subtree of the module.  
Then, you need to flush the cache, or at least the cache named `RenderService.TemplatesCache`.

If you suspect that the issue is not related to a module deployment error, then you should consider a full reindexation of the JCR.

## UndeclaredNodeTypesCheck

## UndeployedModulesReferencesCheck

## VersionHistoryCheck

## WipSanityCheck

