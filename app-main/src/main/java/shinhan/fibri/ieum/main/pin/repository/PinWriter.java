package shinhan.fibri.ieum.main.pin.repository;

import shinhan.fibri.ieum.main.pin.domain.PinType;

public interface PinWriter {

	Long create(Long authorId, PinType type, double latitude, double longitude);
}
