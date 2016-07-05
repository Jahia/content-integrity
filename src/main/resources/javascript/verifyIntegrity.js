function verifyIntegrity (site, workspace, language) {
	$.ajax({
		url: "/cms/edit/"+workspace+"/"+language+"/sites/"+site+"/home.verifyIntegrityOfSiteContent.do",
		context: document.body,
		dataType: "json"
	}).done(function(data) {
		parseIntegrityActionFeedback(data);
	});
}

function displayErrors(json) {
	$("#errorDisplay").html("");
	errorArray = json.errors.split("\n");

	$("#errorDisplay").append($('<tr>')
		.append($('<td colspan=3 class="errorDisplay">')
			.append(json.numberOfErrors + " error(s) detected")
		)
	);

	for (var i = 0; i < errorArray.length-1; i++) {

		error = errorArray[i].split(":");
		nodetype = error[0].split(" ");
		$("#errorDisplay").append($('<tr>')
			.append($('<td>')
				.append(nodetype[0])
			)
			.append($('<td>')
				.append(nodetype[1])
			)
			.append($('<td>')
				.append(error[1])
			)
		);
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
		verifyIntegrity($("#currentSite").val(),$("#currentWorkspace").val(),$("#currentLanguage").val());
	});
});