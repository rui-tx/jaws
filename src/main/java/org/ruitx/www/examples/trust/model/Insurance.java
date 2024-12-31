package org.ruitx.www.examples.trust.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public record Insurance(
        @JsonProperty("id")
        Integer id,
        @JsonProperty("insurance_policy_id")
        String insurancePolicyId,
        @JsonProperty("company_name")
        String companyName,
        @JsonProperty("agent_name")
        String agentName) {
}
