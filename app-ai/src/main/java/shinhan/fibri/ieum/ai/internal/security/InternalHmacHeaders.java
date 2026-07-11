package shinhan.fibri.ieum.ai.internal.security;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class InternalHmacHeaders {

	private final Map<String, List<String>> valuesByLowerName = new LinkedHashMap<>();

	public void add(String name, String value) {
		valuesByLowerName.computeIfAbsent(name.toLowerCase(Locale.ROOT), unused -> new ArrayList<>()).add(value);
	}

	String singleValue(String name) {
		List<String> values = valuesByLowerName.get(name.toLowerCase(Locale.ROOT));
		if (values == null || values.size() != 1) {
			return null;
		}
		String value = values.getFirst();
		if (value == null || value.isBlank()) {
			return null;
		}
		return value;
	}

	boolean hasExactlyOneValue(String name) {
		List<String> values = valuesByLowerName.get(name.toLowerCase(Locale.ROOT));
		return values != null && values.size() == 1 && values.getFirst() != null && !values.getFirst().isBlank();
	}

}
