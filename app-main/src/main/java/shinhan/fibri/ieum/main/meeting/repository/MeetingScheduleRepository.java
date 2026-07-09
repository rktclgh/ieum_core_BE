package shinhan.fibri.ieum.main.meeting.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import shinhan.fibri.ieum.main.meeting.domain.MeetingSchedule;

public interface MeetingScheduleRepository extends JpaRepository<MeetingSchedule, Long> {
}
