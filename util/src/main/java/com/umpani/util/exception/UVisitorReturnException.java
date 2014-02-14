package com.umpani.util.exception;

/**
 * An exception that, if thrown by an visitor, will stop the visiting. The visiting method will return the provided 
 * result to the caller.
 */
@SuppressWarnings("serial")
public class UVisitorReturnException extends UVisitorException {
	/**
	 * Create a new return exception.
	 * @param result
	 * the result to be returned by the visit method.
	 */
	public UVisitorReturnException( final Object result ) {
		this.result = result;
	}

	/**
	 * The result of the visit.
	 */
	public final Object result;
}