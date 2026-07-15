package shinhan.fibri.ieum.main.admin.user.exception;

public class AdminRoleRequiredException extends RuntimeException {

	public AdminRoleRequiredException() {
		super("Administrator role is required");
	}
}
