package shinhan.fibri.ieum.main.meeting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;
import shinhan.fibri.ieum.main.meeting.repository.MeetingScheduleRepository;

class MeetingScheduleMaintenanceServiceTest {

	private final MeetingScheduleRepository repository = mock(MeetingScheduleRepository.class);
	private final MeetingScheduleMaintenanceService service = new MeetingScheduleMaintenanceService(repository);

	@Test
	void completeExpiredSchedulesDelegatesVisibleUntilBulkUpdate() {
		OffsetDateTime now = OffsetDateTime.parse("2026-07-10T00:00:00+09:00");
		when(repository.completeExpiredSchedules(now)).thenReturn(2);

		int updated = service.completeExpiredSchedules(now);

		assertThat(updated).isEqualTo(2);
		verify(repository).completeExpiredSchedules(now);
	}
}
