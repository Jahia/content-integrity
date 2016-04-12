# verify-integrity
Jahia module to detect if integrity content of a site is correct. Used to detect if changes in the nodetypes definitions has been done without editing the content accordingly

Issues currently detected :
- mandatory field missing values
- property type modified (i.e. property was previously a string, is now a date, but the value is incorrect for a date field)
- regex or range constraint not being fulfiled
- removed property while there was still value