<%@ page import="org.jahia.modules.contentintegrity.services.Utils" %>
<%@ page language="java" contentType="text/html;charset=UTF-8" %>
<%@ taglib prefix="template" uri="http://www.jahia.org/tags/templateLib" %>
<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ taglib prefix="fn" uri="http://java.sun.com/jsp/jstl/functions" %>
<%@ taglib prefix="fmt" uri="http://java.sun.com/jsp/jstl/fmt" %>
<%@ taglib prefix="jcr" uri="http://www.jahia.org/tags/jcr" %>
<%@ taglib prefix="ui" uri="http://www.jahia.org/tags/uiComponentsLib" %>
<%@ taglib prefix="functions" uri="http://www.jahia.org/tags/functions" %>
<%@ taglib prefix="query" uri="http://www.jahia.org/tags/queryLib" %>
<%@ taglib prefix="utility" uri="http://www.jahia.org/tags/utilityLib" %>
<%@ taglib prefix="s" uri="http://www.jahia.org/tags/search" %>
<%--@elvariable id="currentNode" type="org.jahia.services.content.JCRNodeWrapper"--%>
<%--@elvariable id="out" type="java.io.PrintWriter"--%>
<%--@elvariable id="script" type="org.jahia.services.render.scripting.Script"--%>
<%--@elvariable id="scriptInfo"checkSummary type="java.lang.String"--%>
<%--@elvariable id="workspace" type="java.lang.String"--%>
<%--@elvariable id="renderContext" type="org.jahia.services.render.RenderContext"--%>
<%--@elvariable id="currentResource" type="org.jahia.services.render.Resource"--%>
<%--@elvariable id="url" type="org.jahia.services.render.URLGenerator"--%>
<c:set var="scanPanelKey" value="scan" />
<c:set var="resultsPanelKey" value="results" />
<c:set var="resultsDetailsAreaID" value="resultsDetails" />
<c:set var="resultsChartAreaID" value="resultsChart" />
<c:set var="resultsChartWrapperID" value="resultsChartWrapper" />
<c:set var="configurationAreaID" value="configurations" />
<c:set var="configurationPanelID" value="configurationPanelWrapper" />
<c:set var="errorDetailsPanelID" value="errorDetailsPanelWrapper" />
<c:set var="logsPanelID" value="logs" />
<c:set var="reportFilesPanelID" value="reportFiles" />
<c:set var="runScanButtonID" value="runScan" />
<c:set var="stopScanButtonID" value="stopScan" />
<c:set var="excludedPathsNewValueID" value="pathToExclude" />
<c:set var="excludedPathsAddButtonID" value="addExcludedPath" />
<c:set var="excludedPathsCurrentValuesID" value="excludedPaths" />

<template:addResources type="javascript" resources="jquery.js,jquery-ui.min.js,chart.umd.js,contentIntegrity.js"/>
<%--<template:addResources type="javascript" resources="jquery.js,jquery-ui.min.js,contentIntegrity.js?v=<%=Utils.getContentIntegrityVersion()%>"/>--%>
<template:addResources type="css" resources="contentIntegrity.css,jquery-ui.smoothness.css"/>
<template:addResources>
    <script type="text/javascript">
        const constants = {
            scanPanel: {
                key: "${scanPanelKey}",
                configurationsArea: {
                    id: "${configurationAreaID}"
                },
                configurationPanel: {
                    id: "${configurationPanelID}",
                    itemKey: "configurationPanel"
                },
                logsPanel: {
                    id: "${logsPanelID}"
                },
                reportFilesPanel : {
                    id: "${reportFilesPanelID}"
                },
                runScanButton: {
                    id: "${runScanButtonID}"
                },
                stopScanButton: {
                    id: "${stopScanButtonID}"
                },
                excludedPaths: {
                    newValueID: "${excludedPathsNewValueID}",
                    addButtonID: "${excludedPathsAddButtonID}",
                    currentValuesID: "${excludedPathsCurrentValuesID}"
                }
            },
            resultsPanel: {
                key: "${resultsPanelKey}",
                views: {
                    default: "default",
                    chart: "chart"
                },
                resultsSelector: {
                    wrapper: "resultsSelector",
                    select: "resultList"
                },
                resultsArea: {
                    id: "${resultsDetailsAreaID}"
                },
                chartArea: {
                    id: "${resultsChartAreaID}",
                    wrapper: "${resultsChartWrapperID}"
                },
                pager: {
                    allowedPageSizes: [5, 10, 20, 50, 100],
                    nbEdgePages: 2,
                    nbSiblingPages: 1,
                    skippedLinksSeparator: {
                        key: "sep",
                        label: "..."
                    },
                    previous: "<<",
                    next: ">>"
                },
                columns: [
                    {key: "checkName", label: "Check name", display: false, filterable: true},
                    {key: "errorType", label: "Error type", display: false, filterable: true},
                    {key: "workspace", label: "Workspace", display: false, filterable: true},
                    {key: "site", label: "Site", filterable: true},
                    {key: "nodePath", label: "Path", jcrBrowserLink: true},
                    {key: "nodeId", label: "UUID", display: false, jcrBrowserLink: true},
                    {key: "nodePrimaryType", label: "Primary type", display: true, filterable: true},
                    {key: "nodeMixins", label: "Mixins", display: false},
                    {key: "locale", label: "Locale", display: false, filterable: true},
                    {key: "message", label: "Message", filterable: true},
                    {key: "extraInfosString", label: "Extra info", display: false},
                    {key: "importError", label: "Impact on XML import", display: false, filterable: true}
                ],
                filters: {
                    noFilter: "--- ALL ---"
                },
                errorDetailsPanel: {
                    id: "${errorDetailsPanelID}"
                }
            },
            url: {
                context: "${url.context}",
                module: "${url.context}${url.currentModule}",
                files: "${url.context}${url.files}",
                toolsToken: "<c:url value="${url.context}${url.base}${currentNode.path}.toolsToken.json" />"
            }
        }
    </script>
</template:addResources>

<h1><fmt:message key="label.settings.title"/></h1>
<p><fmt:message key="label.contentIntegrity.description"/></p>

<div class="tabs">
    <span class="tabLink" tabrole="${scanPanelKey}">Scan</span>
    <span class="tabLink" tabrole="${resultsPanelKey}">Results</span>
</div>

<div id="scan-panel" class="mainPanel">
<fieldset class="configWrapper">
    <div id="${configurationAreaID}"></div>
    <div>
        <a href="#" onclick="selectAllChecks(true)">select all</a> / <a href="#" onclick="selectAllChecks(false)">unselect all</a>
    </div>
</fieldset>

<table id="scanParameters">
    <tr>
        <td><label for="rootNode">Root node: </label></td>
        <td><input id="rootNode" type="text" value="/"/></td>
    </tr>
    <tr>
        <td><label for="${excludedPathsNewValueID}">Excluded paths:</label></td>
        <td>
            <input id="${excludedPathsNewValueID}" type="text" value=""/>
            <button id="${excludedPathsAddButtonID}">Add</button>
            <div id="${excludedPathsCurrentValuesID}"></div>
        </td>
    </tr>
    <tr>
        <td><label for="workspace">Workspace: </label></td>
        <td>
            <select id="workspace">
                <option value="EDIT" selected="selected">default</option>
                <option value="LIVE">live</option>
                <option value="BOTH">All workspaces</option>
            </select>
        </td>
    </tr>
    <tr>
        <td><label for="includeVirtualNodes">Include the virtual nodes</label></td>
        <td><input id="includeVirtualNodes" type="checkbox" checked="checked"></td>
    </tr>
    <%--
    <tr>
        <td><label for="switchToResults">Display the errors at the end of the scan</label></td>
        <td><input id="switchToResults" type="checkbox" checked="checked"></td>
    </tr>
    --%>
</table>
<div>
    <input id="${runScanButtonID}" type="button" value="Run an integrity check"/>
    <input id="${stopScanButtonID}" type="button" value="Stop"/>
    <div>
        <pre id="${logsPanelID}"></pre>
    </div>
    <div id="${reportFilesPanelID}" style="display: none"></div>
</div>
<div id="${configurationPanelID}" style="display: none"></div>
</div>
<div id="results-panel" class="mainPanel">
    <div id="resultsSelector"></div>
    <div id="${resultsDetailsAreaID}"></div>
</div>
<div id="${resultsChartWrapperID}"><canvas id="${resultsChartAreaID}"></canvas></div>
<div id="${errorDetailsPanelID}" style="display: none"></div>
<%--
https://www.chartjs.org/docs/latest/getting-started/
--%>
