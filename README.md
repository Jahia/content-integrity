# verify-integrity
DX module that provides an extensible service to test the integrity of the content
* [How to use it] (#how-to-use)
    * [jcr:integrity-check] (#jcr:integrity-check) 
    * [jcr:integrity-printChecks] (#jcr:integrity-printChecks) 
    * [jcr:integrity-printTestResults] (#jcr:integrity-printTestResults) 
* [How to extend it] (#how-to-extend) 

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
#### <a name="jcr:integrity-check"></a>jcr:integrity-check  
Runs a scan of the current tree and current workspace.

##### Options:  
Name | alias | Mandatory | Value | Description
 --- | --- | :---: | :---: | ---
 -l | --limit | | positive integer, [20] | Specifies the maximum number of errors to print out
 
##### Examples:
    jcr:cd /sites/mySite/
    jcr:workspace live
    jcr:integrity-check -l 10   
      
#### <a name="jcr:integrity-printChecks"></a>jcr:integrity-printChecks 
Prints out the currently registered checks. 
                         
##### Options:
Name | alias | Mandatory | Value | Description
 --- | --- | :---: | :---: | ---
 -l | --outputLevel | | [simple] , full | Specifies the output level to use  
 
#### <a name="jcr:integrity-printTestResults"></a>jcr:integrity-printTestResults   
Allows to reprint the result of a previous test.   
                         
##### Options:
Name | alias | Mandatory | Value | Description
 --- | --- | :---: | :---: | ---
 -l | --limit | | positive integer, [20] | Specifies the maximum number of errors to print out
 -d | --dump , --dumpToCSV | | boolean, [false] | Dumps the errors into a CSV file in temp/content-integrity/ . The limit option is ignored when dumping
 -f | --showFixedErrors | | boolean, [false] | Coming soon 

 
#### jcr:integrity-fix
Coming soon    
 

## <a name="how-to-extend"></a>How to extend?

[TODO]

## Changelog
Version | Required DX version | Changes
------------ | -------------| -------------
2.0 | 7.2.0.0 | Initial version