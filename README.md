# verify-integrity
Jahia module to detect if integrity content of a site is correct. Used to detect if changes in the nodetypes definitions has been done without editing the content accordingly

# Integrity issues currently being checked :
- mandatory field missing values
- property type modified (i.e. property was previously a string, is now a date, but the value is incorrect for a date field)
- regex or range constraint not being fulfiled
- removed property while there was still value

# How-to use the module :
The module has to be installed on your environment, but you do not have to deploy it on a site.
It provides an action than you can call on any node of a site, the action will then scan entirely the site for
identifying integrity issue.

Name of the action : verifyIntegrityOfSiteContent

Example of URL to call : http://localhost:8080/cms/render/default/en/sites/mySite/home.verifyIntegrityOfSiteContent.do

The action will then log detected node in errors with a message specifying the issue
It will also return JSON result for automation. You can check for those keys in the result :
- siteContentIsValid : "true" or "false" value, depending on the result of the verification
- numberOfErrors : number of errors found during the verification
- errors : logged errors found