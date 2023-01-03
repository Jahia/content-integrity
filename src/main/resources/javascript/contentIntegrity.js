const RUNNING = "running";
let logsLoader;
const STOP_PULLING_LOGS = _ => clearInterval(logsLoader);
const model = {
    excludedPaths: [],
    errorsDisplay : {
        resultsID: undefined,
        errorsCount: 0,
        offset: 0,
        pageSize: 20
    }
}
const constants = {
    resultsPanel : {
        selectID : "resultList"
    }
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
            "            id status logs report" +
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

function getScanResults() {
    return {
        query: "query ($id : String!, $offset : Int!, $size : Int!) {" +
            "    integrity:contentIntegrity {" +
            "        results:scanResultsDetails(id: $id) {" +
            "            reportFilePath reportFileName errorCount" +
            "            errors(offset: $offset, pageSize: $size) {" +
            "                nodePath message" +
            "            }" +
            "        }" +
            "    }" +
            "}",
        variables: {id: model.errorsDisplay.resultsID, offset: model.errorsDisplay.offset, size: model.errorsDisplay.pageSize}
    }
}

function escapeConfigName(name) {
    return "configure_" + name.replaceAll("-", "_")
}

function gqlCall(query, successCB, failureCB) {
    jQuery.ajax({
        url: urlContext + '/modules/graphql',
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

function loadConfigurations() {
    gqlCall(gqlConfig, (data) => renderConfigurations(data.integrity.checks))
}

const IntegrityCheckItem = ({id, enabled, name, configurable, documentation}) => {
    let out = `<span class="config">`;
    out += `<input id="${id}" class="checkEnabled" type="checkbox" ${enabled ? checked = "checked" : ""}/>${name}`;
    out += HelpButtonItem(name, documentation, moduleContentIntegrityURL)
    if (configurable) out += ConfigureButtonItem(id, moduleContentIntegrityURL)
    out += `</span>`;
    return out;
}

const HelpButtonItem = (name, url, baseURL) => {
    if (url === null) return ""
    return `<a href="${url}" target="_blank"><img class="configureLink" src="${baseURL}/img/help.png" title="${name}: documentation" alt="Documentation" /></a>`
}

const ConfigureButtonItem = (id, baseURL) => {
    return `<img class="configureLink" src="${baseURL}/img/configure.png" title="configure" alt="Configure" checkID="${id}" dialogID="configure-${id}" />`
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

const ReportFileItem = (filename, path, urlContext, urlFiles) => `Report: <a href="${urlContext}${urlFiles}${path}" target="_blank">${filename}</a>`

const ScanResultsSelectorItem = (ids) => {
    const current = model.errorsDisplay.resultsID
    let out = `<select id="${constants.resultsPanel.selectID}">`
    ids.forEach((id, idx) => out += `<option value="${id}"${current === undefined && idx === 0 || current === id ? " selected='selected'" : ""}>${id}</option>`)
    out += `</select>`
    return out
}

const ErrorItem = (error) => `<tr><td>${error.nodePath}</td><td>${error.message}</td></tr>`

const ErrorsListItem = (errors) => {
    let out = `<table>`
    out += `<tr><th>Path</th><th>Message</th></tr>`
    out += errors.map(ErrorItem).join('')
    out += `</table>`
    out += ErrorsPagerItem()
    return out
}

const ErrorsPagerItem = _ => {
    if (model.errorsDisplay.errorsCount <= model.errorsDisplay.pageSize) return ""

    const offset = model.errorsDisplay.offset
    const pageSize = model.errorsDisplay.pageSize
    const errorsCount = model.errorsDisplay.errorsCount
    const nbEdgePages = 2
    const nbSiblingPages = 1

    const pageIdx = offset / pageSize + 1
    const lastPage = Math.ceil(errorsCount / pageSize)

    const displayedIdx = []
    for (let i=1; i<=lastPage; i++) {
        if (i <= nbEdgePages || i > lastPage - nbEdgePages || (i >= pageIdx - nbSiblingPages && i <= pageIdx + nbSiblingPages))
            displayedIdx.push(i)
    }

    let out = `<div class="resultsPager">`
    out += displayedIdx.map((idx) => ErrorPagerLinkItem(idx, (idx-1)*pageSize, pageIdx)).join('')
    out += `</div>`
    return out
}

const ErrorPagerLinkItem = (idx, offset, currentPageIdx) => {
    if (idx === currentPageIdx)
        return `<a href="#" onclick="return false" class="current">${idx}</a>`;
    else
        return `<a href="#" onclick="displayScanResults(${offset});return false">${idx}</a>`;
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
    jQuery(".checkEnabled").prop("checked", value);
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
            const report = data.integrity.scan.report
            if (report != null && report.length > 0) {
                const filename = report.slice(report.lastIndexOf('/') + 1)
                reportFileDiv.html(ReportFileItem(filename, report, urlContext, urlFiles)).show()
            }
        }
    }, _ => STOP_PULLING_LOGS);
}

function setupLogsLoader(executionID) {
    if (logsLoader !== null) clearInterval(logsLoader);
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

const ExcludedPathItem = ({path}) => `<span class="excludedPath" path="${path}">${path}</span>`

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
            loadScanResultsList()
            break
        default:
    }
}

function loadScanResultsList() {
    gqlCall(getScanResultsList(), (data) => {
        jQuery("#resultsSelector").html(ScanResultsSelectorItem(data.integrity.scanResults))
        jQuery("#" + constants.resultsPanel.selectID).change(_ => displayScanResults())
        if (model.errorsDisplay.resultsID === undefined) displayScanResults()
    })
}

function displayScanResults(offset, pageSize) {
    model.errorsDisplay.resultsID = jQuery("#" + constants.resultsPanel.selectID).val()
    if (model.errorsDisplay.resultsID === null) return
    model.errorsDisplay.offset = offset === undefined ? 0 : offset
    if (pageSize !== undefined) {
        model.errorsDisplay.pageSize = pageSize
    }
    gqlCall(getScanResults(), (data) => {
        let out = ""
        const results = data.integrity.results
        if (results !== null) {
            model.errorsDisplay.errorsCount = results.errorCount
            out += ErrorsListItem(results.errors)

            const reportFilePath = results.reportFilePath
            if (reportFilePath !== null) {
                out += ReportFileItem(results.reportFileName, results.reportFilePath, urlContext, urlFiles)
            }
        }
        jQuery("#resultsDetails").html(out)
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
    addPanelListener()
});