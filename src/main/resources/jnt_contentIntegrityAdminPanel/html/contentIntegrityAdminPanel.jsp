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

<template:addResources type="javascript" resources="jquery.js,contentIntegrity.js"/>
<template:addResources type="css" resources="contentIntegrity.css"/>

<h2><fmt:message key="label.settings.title"/></h2>
<p><fmt:message key="label.contentIntegrity.description"/></p>

<fieldset class="configWrapper">
    <div id="configurations">

    </div>
    <div>
        <a href="javascript:selectAllChecks(true)">select all</a> / <a href="javascript:selectAllChecks(false)">unselect all</a>
    </div>
</fieldset>

<div>
    <label for="rootNode">Root node: </label>
    <input id="rootNode" type="text" value="/"/>
    <label for="workspace">Workspace: </label>
    <select id="workspace">
        <option value="EDIT" selected="selected">default</option>
        <option value="LIVE">live</option>
        <option value="BOTH">All workspaces</option>
    </select>
    <br/>
    <input id="runScan" type="button" value="Run an integrity check"/>
    <input id="stopScan" type="button" value="Stop"/>
    <div>
        <pre id="logs"></pre>
    </div>
</div>

<%--
<input type="hidden" id="currentSite" value="${renderContext.site.siteKey}" />
<input type="hidden" id="currentLanguage" value="${renderContext.mainResourceLocale.language}" />
Workspace : <select id="currentWorkspace">
    <option>default</option>
    <option>live</option>
</select>
<input type="submit" id="verifyButton" value="Check JCR integrity" />

<table class="table table-bordered table-striped table-hover integrityTable"  id="errorDisplay">

</table>
--%>