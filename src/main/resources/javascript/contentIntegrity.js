const RUNNING = "running";
let logsLoader;
const STOP_PULLING_LOGS = _ => clearInterval(logsLoader);

const gqlConfig = {
    query: "{" +
        "  integrity:contentIntegrity {" +
        "    checks:integrityChecks {" +
        "      id enabled configurable configurations " +
        "        { defaultValue description name type value }" +
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
            "            id status logs" +
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
            "integrityCheckById(id : " + '"' + checkID + '"' + ") {" +
            updates +
            "}" +
            "}}"
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
    if (configurable) out += `<a class="configureLink" title="configure" dialogID="configure-${id}">Configure</a>`;
    out += `</span>`;
    return out;
}

const ConfigPanelItem = ({id, name, configurations}) => {
    let out = `<div class="configurationPanel" id="configure-${id}" integrityCheckID="${id}">${name}<div>`;
    out += configurations.map(ConfigItem).join('');
    out += `</div></div>`;
    return out;
}

const ConfigItem = ({name, type, value}) => {
    const params = { name: name, value: value }
    switch (type) {
        case "integer":
            return IntegerConfigItem(params);
        case "boolean":
            return BooleanConfigItem(params);
        default:
            console.error("Unsupported type '", type, "' for the configuration '", name, "'")
    }
}

const IntegerConfigItem = ({name, value}) => `${name}: <input type="text" name="${name}" value="${value}" />`

const BooleanConfigItem = ({name, value}) => {
    let out = `${name}: <input type="checkbox" name="${name}"`
    if (value === "true") out += ` checked="checked"`
    out += `/>`
    return out
}

function renderConfigurations(data) {
    const conf = []
    jQuery.each(data, function (index) {
        conf[index] = {
            name: this.id,
            id: this.id,
            enabled: this.enabled,
            configurable: this.configurable,
            configurations: this.configurations
        }
    })
    jQuery('#configurations').html(conf.map(IntegrityCheckItem).join(''));
    jQuery("#configurationPanels").html(conf.filter(value => {return value.configurable}).map(ConfigPanelItem).join(''));
    jQuery('.configurationPanel').dialog({
        autoOpen: false,
        resizable: false,
        height: "auto",
        width: 800,
        modal: true,
        buttons: {
            "Save": function () {
                const confs = jQuery(this).find("input").map(function() {
                    return ({
                        name: this.name,
                        value: this.type === "checkbox" ? (this.checked ? "true" : "false") : this.value
                    });
                });
                saveConfigurations(jQuery(this).attr("integrityCheckID"), jQuery.makeArray(confs))
                jQuery(this).dialog("close");
            },
            "Reset to default values": function () {
                jQuery(this).dialog("close");
            },
            Cancel: function () {
                jQuery(this).dialog("close");
            }
        },
        title: "Configure"
    });
    jQuery('.configureLink').on("click", function () {
        const id = "#" + jQuery(this).attr("dialogID");
        jQuery(id).dialog("open");
    });
}

function saveConfigurations(checkID, confs) {
    gqlCall(getSaveConfsQuery(checkID, confs))
}

function selectAllChecks(value) {
    jQuery(".checkEnabled").prop("checked", value);
}

function renderLogs(executionID) {
    gqlCall(getLogsQuery(executionID), (data) => {
        const block = jQuery("#logs")
        block.html("")
        jQuery.each(data.integrity.scan.logs, function () {
            block.append(this+"\n")
        })
        if (data.integrity.scan.status === RUNNING) {
            showStopButton(true);
        } else {
            STOP_PULLING_LOGS();
            showStopButton(false);
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