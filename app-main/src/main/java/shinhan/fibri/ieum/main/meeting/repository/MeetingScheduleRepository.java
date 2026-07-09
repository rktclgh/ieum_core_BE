package shinhan.fibri.ieum.main.meeting.repository;

import java.time.OffsetDateTime;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import shinhan.fibri.ieum.main.meeting.domain.MeetingSchedule;

public interface MeetingScheduleRepository extends JpaRepository<MeetingSchedule, Long> {

	@Query("""
		select count(schedule) > 0
		from MeetingSchedule schedule
		where schedule.meetingId = :meetingId
			and schedule.status = shinhan.fibri.ieum.main.meeting.domain.MeetingScheduleStatus.scheduled
			and schedule.visibleUntil >= :now
			and schedule.deletedAt is null
		""")
	boolean existsActiveSchedule(@Param("meetingId") Long meetingId, @Param("now") OffsetDateTime now);
}
