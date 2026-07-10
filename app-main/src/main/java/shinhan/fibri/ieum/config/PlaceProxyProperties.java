package shinhan.fibri.ieum.config;

public record PlaceProxyProperties(
	String naverSearchBaseUrl,
	String naverSearchClientId,
	String naverSearchClientSecret,
	String ncpMapsBaseUrl,
	String ncpMapsKeyId,
	String ncpMapsKey
) {
	public PlaceProxyProperties {
		requireNonBlank(naverSearchBaseUrl, "naverSearchBaseUrl");
		requireNonBlank(naverSearchClientId, "naverSearchClientId");
		requireNonBlank(naverSearchClientSecret, "naverSearchClientSecret");
		requireNonBlank(ncpMapsBaseUrl, "ncpMapsBaseUrl");
		requireNonBlank(ncpMapsKeyId, "ncpMapsKeyId");
		requireNonBlank(ncpMapsKey, "ncpMapsKey");
	}

	private static void requireNonBlank(String value, String name) {
		if (value == null || value.isBlank()) {
			throw new IllegalStateException(name + " must not be blank");
		}
	}
}
