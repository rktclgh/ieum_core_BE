package shinhan.fibri.ieum.main.meeting.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import shinhan.fibri.ieum.main.meeting.domain.MeetingType;

public record CreateMeetingRequest(
	@NotBlank @Size(max = 200) String title,
	@Size(max = 2000) String content,
	@NotNull MeetingType type,
	@NotBlank @Size(max = 100) String placeName,
	@NotNull @Valid CreateMeetingScheduleRequest schedule,
	@Valid CreateMeetingRecurrenceRuleRequest recurrenceRule,
	@NotNull @Min(2) @Max(99) Integer maxMembers,
	@NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double lat,
	@NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double lng,
	UUID imageFileId
) {
}
