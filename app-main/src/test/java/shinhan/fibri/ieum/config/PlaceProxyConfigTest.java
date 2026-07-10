package shinhan.fibri.ieum.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PlaceProxyConfigTest {

	@Test
	void rejectsBlankProviderCredentialsAtStartup() {
		assertThatThrownBy(() -> new PlaceProxyProperties(
			"https://openapi.naver.com", "", "client-secret",
			"https://maps.apigw.ntruss.com", "key-id", "key"
		))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("naverSearchClientId");
	}
}
