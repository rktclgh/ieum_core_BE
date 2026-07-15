package shinhan.fibri.ieum.main.admin.user.exception;

public class CannotChangeOwnRoleException extends RuntimeException {

	public CannotChangeOwnRoleException() {
		super("Administrators cannot change their own role");
	}
}
