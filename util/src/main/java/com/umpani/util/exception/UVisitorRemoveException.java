package com.umpani.util.exception;

/**
 * An exception that can be thrown by a visitor to ask the visiting method to remove the just visited item from the 
 * visited object. This is the fasted way to do this as modifying the visited object directly requires an 
 * {@link UVisitorModifiedException} to be thrown by the visitor and this may be much more expensive then this 
 * exception.
 * 
 * </p><p>This exception guarantees that no item is visited twice.
 */
@SuppressWarnings("serial")
public class UVisitorRemoveException extends UVisitorException {}