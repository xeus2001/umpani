package com.umpani.util.exception;

/**
 * An exception that can be thrown by an visitor to signal something to the visiting method. This exception and all 
 * derived will not produce stack traces for performance reasons.
 */
@SuppressWarnings("serial")
public abstract class UVisitorException extends Exception {
	/**
	 * Constructs a new exception to signal something to the visitor.
	 */
	public UVisitorException() {
		super(null,null,false,true);
	}

    /**
     * Constructs a new exception to signal something to the visitor with the specified detail message, cause and
     * optionally a stack trace or not.
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
    public UVisitorException(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }

	@Override
	public Throwable fillInStackTrace() {
		return this;
	}
}