function interestingType(type) {
	if(type == "Code_Defect") {
		return 1
	}
	if(type == "Feature_Request") {
		return 1
	}
	return 0
}

function interestingStatus(status) {
	if(status == "Fixed") {
		return 1
	}
	return 0
}

{ 
	if(interestingType($4) == 1 && interestingStatus($5) == 1) {
		printf "\t%s %s --- %s\n", $4, $5, $6
	}	
}
