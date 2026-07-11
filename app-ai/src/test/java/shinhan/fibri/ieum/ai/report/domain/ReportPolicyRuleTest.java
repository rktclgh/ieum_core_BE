package shinhan.fibri.ieum.ai.report.domain;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ReportPolicyRuleTest {

	@Test
	void acceptsAHighSeveritySuspendRule() {
		assertThatCode(() -> rule(ReportPolicyDecision.suspend, ReportPolicySeverity.high)).doesNotThrowAnyException();
	}

	@Test
	void rejectsSuspendRuleBelowHighSeverity() {
		assertThatThrownBy(() -> rule(ReportPolicyDecision.suspend, ReportPolicySeverity.medium))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("suspend");
	}

	@Test
	void rejectsNormalRuleAboveLowSeverity() {
		assertThatThrownBy(() -> rule(ReportPolicyDecision.normal, ReportPolicySeverity.medium))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("normal");
	}

	private ReportPolicyRule rule(ReportPolicyDecision decision, ReportPolicySeverity severity) {
		return new ReportPolicyRule(
			"CONTENT-ABUSE-001",
			"abuse",
			decision,
			severity,
			new BigDecimal("0.8500"),
			ReportEvidenceType.text,
			100,
			1
		);
	}
}
