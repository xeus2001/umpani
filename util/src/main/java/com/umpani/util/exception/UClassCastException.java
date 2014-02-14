package com.umpani.util.exception;

/**
 * An exception that is thrown by the UMPANI framework if any casting failed.
 */
@SuppressWarnings("serial")
public class UClassCastException extends ClassCastException {
	/**
	 * If this property is true, then stack-traces are enabled, otherwise a UClassCastException will not create a 
	 * stack trace. It should be noted that stack traces are very expensive, therefore it may be helpful to disable
	 * them for UClassCastExceptions. By default stack traces are enabled.
	 */
	public static boolean ENABLE_STACKTRACE = true;

	/**
	 * Create a new class cast exception.
	 * @param object
	 * the object that should be casted.
	 * @param targetClass
	 * the class to which it should be casted and what failed.
	 */
	public UClassCastException( final Object object, final Class<?> targetClass ) {
		super("Failed to cast object to class");
		this.object = object;
		this.targetClass = targetClass;
	}

	/**
	 * The object that should have been casted.
	 */
	public final Object object;

	/**
	 * The class to which the object should have been casted, but what failed.
	 */
	public final Class<?> targetClass;

	@Override
	public Throwable fillInStackTrace() {
		if (ENABLE_STACKTRACE) return super.fillInStackTrace();
		return this;
	}
	
	@Override
	public String toString() {
		if (object==null && targetClass==null)
			return "Failed to cast class from null to null?";
		if (object==null)
			return "Failed to cast from "+object.getClass().getName()+" to null";
		if (targetClass==null)
			return "Failed to cast from null to "+targetClass.getName();
		return "Failed to cast from "+object.getClass().getName()+" to "+targetClass.getName();
	}
}