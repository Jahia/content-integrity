var logsLoader;

const gqlConfig = {
    query: "{" +
        "  integrity {" +
        "    integrityChecks {" +
        "      id configurations {name, value}" +
        "    }" +
        "  }" +
        "}"
}

function getScanQuery(rootPath, workspace) {
    return {
        query: "query ($path: String!, $ws: WorkspaceToScan!) {" +
            "  integrity {" +
            "    integrityScan {" +
            "      scan(startNode: $path, workspace: $ws, uploadResults: true)" +
            "    }" +
            "  }" +
            "}",
        variables: {path: rootPath, ws: workspace}
    }
}

function getLogsQuery(executionID) {
    return {
        query: "query ($id : String!) {" +
            "    integrity {\n" +
            "        integrityScan {\n" +
            "            logs:execution (id: $id)\n" +
            "        }" +
            "    }" +
            "}",
        variables: {id: executionID}
    }
}

function loadConfigurations() {
    jQuery.ajax({
        url: '/modules/graphql',
        type: 'POST',
        contentType: "application/json",
        data: JSON.stringify(gqlConfig),
        success: function (result) {
            if (result.errors != null) {
                console.log("Error while loading the data", result.errors);
            }
            if (result.data == null) {
                return;
            }
            renderConfigurations(result.data.integrity.integrityChecks)
        }
    })
}

function renderConfigurations(data) {
    const block = jQuery("#configurations");
    block.html("<ul>");
    jQuery.each(data, function () {
        block.append("<li>" + this.id + "</li>")
    });
    block.append("</ul>");
}

function renderLogs(executionID) {
    if (logsLoader !== null) clearInterval(logsLoader);
    logsLoader = setInterval(function () {
        jQuery.ajax({
            url: '/modules/graphql',
            type: 'POST',
            contentType: "application/json",
            data: JSON.stringify(getLogsQuery(executionID)),
            success: function (result) {
                if (result.errors != null) {
                    console.log("Error while loading the data", result.errors);
                }
                if (result.data == null) {
                    return;
                }
                const block = jQuery("#logs")
                block.html("")
                jQuery.each(result.data.integrity.integrityScan.logs, function () {
                    block.append(this+"\n")
                })
            }
        })
    }, 5000)
}

jQuery(document).ready(function () {
    loadConfigurations();
    jQuery("#runScan").click(function () {
        const rootPath = jQuery("#rootNode").val();
        const workspace = jQuery("#workspace").val();

        jQuery.ajax({
            url: '/modules/graphql',
            type: 'POST',
            contentType: "application/json",
            data: JSON.stringify(getScanQuery(rootPath, workspace)),
            success: function (result) {
                if (result.errors != null) {
                    console.log("Error while loading the data", result.errors);
                }
                if (result.data == null) {
                    return;
                }
                renderLogs(result.data.integrity.integrityScan.scan);
            }
        })
    })
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