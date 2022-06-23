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
        const moduleContentIntegrityURL = '${url.context}${url.currentModule}'
        const urlContext = '${url.context}'
        const urlFiles  = '${url.files}'
    </script>
</template:addResources>

<h1><fmt:message key="label.settings.title"/></h1>
<p><fmt:message key="label.contentIntegrity.description"/></p>

<fieldset class="configWrapper">
    <div id="configurations"></div>
    <div>
        <a href="javascript:selectAllChecks(true)">select all</a> / <a href="javascript:selectAllChecks(false)">unselect all</a>
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