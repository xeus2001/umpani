package com.umpani.util.exception;

/**
 * An exception that can be thrown by an visitor to signal the visiting method that it modified the visited object. Not 
 * doing so may cause an undefined behave with very weird side effects, because the visited object might have been 
 * re-indexed or modified in other ways and due to these changes the references currently cached at the stack of the 
 * visiting method may have become invalid and need to be updated.
 * 
 * </p><p>Throwing this exception may have the effect that already visited items are visited again.
 */
@SuppressWarnings("serial")
public class UVisitorModifiedException extends UVisitorException {}