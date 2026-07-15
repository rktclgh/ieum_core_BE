package shinhan.fibri.ieum.main.admin.user.exception;

public class LastAdminRequiredException extends RuntimeException {

	public LastAdminRequiredException() {
		super("At least one administrator is required");
	}
}
