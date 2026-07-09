package shinhan.fibri.ieum.main.pin.repository;

import java.time.OffsetDateTime;
import shinhan.fibri.ieum.main.pin.domain.PinType;

public interface PinWriter {

	Long create(Long authorId, PinType type, double latitude, double longitude);

	void softDelete(Long pinId, OffsetDateTime deletedAt);
}
