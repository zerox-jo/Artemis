package de.tum.in.www1.artemis.service.connectors.localci;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record LocalCIBuildPlan(String key, String submissionRepositoryUrl, String testRepositoryUrl) {
}
