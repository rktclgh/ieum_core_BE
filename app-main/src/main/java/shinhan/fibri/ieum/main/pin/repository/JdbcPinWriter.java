package shinhan.fibri.ieum.main.pin.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import shinhan.fibri.ieum.main.pin.domain.PinType;

@Repository
@RequiredArgsConstructor
public class JdbcPinWriter implements PinWriter {

	private final JdbcTemplate jdbcTemplate;

	@Override
	public Long create(Long authorId, PinType type, double latitude, double longitude) {
		return jdbcTemplate.queryForObject(
			"""
				INSERT INTO pins (author_id, pin_type, location)
				VALUES (?, CAST(? AS pin_type), ST_SetSRID(ST_MakePoint(?, ?), 4326)::geography)
				RETURNING pin_id
				""",
			Long.class,
			authorId,
			type.name(),
			longitude,
			latitude
		);
	}
}
