package com.umpani.util.exception;

import com.umpani.util.UType;

/**
 * An exception that is being thrown whenever it was tried to modify an sealed object.
 */
@SuppressWarnings("serial")
public class UReadOnlyException extends RuntimeException {
	/**
	 * Creates a new sealed exception.
	 * @param cause
	 * the object that caused the violation.
	 * @param causingMethod
	 * the name of the method that was invoked and causing this error.
	 * @param reason
	 * the object that should be modified, but which is sealed.
	 */
	public UReadOnlyException( Object cause, String causingMethod, UType reason ) {
		super("A sealed Json was target of an modification what is not allowed");
		this.cause = cause;
		this.causingMethod = causingMethod;
		this.reason = reason;
	}
	
	/**
	 * The object that should be modified, but which is sealed.
	 */
	public final UType reason;

	/**
	 * The object (instance) or class (static method) that caused the violation.
	 */
	public final Object cause;
	
	/**
	 * The name of the method that was invoked and causing this error.
	 */
	public final String causingMethod;
}