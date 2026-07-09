package shinhan.fibri.ieum.main.meeting.service;

import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import shinhan.fibri.ieum.main.meeting.repository.MeetingScheduleRepository;

@Service
@RequiredArgsConstructor
public class MeetingScheduleMaintenanceService {

	private final MeetingScheduleRepository meetingScheduleRepository;

	@Transactional
	public int completeExpiredSchedules(OffsetDateTime now) {
		return meetingScheduleRepository.completeExpiredSchedules(now);
	}
}
