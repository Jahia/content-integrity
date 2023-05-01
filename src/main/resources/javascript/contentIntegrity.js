const RUNNING = "running";
let logsLoader;
const STOP_PULLING_LOGS = _ => clearInterval(logsLoader);
const model = {
    excludedPaths: [],
    errorsDisplay: {
        resultsID: undefined,
        totalErrorCount: 0,
        errorCount: 0,
        offset: 0,
        pageSize: 20,
        filters: {
            possibleValues: [],
            active: {}
        }
    },
    toolsToken: undefined
}

const gqlConfig = {
    query: "{" +
        "  integrity:contentIntegrity {" +
        "    checks:integrityChecks {" +
        "      id enabled configurable documentation" +
        "    }" +
        "  }" +
        "}"
}

function getScanQuery(rootPath, workspace, skipMP, checks) {
    return {
        query: "query ($path: String!, $excludedPaths: [String], $ws: WorkspaceToScan!, $checks: [String], $skipMP: Boolean) {" +
            "  integrity:contentIntegrity {" +
            "    scan:integrityScan {" +
            "      id:scan(startNode: $path, excludedPaths: $excludedPaths, workspace: $ws, checksToRun: $checks, uploadResults: true, skipMountPoints: $skipMP)" +
            "    }" +
            "  }" +
            "}",
        variables: {path: rootPath, excludedPaths: model.excludedPaths, ws: workspace, checks: checks, skipMP: skipMP}
    }
}

function getLogsQuery(executionID) {
    return {
        query: "query ($id : String!) {" +
            "    integrity:contentIntegrity {" +
            "        scan:integrityScan (id: $id) {" +
            "            id status logs" +
            "            reports {name location uri extension}" +
            "        }" +
            "    }" +
            "}",
        variables: {id: executionID}
    }
}

function getRunningTaskQuery() {
    return {
        query: "{" +
            "    integrity:contentIntegrity {" +
            "        scan:integrityScan {" +
            "            id status" +
            "        }" +
            "    }" +
            "}"
    }
}

function getStopScanQuery(executionID) {
    return {
        query: "query ($id : String!) {" +
            "    integrity:contentIntegrity {" +
            "        scan:integrityScan(id: $id) {" +
            "            stopRunningScan" +
            "        }" +
            "    }" +
            "}",
        variables: {id: executionID}
    }
}

function getSaveConfsQuery(checkID, confs) {
    const updates = confs.map(({name, value}) => (escapeConfigName(name) + `:configure (name:"${name}", value: "${value}")`)).join(" ");
    return {
        query: "{integrity:contentIntegrity {" +
            "check:integrityCheckById(id : " + '"' + checkID + '"' + ") {" +
            updates +
            "}" +
            "}}"
    }
}

function getLoadCheckConfsQuery(checkID) {
    return getCheckConfsQuery(checkID, false)
}

function getResetCheckConfsQuery(checkID) {
    return getCheckConfsQuery(checkID, true)
}

function getCheckConfsQuery(checkID, reset) {
    const resetQuery = reset ? " reset:resetAllConfigurations" : ""
    return {
        query: "query ($id : String!) {" +
            "  integrity: contentIntegrity {" +
            "    check:integrityCheckById(id: $id) {" +
            resetQuery +
            "      configurations {" +
            "        name value type description" +
            "      }" +
            "    }" +
            "  }" +
            "}",
        variables: {id: checkID}
    }
}

function getScanResultsList() {
    return {
        query: "{" +
            "    integrity:contentIntegrity {" +
            "        scanResults" +
            "    }" +
            "}"
    }
}

function getScanResults(filters) {
    const fields = ["id", "nodePath", "nodeId", "message", "workspace"]
    model.errorsDisplay.columns.filter(({key}) => !fields.includes(key)).forEach(({key}) => fields.push(key))
    const filteredColumns = filters.map((filter) => filter.key)
    const activeFiltersJson = model.errorsDisplay.filters.active
    const activeFilters = []
    for (let f in activeFiltersJson) {
        const filterValue = activeFiltersJson[f];
        if (filterValue !== constants.resultsPanel.filters.noFilter)
            activeFilters.push(f + ";" + filterValue)
    }
    return {
        query: "query ($id : String!, $offset : Int!, $size : Int!, $filters : [String]!) {" +
            "    integrity:contentIntegrity {" +
            "        results:scanResultsDetails(id: $id, filters: $filters) {" +
            "            errorCount totalErrorCount" +
            "            reports {name location uri extension}" +
            "            errors(offset: $offset, pageSize: $size) {" +
            fields.join(" ") +
            "            }" +
            "            possibleValues(names: [" + filteredColumns.map((name) => `"${name}"`).join(",") + "]) {name values}" +
            "        }" +
            "    }" +
            "}",
        variables: {id: model.errorsDisplay.resultsID, offset: model.errorsDisplay.offset, size: model.errorsDisplay.pageSize, filters: activeFilters}
    }
}

function getErrorDetails(id) {
    return {
        query: "query ($id: String!, $resultsID: String!) {" +
            "    integrity:contentIntegrity {" +
            "        results:scanResultsDetails(id: $resultsID) {" +
            "            error:errorById(id: $id) {" +
            "                checkName workspace locale" +
            "                nodePath nodeId nodePrimaryType nodeMixins" +
            "                message errorType" +
            "                extraInfos { label value } " +
            "            }" +
            "        }" +
            "    }" +
            "}",
        variables: {id: id, resultsID: model.errorsDisplay.resultsID}
    }
}

function escapeConfigName(name) {
    return "configure_" + name.replaceAll("-", "_")
}

function gqlCall(query, successCB, failureCB) {
    jQuery.ajax({
        url: constants.url.context + '/modules/graphql',
        type: 'POST',
        contentType: "application/json",
        data: JSON.stringify(query),
        success: function (result) {
            if (result.errors != null) {
                console.error("Error with the query:", query, "response:", result.errors);
                if (failureCB !== undefined) failureCB();
                return;
            }
            if (result.data == null) {
                if (failureCB !== undefined) failureCB();
                return;
            }
            if (successCB !== undefined) successCB(result.data);
        }
    })
}

function refreshToolsTokenCall() {
    jQuery.ajax({
        url: constants.url.toolsToken,
        type: 'GET',
        success: function (result) {
            if (result.token !== null) {
                model.toolsToken = result
            } else {
                model.toolsToken = undefined
            }
        } ,
        error: (jqXHR, textStatus, errorThrown ) => console.error("Error when refreshing the tools token", ", status:", textStatus, ", source error:", errorThrown)
    })
}

function loadConfigurations() {
    gqlCall(gqlConfig, (data) => renderConfigurations(data.integrity.checks))
}

const IntegrityCheckItem = ({id, enabled, name, configurable, documentation}) => {
    let out = `<span class="config">`;
    out += `<input id="${id}" class="checkEnabled" type="checkbox" ${enabled ? checked = "checked" : ""}/>${name}`;
    out += HelpButtonItem(name, documentation)
    if (configurable) out += ConfigureButtonItem(id)
    out += `</span>`;
    return out;
}

const HelpButtonItem = (name, url) => {
    if (url === null) return ""
    return `<a href="${url}" target="_blank"><img class="configureLink" src="${constants.url.module}/img/help.png" title="${name}: documentation" alt="documentation" /></a>`
}

const ConfigureButtonItem = (id) => {
    return `<img class="configureLink" src="${constants.url.module}/img/configure.png" title="Configure" alt="configure" checkID="${id}" dialogID="configure-${id}" />`
}

const ConfigPanelItem = ({id, name, configurations}) => {
    let out = `<div id="configurationPanel" integrityCheckID="${id}"><span class="panelTitle">${name}</span>`;
    if (configurations !== null && configurations !== undefined) {
        out += `<div class="configurationPanelInput">`
        out += configurations.map(ConfigItem).join('')
        out += `</div>`;
    }
    out += `</div>`;
    return out;
}

const ConfigItem = ({name, type, value, description}) => {
    const params = { name: name, value: value, description: description }
    let out = `<span class="inputLabel">${name}:</span>`
    switch (type) {
        case "integer":
            out += IntegerConfigItem(params)
            break
        case "boolean":
            out += BooleanConfigItem(params)
            break
        default:
            console.error("Unsupported type '", type, "' for the configuration '", name, "'")
            return ''
    }
    out += `<span class="configDesc">${description}</span>`
    return out
}

const IntegerConfigItem = ({name, value}) => `<input type="text" name="${name}" value="${value}" />`

const BooleanConfigItem = ({name, value}) => {
    let out = `<input type="checkbox" name="${name}"`
    if (value === "true") out += ` checked="checked"`
    out += `/>`
    return out
}

const ReportFileItem = (filename, path) => `<a href="${constants.url.files}${path}" target="_blank" class="report">${filename}</a>`

const ReportFileListItem = (files) => {
    const validFiles = files.map(({name, location, uri}) => {
        if (location !== "JCR") return null
        return {filename: name, path: uri}
    }).filter(f => f !== null)
    if (validFiles.length === 0) return ""

    if (validFiles.length === 1) {
        const file = validFiles[0]
        return "Report: " + ReportFileItem(file.filename, file.path)
    }
    let out = "Reports:<ul>"
    validFiles.forEach(({filename, path}) => out += `<li>${ReportFileItem(filename, path)}</li>`)
    return out + "</ul>"
}

const ScanResultsSelectorItem = (ids) => {
    const current = model.errorsDisplay.resultsID
    let out = `<select id="${constants.resultsPanel.resultsSelector.select}">`
    ids.forEach((id) => out += `<option value="${id}"${current === id ? " selected='selected'" : ""}>${id}</option>`)
    out += `</select>`
    return out
}

const ErrorItem = (error, columns) => {
    const cells = columns.map(({key, jcrBrowserLink}) => {
        if (jcrBrowserLink === true) return JcrBrowserLinkItem(error[key], error.nodeId, error.workspace)
        return error[key]
    })
    return TableRowItem(...cells, `<img src="${constants.url.module}/img/help.png" title="Error details" alt="details" class="errorDetails" error-id="${error.id}" />`)
}

const ErrorsListItem = (errors) => {
    const columns = model.errorsDisplay.columns.filter(({display}) => display !== false)
    let out = `<table>`
    out += TableHeaderRowItem(...(columns.map(({key, label}) => label === undefined ? key : label)), "Details")
    out += errors.map(e => ErrorItem(e, columns)).join('')
    out += `</table>`
    out += ErrorsPagerItem()
    return out
}

const ErrorsColumnsConfigItem = _ => `<div class="columnsConfig">${model.errorsDisplay.columns.map(({key, label, display}) => {
    const id = `col-display-${key}`
    const checked = display === false ? "" : `checked="checked"`
    return `<span><input type="checkbox" id="${id}" col-id="${key}" ${checked}/><label for="${id}">${label === undefined ? key : label}</label></span>`
}).join('')}</div>`

const ErrorsFiltersConfigItem = (filters) => `<div class="columnsFilters">${filters.map(ErrorFilterConfigItem).join('')}</div>`

const ErrorFilterConfigItem = (filter) => {
    let template;
    switch (filter.type) {
        case undefined:
        case "select":
            template = ErrorFilterConfigSelectItem
            break
        default:
            console.error("Unsupported filter type", filter.type)
    }
    if (template === undefined) return

    const params = {
        key: filter.key,
        values: model.errorsDisplay.filters.possibleValues.filter((col) => col.name === filter.key)[0].values
    }
    return template(params);
}

const ErrorFilterConfigSelectItem = ({key, label, values}) => {
    values.unshift(constants.resultsPanel.filters.noFilter)
    const current = model.errorsDisplay.filters.active[key]
    return `<label for="">${label === undefined ? key : label}</label><select id="col-filter-${key}" filter="${key}" class="columnFilter">${values.map((val) => HtmlOptionItem(val, val === current))}</select>`;
}

const ErrorsPagerItem = _ => {
    return `
    <div class="resultsPager">
        ${ErrorPagerButtonsItem()}
        ${ErrorPagerSizeConfigItem()}
        ${ErrorPagerNumberOfErrorsItem()}
    </div>`
}

const ErrorPagerButtonsItem = _ => {
    if (model.errorsDisplay.errorCount <= model.errorsDisplay.pageSize) return ""

    const offset = model.errorsDisplay.offset
    const pageSize = model.errorsDisplay.pageSize
    const errorCount = model.errorsDisplay.errorCount
    const nbEdgePages = constants.resultsPanel.pager.nbEdgePages
    const nbSiblingPages = constants.resultsPanel.pager.nbSiblingPages

    const pageIdx = offset / pageSize + 1
    const lastPage = Math.ceil(errorCount / pageSize)

    const displayedIdx = []
    const isDisplayedIndex = (i) => i <= nbEdgePages || i > lastPage - nbEdgePages || i >= pageIdx - nbSiblingPages && i <= pageIdx + nbSiblingPages
    for (let i = 1; i <= lastPage; i++) {
        if (isDisplayedIndex(i)) displayedIdx.push(i)
        else if (displayedIdx.includes(i - 1)) displayedIdx.push(constants.resultsPanel.pager.skippedLinksSeparator.key)
    }

    let out = ""
    if (pageIdx > 1) out += ErrorPagerLinkItem(pageIdx - 1, pageSize, pageIdx, constants.resultsPanel.pager.previous)
    out += displayedIdx.map((idx) => ErrorPagerLinkItem(idx, pageSize, pageIdx)).join('')
    if (pageIdx < lastPage) out += ErrorPagerLinkItem(pageIdx + 1, pageSize, pageIdx, constants.resultsPanel.pager.next)
    return out
}

const ErrorPagerLinkItem = (idx, pageSize, currentPageIdx, label) => {
    if (idx === constants.resultsPanel.pager.skippedLinksSeparator.key)
        return `<span>${constants.resultsPanel.pager.skippedLinksSeparator.label}</span>`
    const offset = (idx - 1) * pageSize
    if (idx === currentPageIdx)
        return `<a href="#" onclick="return false" class="current">${label === undefined ? idx : label}</a>`;
    else
        return `<a href="#" onclick="displayScanResults(${offset});return false">${label === undefined ? idx : label}</a>`;
}

const ErrorPagerSizeConfigItem = _ => {
    let out = `<select onchange="displayScanResults(${model.errorsDisplay.offset}, this.value)">`
    out += constants.resultsPanel.pager.allowedPageSizes
        .map((size) => `<option value="${size}" ${size === model.errorsDisplay.pageSize ? 'selected="selected"' : ''}>${size} errors per page</option>`)
        .join('')
    out += `</select>`
    return out
}

const ErrorPagerNumberOfErrorsItem = _ => {
    const totalInfo = model.errorsDisplay.errorCount === model.errorsDisplay.totalErrorCount ?
        "" : ` (total: ${model.errorsDisplay.totalErrorCount})`
    const errorWord = model.errorsDisplay.errorCount > 1 ? "errors" : "error"

    return `<div>${model.errorsDisplay.errorCount} ${errorWord}${totalInfo}</div>`
}

const ErrorDetailsItem = (error) => {
    let out = "<table>"
    out += TableRowItem("Check name", error.checkName)
    out += TableRowItem("Workspace", error.workspace)
    out += TableRowItem("Locale", error.locale)
    out += TableRowItem("Path", JcrBrowserLinkItem(error.nodePath, error.nodeId, error.workspace))
    out += TableRowItem("UUID", JcrBrowserLinkItem(error.nodeId, error.nodeId, error.workspace))
    out += TableRowItem("Node type", error.nodePrimaryType)
    out += TableRowItem("Mixin types", error.nodeMixins)
    out += TableRowItem("Message", error.message)
    out += TableRowItem("Error type", error.errorType)
    error.extraInfos.forEach((info) => out += TableRowItem(info.label, info.value))
    out += "</table>"
    return out
}

const TableRowItem = (...cells) => TypedTableHeaderRowItem(false, ...cells)

const TableHeaderRowItem = (...cells) => TypedTableHeaderRowItem(true, ...cells)

const TypedTableHeaderRowItem = (isHeader, ...cells) => {
    const cellType = isHeader ? "th" : "td"
    const s = cells.join(`</${cellType}><${cellType}>`);
    return `<tr><${cellType}>${s}</${cellType}></tr>`;
}

const HtmlOptionItem = (value, selected, label) =>  `<option value="${value}"${selected === true ? ` selected="selected"` : ""}>${label === undefined ? value : label}</option>`

const ExcludedPathItem = ({path}) => `<span class="excludedPath" path="${path}">${path}</span>`

const JcrBrowserLinkItem = (name, uuid, workspace) => {
    if (model.toolsToken === undefined) return name
    return `<a href="${constants.url.context}/modules/tools/jcrBrowser.jsp?workspace=${workspace}&uuid=${uuid}&toolAccessToken=${model.toolsToken.token}" target="_blank">${name}</a>`;
}

function renderConfigurations(data) {
    const conf = []
    jQuery.each(data, function (index) {
        conf[index] = {
            name: this.id,
            id: this.id,
            enabled: this.enabled,
            configurable: this.configurable,
            documentation: this.documentation
        }
    })
    jQuery('#configurations').html(conf.map(IntegrityCheckItem).join(''));
    jQuery('#configurationPanelWrapper').dialog({
        autoOpen: false,
        resizable: false,
        height: "auto",
        width: 800,
        modal: true,
        buttons: {
            "Save": function () {
                const panel = jQuery("#configurationPanel");
                const confs = panel.find("input").map(function() {
                    return ({
                        name: this.name,
                        value: this.type === "checkbox" ? (this.checked ? "true" : "false") : this.value
                    });
                });
                saveConfigurations(panel.attr("integrityCheckID"), jQuery.makeArray(confs))
                jQuery(this).dialog("close");
            },
            "Reset to default values": function () {
                const panel = jQuery("#configurationPanel");
                gqlCall(getResetCheckConfsQuery(panel.attr("integrityCheckID")), (data) => {
                    jQuery(this).dialog("close");
                })
            },
            Cancel: function () {
                jQuery(this).dialog("close");
            }
        },
        title: "Configure"
    });
    jQuery('.configureLink').on("click", function () {
        const id = jQuery(this).attr("checkID")
        const popup = jQuery("#configurationPanelWrapper")
        gqlCall(getLoadCheckConfsQuery(id), (data) => {
            popup.html(ConfigPanelItem({id: id, name: id, configurations: data.integrity.check.configurations}))
            popup.dialog("open")
        })
    });
}

function saveConfigurations(checkID, confs) {
    gqlCall(getSaveConfsQuery(checkID, confs))
}

function selectAllChecks(value) {
    jQuery(".checkEnabled").prop("checked", value)
    return false
}

function renderLogs(executionID) {
    const reportFileDiv = jQuery("#reportFile")
    reportFileDiv.hide()
    gqlCall(getLogsQuery(executionID), (data) => {
        const logs = jQuery("#logs")
        const logsElement = logs[0]
        const currentScroll = logsElement.scrollTop
        const isScrolledToEnd = currentScroll + logsElement.clientHeight === logsElement.scrollHeight
        logs.html("")
        jQuery.each(data.integrity.scan.logs, function () {
            logs.append(this+"\n")
        })
        const scrollTarget = isScrolledToEnd ? logsElement.scrollHeight : currentScroll
        logs.scrollTop(scrollTarget)
        if (data.integrity.scan.status === RUNNING) {
            showStopButton(true, executionID);
        } else {
            STOP_PULLING_LOGS();
            showStopButton(false);
            const reports = data.integrity.scan.reports
            if (reports != null) {
                const out = ReportFileListItem(reports)
                if (out.length > 0) reportFileDiv.html(out).show()
            }
        }
    }, _ => STOP_PULLING_LOGS);
}

function setupLogsLoader(executionID) {
    if (logsLoader !== null) STOP_PULLING_LOGS();
    renderLogs(executionID);
    logsLoader = setInterval((id) => {renderLogs(id)}, 5000, executionID)
}

function wireToRunningScan() {
    gqlCall(getRunningTaskQuery(), (data) => {
        const scan = data.integrity.scan;
        if (scan == null) return;
        if (scan.status === RUNNING && scan.id != null) {
            setupLogsLoader(scan.id)
        }
    })
}

function showStopButton(visible, executionID) {
    if (visible) {
        jQuery("#runScan").attr("disabled", "disabled");
        jQuery("#stopScan").click(function () {
            gqlCall(getStopScanQuery(executionID), _ => showStopButton(false))
        }).show();
    }
    else {
        jQuery("#runScan").removeAttr("disabled");
        jQuery("#stopScan").hide();
    }
}

function addExcludedPath() {
    const input = jQuery("#pathToExclude");
    input.focus()
    const path = input.val().trim()
    if (path.length === 0) return
    if (!model.excludedPaths.includes(path)) {
        model.excludedPaths.push(path)
        renderExcludedPaths()
    }
}

function removeExcludedPath(path) {
    if (!model.excludedPaths.includes(path)) return
    model.excludedPaths = model.excludedPaths.filter(p => p !== path)
    renderExcludedPaths()
}

function renderExcludedPaths() {
    const input = jQuery("#pathToExclude");
    input.val("")
    const wrapper = jQuery("#excludedPaths")
    wrapper.html("")
    model.excludedPaths.forEach(path => wrapper.append(ExcludedPathItem({path: path})))
    jQuery(".excludedPath").click(function (){removeExcludedPath(jQuery(this).attr("path"))})
}

function displayPanel(id) {
    jQuery(".mainPanel").hide()
    jQuery("#" + id + "-panel").show()
    refreshOnActivation(id)
    jQuery(".tabs .tabLink").removeClass("selected")
    jQuery(".tabs .tabLink[tabrole=" + id + "]").addClass("selected")
}

function addPanelListener() {
    jQuery(".tabs .tabLink").click(function () {
        displayPanel(jQuery(this).attr("tabrole"))
    })
}

function refreshOnActivation(panelID) {
    switch (panelID) {
        case "results":
            activateResultsPanel()
            break
        default:
    }
}

function activateResultsPanel() {
    gqlCall(getScanResultsList(), (data) => {
        let needRefresh = false
        const ids = data.integrity.scanResults
        if (!ids.includes(model.errorsDisplay.resultsID)) {
            model.errorsDisplay.resultsID = ids.find(_ => true)
            needRefresh = true
        }
        jQuery("#" + constants.resultsPanel.resultsSelector.wrapper).html(ScanResultsSelectorItem(ids))
        jQuery("#" + constants.resultsPanel.resultsSelector.select).change(function () {
            model.errorsDisplay.resultsID = jQuery(this).val()
            displayScanResults()
        })
        if (needRefresh) displayScanResults()
    })
}

function displayScanResults(offset, pageSize) {
    refreshToolsTokenCall()

    const out = jQuery("#resultsDetails")
    out.html("")
    if (model.errorsDisplay.resultsID === undefined) return

    model.errorsDisplay.offset = offset === undefined || isNaN(offset) ? 0 : parseInt(offset)
    if (pageSize !== undefined && !isNaN(pageSize)) {
        model.errorsDisplay.pageSize = parseInt(pageSize)
    }
    model.errorsDisplay.offset = Math.floor(model.errorsDisplay.offset / model.errorsDisplay.pageSize) * model.errorsDisplay.pageSize
    const filters = model.errorsDisplay.columns.filter((col) => col.filterable === true)
    gqlCall(getScanResults(filters), (data) => {
        const results = data.integrity.results
        if (results === null) {
            return
        }

        model.errorsDisplay.errorCount = results.errorCount
        model.errorsDisplay.totalErrorCount = results.totalErrorCount
        model.errorsDisplay.filters.possibleValues = results.possibleValues
        out.append(ErrorsColumnsConfigItem())
        jQuery(".columnsConfig input[type='checkbox']").on("change", function () {
            const colKey = jQuery(this).attr("col-id")
            model.errorsDisplay.columns.forEach((col) => {
                if (col.key === colKey) {
                    if (col.display === false) col.display = true
                    else col.display = false
                }
            })
            displayScanResults()
        })
        out.append(ErrorsFiltersConfigItem(filters))
        jQuery(".columnFilter").on("change", function () {
            model.errorsDisplay.filters.active[jQuery(this).attr("filter")] = jQuery(this).val()
            model.errorsDisplay.offset = 0
            displayScanResults()
        })
        out.append(ErrorPagerNumberOfErrorsItem())
        out.append(ErrorsListItem(results.errors))
        jQuery(".errorDetails").on("click", function () {
            displayErrorDetails(jQuery(this).attr("error-id"))
        })

        const reports = results.reports
        if (reports != null) {
            out.append(ReportFileListItem(reports))
        }
    })
}

function displayErrorDetails(id) {
    gqlCall(getErrorDetails(id), (data) => {
        const error = data.integrity.results.error
        if (error === null) {
            alert("Unknown error")
            return
        }
        const popup = jQuery("#errorDetailsPanelWrapper")
        popup.html(ErrorDetailsItem(error)).dialog("open")
    })
}

function initResultsScreen() {
    model.errorsDisplay.columns = constants.resultsPanel.columns.filter(({key}) => key !== undefined)

    jQuery("#errorDetailsPanelWrapper").dialog({
        autoOpen: false,
        resizable: false,
        height: "auto",
        width: 800,
        modal: true,
        title: "Error details",
        buttons: {
            Cancel: function () {
                jQuery(this).dialog("close")
            }
        }
    })
}

jQuery(document).ready(function () {
    displayPanel("scan")
    loadConfigurations();
    jQuery("#pathToExclude").keypress(function (event){
        // 13: <enter>
        if (event.which === 13) {
            jQuery("#addExcludedPath").click()
        }
    })
    jQuery("#addExcludedPath").click(function () {
        addExcludedPath()
    })
    jQuery("#runScan").click(function () {
        const rootPath = jQuery("#rootNode").val();
        const workspace = jQuery("#workspace").val();
        const skipMP = !jQuery("#includeVirtualNodes").is(":checked")

        const checks = jQuery.map(jQuery(".checkEnabled:checked"), function (cb, i) {
            return jQuery(cb).attr("id")
        })

        gqlCall(getScanQuery(rootPath, workspace, skipMP, checks), (data) => setupLogsLoader(data.integrity.scan.id));
    });
    wireToRunningScan();
    initResultsScreen()
    addPanelListener()
});
