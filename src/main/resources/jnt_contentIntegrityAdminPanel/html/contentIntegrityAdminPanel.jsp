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

<template:addResources type="javascript" resources="jquery.js,jquery-ui.min.js,contentIntegrity.js"/>
<template:addResources type="css" resources="contentIntegrity.css,jquery-ui.smoothness.css"/>
<template:addResources>
    <script type="text/javascript">
        const constants = {
            resultsPanel: {
                resultsSelector: {
                    wrapper: "resultsSelector",
                    select: "resultList",
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
    <span class="tabLink" tabrole="scan">Scan</span>
    <span class="tabLink" tabrole="results">Results</span>
</div>

<div id="scan-panel" class="mainPanel">
<fieldset class="configWrapper">
    <div id="configurations"></div>
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
        <td><label for="pathToExclude">Excluded paths:</label></td>
        <td>
            <input id="pathToExclude" type="text" value=""/>
            <button id="addExcludedPath">Add</button>
            <div id="excludedPaths"></div>
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
</table>
<div>
    <input id="runScan" type="button" value="Run an integrity check"/>
    <input id="stopScan" type="button" value="Stop"/>
    <div>
        <pre id="logs"></pre>
    </div>
    <div id="reportFile" style="display: none"></div>
</div>
<div id="configurationPanelWrapper" style="display: none"></div>
</div>
<div id="results-panel" class="mainPanel">
    <div id="resultsSelector"></div>
    <div id="resultsDetails"></div>
</div>
<div id="errorDetailsPanelWrapper" style="display: none"></div>
