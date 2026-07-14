package shinhan.fibri.ieum.main.report.ai.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.converter.HttpMessageConversionException;

class ReportAiRetryPolicyTest {

	@Test
	void treatsAnInvalidSuccessResponseAsPermanent() {
		ReportAiFailureDisposition disposition = new ReportAiRetryPolicy().classify(
			new HttpMessageConversionException("safe response decoding failure"),
			1,
			5
		);

		assertThat(disposition.errorCode()).isEqualTo("REPORT_AI_RESPONSE_INVALID");
		assertThat(disposition.retryable()).isFalse();
	}
}
