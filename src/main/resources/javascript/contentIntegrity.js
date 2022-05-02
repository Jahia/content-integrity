const RUNNING = "running";
var logsLoader;

const gqlConfig = {
    query: "{" +
        "  integrity:contentIntegrity {" +
        "    checks:integrityChecks {" +
        "      id enabled configurations {name, value}" +
        "    }" +
        "  }" +
        "}"
}

function getScanQuery(rootPath, workspace, checks) {
    return {
        query: "query ($path: String!, $ws: WorkspaceToScan!, $checks: [String]) {" +
            "  integrity:contentIntegrity {" +
            "    scanner:integrityScan {" +
            "      scan(startNode: $path, workspace: $ws, checksToRun: $checks, uploadResults: true)" +
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

function gqlCall(query, successCB, failureCB) {
    jQuery.ajax({
        url: '/modules/graphql',
        type: 'POST',
        contentType: "application/json",
        data: JSON.stringify(query),
        success: function (result) {
            if (result.errors != null) {
                console.log("Error while loading the data", result.errors);
                if (failureCB !== undefined) failureCB();
                return;
            }
            if (result.data == null) {
                if (failureCB !== undefined) failureCB();
                return;
            }
            successCB(result.data);
        }
    })
}

const STOP_PULLING_LOGS = _ => clearInterval(logsLoader);

function loadConfigurations() {
    gqlCall(gqlConfig, (data) => renderConfigurations(data.integrity.checks))
}

function renderConfigurations(data) {
    const conf = []
    jQuery.each(data, function (index) {
        conf[index] = {
            name: this.id,
            id: this.id,
            enabled: this.enabled
        }
    })
    const Item = ({id, enabled, name}) => `<p><input id="${id}" class="checkEnabled" type="checkbox" ${enabled ? checked="checked" : ""}/>${name}</p>`;
    jQuery('#configurations').html(conf.map(Item).join(''));
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
        if (data.integrity.scan.status !== RUNNING) STOP_PULLING_LOGS()
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

jQuery(document).ready(function () {
    loadConfigurations();
    jQuery("#runScan").click(function () {
        const rootPath = jQuery("#rootNode").val();
        const workspace = jQuery("#workspace").val();

        const checks = jQuery.map(jQuery(".checkEnabled:checked"), function (cb, i) {
            return jQuery(cb).attr("id")
        })

        gqlCall(getScanQuery(rootPath, workspace, checks), (data) => setupLogsLoader(data.integrity.scanner.scan));
    });
    wireToRunningScan();
});

/*
function contentIntegrity (site, workspace, language) {

	$.ajax({
		url: "/cms/render/"+workspace+"/"+language+"/sites/"+site+"/home.verifyIntegrityOfSiteContent.do",
		context: document.body,
		dataType: "json"
	}).done(function(data) {
		parseIntegrityActionFeedback(data);
	});
}

function displayErrors(json) {

	$("#errorDisplay").html("");

	$("#errorDisplay").append($('<tr>')
			.append($('<td colspan=3 class="errorDisplay">')
				.append(json.numberOfErrors + " error(s) detected")
		)
	);

	$("#errorDisplay").append($('<tr>')
			.append($('<td class="errorDisplay">')
				.append("Path")
		)
			.append($('<td class="errorDisplay">')
				.append("Property name")
		)
			.append($('<td class="errorDisplay">')
				.append("Type of error")
		)
	);

	if (json.numberOfErrors > 0) {
		for (var i=0; i<json.errors.length; i++) {
			var error = json.errors[i];

			$("#errorDisplay").append($('<tr>')
					.append($('<td>')
						.append(error.path)
				)
					.append($('<td>')
						.append(error.propertyName)
				)
					.append($('<td>')
						.append(error.constraintMessage)
				)
			);
		}
	}
}

function parseIntegrityActionFeedback(json) {
	if(json.siteContentIsValid == "false") {
		displayErrors(json);
	} else {
		alert("No integrity error detected.");
	}
}

$( document ).ready(function() {
	$( "#verifyButton" ).click(function() {
		contentIntegrity($("#currentSite").val(),$("#currentWorkspace").val(),$("#currentLanguage").val());
	});
});
 */