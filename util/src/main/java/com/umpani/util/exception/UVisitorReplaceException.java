package com.umpani.util.exception;

/**
 * An exception that can be thrown by an visitor to replace the value of the just visited entity. The method guarantees
 * no side effects, which means that the element is not visited again and no other issues will arise. If the visited
 * object is a map, this method will replace the value and not the key.
 */
@SuppressWarnings("serial")
public class UVisitorReplaceException extends UVisitorException {
	/**
	 * Creates a new replace exception.
	 * @param replacement
	 * the replacement value.
	 */
	public UVisitorReplaceException( final Object replacement ) {
		this.replacement = replacement;
	}
	
	/**
	 * The value with which the value of the last visited item should be replaced.
	 */
	public final Object replacement;
}