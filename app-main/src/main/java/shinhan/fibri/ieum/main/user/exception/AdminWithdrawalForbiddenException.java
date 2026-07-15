package shinhan.fibri.ieum.main.user.exception;

public class AdminWithdrawalForbiddenException extends RuntimeException {

	public AdminWithdrawalForbiddenException() {
		super("Administrator accounts must be demoted before withdrawal");
	}
}
