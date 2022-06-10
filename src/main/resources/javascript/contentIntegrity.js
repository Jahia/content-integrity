const RUNNING = "running";
let logsLoader;
const STOP_PULLING_LOGS = _ => clearInterval(logsLoader);

const gqlConfig = {
    query: "{" +
        "  integrity:contentIntegrity {" +
        "    checks:integrityChecks {" +
        "      id enabled configurable" +
        "    }" +
        "  }" +
        "}"
}

function getScanQuery(rootPath, workspace, checks) {
    return {
        query: "query ($path: String!, $ws: WorkspaceToScan!, $checks: [String]) {" +
            "  integrity:contentIntegrity {" +
            "    scan:integrityScan {" +
            "      id:scan(startNode: $path, workspace: $ws, checksToRun: $checks, uploadResults: true)" +
            "    }" +
            "  }" +
            "}",
        variables: {path: rootPath, ws: workspace, checks: checks}
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

function getStopScanQuery() {
    return {
        query: "{" +
            "    integrity:contentIntegrity {" +
            "        stopRunningScan" +
            "    }" +
            "}"
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

function escapeConfigName(name) {
    return "configure_" + name.replaceAll("-", "_")
}

function gqlCall(query, successCB, failureCB) {
    jQuery.ajax({
        url: '/modules/graphql',
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

const IntegrityCheckItem = ({id, enabled, name, configurable}) => {
    let out = `<span class="config">`;
    out += `<input id="${id}" class="checkEnabled" type="checkbox" ${enabled ? checked = "checked" : ""}/>${name}`;
    if (configurable) out += ConfigureButtonItem(id, moduleContentIntegrityURL)
    out += `</span>`;
    return out;
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

function renderConfigurations(data) {
    const conf = []
    jQuery.each(data, function (index) {
        conf[index] = {
            name: this.id,
            id: this.id,
            enabled: this.enabled,
            configurable: this.configurable
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
        const block = jQuery("#logs")
        block.html("")
        jQuery.each(data.integrity.scan.logs, function () {
            block.append(this+"\n")
        })
        block.scrollTop(block[0].scrollHeight)
        if (data.integrity.scan.status === RUNNING) {
            showStopButton(true);
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

function showStopButton(visible) {
    if (visible) {
        jQuery("#runScan").attr("disabled", "disabled");
        jQuery("#stopScan").show();
    }
    else {
        jQuery("#runScan").removeAttr("disabled");
        jQuery("#stopScan").hide();
    }
}

jQuery(document).ready(function () {
    loadConfigurations();
    jQuery("#runScan").click(function () {
        const rootPath = jQuery("#rootNode").val();
        const workspace = jQuery("#workspace").val();

        const checks = jQuery.map(jQuery(".checkEnabled:checked"), function (cb, i) {
            return jQuery(cb).attr("id")
        })

        gqlCall(getScanQuery(rootPath, workspace, checks), (data) => setupLogsLoader(data.integrity.scan.id));
    });
    jQuery("#stopScan").click(function () {
        gqlCall(getStopScanQuery(), _ => showStopButton(false))
    });
    wireToRunningScan();
});