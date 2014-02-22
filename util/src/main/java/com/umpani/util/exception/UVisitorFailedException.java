package com.umpani.util.exception;

/**
 * An exception that can be thrown if visiting failed for some reason.
 *
 * @author Alexander Weber <xeus2001@gmail.com>
 */
@SuppressWarnings("serial")
public class UVisitorFailedException extends RuntimeException {
	/**
	 * Constructs a new exception to signal something to the visitor that something has failed with the specified 
	 * cause. Suppression is disabled and stack trace is enabled.
	 *
	 * @param cause
	 * the cause. (A {@code null} value is permitted, and indicates that the cause is nonexistent or unknown.)
	 */
	public UVisitorFailedException(Throwable cause) {
		super(null, cause, false, true);
	}

	/**
	 * Constructs a new exception to signal something to the visitor that something has failed with the specified 
	 * detail message. Suppression is disabled and stack trace is enabled.
	 *
	 * @param message
	 * the detail message.
	 */
	public UVisitorFailedException(String message) {
		super(message, null, false, true);
	}

	/**
	 * Constructs a new exception to signal something to the visitor that something has failed with the specified 
	 * detail message and cause. Suppression is disabled and stack trace is enabled.
	 *
	 * @param message
	 * the detail message.
	 * @param cause
	 * the cause. (A {@code null} value is permitted, and indicates that the cause is nonexistent or unknown.)
	 */
	public UVisitorFailedException(String message, Throwable cause) {
		super(message, cause, false, true);
	}

	/**
	 * Constructs a new exception to signal something to the visitor that something has failed with the specified 
	 * detail message, cause and optionally a stack trace or not. Suppression is disabled.
	 *
	 * @param message
	 * the detail message.
	 * @param cause
	 * the cause. (A {@code null} value is permitted, and indicates that the cause is nonexistent or unknown.)
	 * @param writableStackTrace
	 * whether or not the stack trace should be writable.
	 */
	public UVisitorFailedException(String message, Throwable cause, boolean writableStackTrace) {
		super(message, cause, false, writableStackTrace);
	}

	/**
	 * Constructs a new exception to signal something to the visitor that something has failed with the specified 
	 * detail message, cause and optionally a stack trace or not.
	 *
	 * @param message
	 * the detail message.
	 * @param cause
	 * the cause. (A {@code null} value is permitted, and indicates that the cause is nonexistent or unknown.)
	 * @param enableSuppression
	 * whether or not suppression is enabled or disabled.
	 * @param writableStackTrace
	 * whether or not the stack trace should be writable.
	 */
	public UVisitorFailedException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
		super(message, cause, enableSuppression, writableStackTrace);
	}
}
