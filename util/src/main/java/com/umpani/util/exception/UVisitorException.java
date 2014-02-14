package com.umpani.util.exception;

/**
 * An exception that can be thrown by an visitor to signal something to the visiting method. This exception and all 
 * derived will not produce stack traces for performance reasons.
 */
@SuppressWarnings("serial")
public abstract class UVisitorException extends Exception {
	/**
	 * 	Creates a exception to signal something to the visitor.
	 */
	public UVisitorException() {}

	@Override
	public Throwable fillInStackTrace() {
		return this;
	}

	/**
	 * This method shall be invoked if the underlying visiting method has caught an unknown {@link UVisitorException}. 
	 * This method will throw an {@link IllegalStateException} with a unique message text so that all visiting methods 
	 * throw the same error for the same reason.
	 * @throws IllegalStateException
	 * throws this exception.
	 */
	public final void unknow() throws IllegalStateException {
		throw new IllegalStateException("Caught unknown visitor exception",this);
	}
}