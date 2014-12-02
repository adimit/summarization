/**
 * 
 */
package dimitrov.sum;

/**
 * @author aleks
 *
 */
public class ContextInitializationException extends Exception {

	/**
	 * 
	 */
	private static final long serialVersionUID = 2115265769714499370L;

	/**
	 * 
	 */
	public ContextInitializationException() {
		super("Very bad. Tell Aleks to fix his code!");
	}

	/**
	 * @param message
	 */
	public ContextInitializationException(String message) {
		super(message);
	}

	/**
	 * @param cause
	 */
	public ContextInitializationException(Throwable cause) {
		super(cause);
	}

	/**
	 * @param message
	 * @param cause
	 */
	public ContextInitializationException(String message, Throwable cause) {
		super(message, cause);
	}

	/**
	 * @param message
	 * @param cause
	 * @param enableSuppression
	 * @param writableStackTrace
	 */
	public ContextInitializationException(String message, Throwable cause,
			boolean enableSuppression, boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}
}
