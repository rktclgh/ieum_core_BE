package shinhan.fibri.ieum.ai.report.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.regex.Pattern;

public record ReportPolicyRule(
	String ruleCode,
	String category,
	ReportPolicyDecision decision,
	ReportPolicySeverity severity,
	BigDecimal minConfidence,
	ReportEvidenceType evidenceType,
	int priority,
	int revision
) {

	private static final Pattern RULE_CODE = Pattern.compile("^[A-Z0-9][A-Z0-9_-]{2,99}$");

	public ReportPolicyRule {
		if (ruleCode == null || !RULE_CODE.matcher(ruleCode).matches()) {
			throw new IllegalArgumentException("ruleCode must be an uppercase policy code");
		}
		if (category == null || category.isBlank()) {
			throw new IllegalArgumentException("category must not be blank");
		}
		Objects.requireNonNull(decision, "decision must not be null");
		Objects.requireNonNull(severity, "severity must not be null");
		Objects.requireNonNull(evidenceType, "evidenceType must not be null");
		if (minConfidence == null || minConfidence.compareTo(BigDecimal.ZERO) < 0 || minConfidence.compareTo(BigDecimal.ONE) > 0) {
			throw new IllegalArgumentException("minConfidence must be between 0 and 1");
		}
		if (priority < -1000 || priority > 1000) {
			throw new IllegalArgumentException("priority must be between -1000 and 1000");
		}
		if (revision < 1) {
			throw new IllegalArgumentException("revision must be positive");
		}
		if (decision == ReportPolicyDecision.suspend && !severity.isAtLeastHigh()) {
			throw new IllegalArgumentException("suspend rules require high or critical severity");
		}
		if (decision == ReportPolicyDecision.normal && severity != ReportPolicySeverity.low) {
			throw new IllegalArgumentException("normal rules require low severity");
		}
	}
}
