# <a name="summary"></a>Content Integrity - Embedded tests
* [How to use it](../README.md#summary)
* [Embedded tests](embedded-tests.md#summary)
* [How to extend it](how-to-extend.md#summary)
* Groovy scripts
  * [scanRefsToSiteContent](#scanrefstositecontent)
* [Release notes](release-notes.md#summary)

## scanRefsToSiteContent

This script scans all the nodes under the selected site, and detects references from nodes stored outside from the site. This is for example useful before deleting a site, in order to detect some references which will be broken after the deletion.

References to pages, files, pieces of content should be updated or deleted prior to the deletion of the site. 

Some other references are expected, and do not require any action before proceeding with the deletion of the site:
             
| Reference                                                                                                               | Explanation                                                                                                                                 |                                 
|:------------------------------------------------------------------------------------------------------------------------|:--------------------------------------------------------------------------------------------------------------------------------------------|
| */sites/j:defaultSite -> /sites/digitall*                                                                               | The site is currently flagged as the default one. After its deletion, the system will automatically select another site as the default one. |
| */groups/privileged/j:members/sites/digitall/groups/site-privileged/j:member -> /sites/digitall/groups/site-privileged* | Each site has a local group named `site-privileged`, and this group has to be member of the server-level group named `privileged`.          |

If you intend to export the site, delete it, and eventually import it back, you should enable the option to create the entries in the references keeper. Doing so, the refences which will get broken while deleting the site will be restored after reimporting it.