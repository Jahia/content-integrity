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