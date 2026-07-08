package shinhan.fibri.ieum.main.question.repository;

import java.time.OffsetDateTime;
import java.util.UUID;

public interface MyQuestionItemProjection {

	Long getQuestionId();

	String getTitle();

	boolean getResolved();

	UUID getThumbnailFileId();

	int getAnswerCount();

	OffsetDateTime getCreatedAt();
}
