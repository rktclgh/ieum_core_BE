package shinhan.fibri.ieum.main.meeting.dto;

import java.time.OffsetDateTime;

public record MeetingDetailResponse(
	Long meetingId,
	Long pinId,
	Long roomId,
	String title,
	String content,
	String placeName,
	OffsetDateTime meetingAt,
	String type,
	boolean active,
	MeetingScheduleItem nextSchedule,
	MeetingDetailRecurrenceRuleResponse recurrenceRule,
	String status,
	int maxMembers,
	long participantCount,
	MeetingHostSummary host,
	String imageUrl,
	String thumbnailUrl,
	MeetingLocation location,
	String myStatus,
	OffsetDateTime createdAt
) {
	public MeetingDetailResponse(
		Long meetingId,
		Long pinId,
		Long roomId,
		String title,
		String content,
		String placeName,
		OffsetDateTime meetingAt,
		String status,
		int maxMembers,
		long participantCount,
		MeetingHostSummary host,
		String imageUrl,
		String thumbnailUrl,
		MeetingLocation location,
		String myStatus,
		OffsetDateTime createdAt
	) {
		this(
			meetingId,
			pinId,
			roomId,
			title,
			content,
			placeName,
			meetingAt,
			"one_time",
			false,
			null,
			null,
			status,
			maxMembers,
			participantCount,
			host,
			imageUrl,
			thumbnailUrl,
			location,
			myStatus,
			createdAt
		);
	}
}
