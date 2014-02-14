package com.umpani.util;

import java.util.List;
import java.util.Map;

import com.umpani.util.exception.UClassCastException;

/**
 * The base class for duck typed objects. All instances based upon this base class are not thread safe, except they 
 * have been made read-only. Duck typed objects are used for easy and quick type casting in services that have a high 
 * demand for flexibility. Even while the performance might be lower compared to bare classes, the disadvantage is 
 * often quickly compensated by the additional comfort these types offer. They might even improve performance if a 
 * large amount of libraries are used together, because duck typing may avoid data converting.
 *
 * </p><p>All classes that extend this base class work the same way. They store their data in an data class that holds
 * a reference to an array that is either used as list ({@link UList}) or as hash map ({@link UMap}). This means that 
 * the data of one type can be transferred into another one as easy as copying the reference of this data structure. 
 * So by coping 8 byte of memory the content of one type can be transferred into another type or two instances can 
 * refer the same data, which is what the <tt>map</tt> method is doing.
 *
 * </p><p>If objects have been marked as read-only, reading their values is seen as being thread safe.
 *
 * @author Alexander Weber <xeus2001@gmail.com>
 */
public class UDuckTyped implements UType {
	/**
	 * The method used to box an value.
	 * @param value
	 * the value to box.
	 * @return
	 * the boxed value.
	 */
	protected Object boxBoolean( final boolean value ) {
		return value ? Boolean.TRUE : Boolean.FALSE;
	}

	/**
	 * The method used to box an value. By default invokes box(long).
	 * @param value
	 * the value to box.
	 * @return
	 * the boxed value.
	 */
	protected final Object boxByte( final byte value ) {
		return boxLong(value);
	}
	
	/**
	 * The method used to box an value. By default invokes box(long).
	 * @param value
	 * the value to box.
	 * @return
	 * the boxed value.
	 */
	protected final Object boxShort( final short value ) {
		return boxLong(value);
	}
	
	/**
	 * The method used to box an value. By default invokes box(long).
	 * @param value
	 * the value to box.
	 * @return
	 * the boxed value.
	 */
	protected final Object boxChar( final char value ) {
		return boxLong(value);
	}
	
	/**
	 * The method used to box an value. By default invokes box(long).
	 * @param value
	 * the value to box.
	 * @return
	 * the boxed value.
	 */
	protected final Object boxInt( final int value ) {
		return boxLong(value);
	}
	
	/**
	 * The method used to box an value. By default invokes box(long).
	 * @param value
	 * the value to box.
	 * @return
	 * the boxed value.
	 */
	protected Object boxLong( final long value ) {
		return Long.valueOf((long)value);
	}

	/**
	 * The method used to box an value. By default invokes box(double).
	 * @param value
	 * the value to box.
	 * @return
	 * the boxed value.
	 */
	protected final Object boxFloat( final float value ) {
		return boxDouble(value);
	}

	/**
	 * The method used to box an value.
	 * @param value
	 * the value to box.
	 * @return
	 * the boxed value.
	 */
	protected Object boxDouble( final double value ) {
		return new Double(value);
	}

	/**
	 * Any value stored in an map or list is boxed using this method. Be aware that this method might be called 
	 * multiple times at the same object.
	 * @param value
	 * the value to box.
	 * @return
	 * the boxed value.
	 */
	protected Object boxValue( final Object value ) {
		return value;
	}

	/**
	 * Any key stored in an map is boxed using this method.
	 * @param value
	 * the value to box.
	 * @return
	 * the boxed value.
	 */
	protected Object boxKey( final Object key ) {
		return key;
	}
	
	/**
	 * Returns the primitive for the given boxed value.
	 * @param value
	 * the boxed value.
	 * @return
	 * the unboxed value.
	 */
	protected boolean unboxBoolean( final Object value ) {
		if (value instanceof Boolean) {
			return ((Boolean)value).booleanValue();
		}
		return false;
	}

	/**
	 * Returns the primitive for the given boxed value. By default invokes the unboxDouble() and then casts.
	 * @param value
	 * the boxed value.
	 * @return
	 * the unboxed value.
	 */
	protected final byte unboxByte( final Object value ) {
		return (byte)unboxLong(value);
	}

	/**
	 * Returns the primitive for the given boxed value. By default invokes the unboxDouble() and then casts.
	 * @param value
	 * the boxed value.
	 * @return
	 * the unboxed value.
	 */
	protected final short unboxShort( final Object value ) {
		return (short)unboxLong(value);
	}

	/**
	 * Returns the primitive for the given boxed value. By default invokes the unboxDouble() and then casts.
	 * @param value
	 * the boxed value.
	 * @return
	 * the unboxed value.
	 */
	protected final char unboxChar( final Object value ) {
		return (char)unboxLong(value);
	}

	/**
	 * Returns the primitive for the given boxed value. By default invokes the unboxDouble() and then casts.
	 * @param value
	 * the boxed value.
	 * @return
	 * the unboxed value.
	 */
	protected final int unboxInt( final Object value ) {
		return (int)unboxLong(value);
	}

	/**
	 * Returns the primitive for the given boxed value.
	 * @param value
	 * the boxed value.
	 * @return
	 * the unboxed value.
	 */
	protected long unboxLong( final Object value ) {
		if (value==null) return 0L;
		if (value instanceof Number) return ((Number)value).longValue();
		return 0L;
	}

	/**
	 * Returns the primitive for the given boxed value. By default invokes the unboxDouble() and then casts.
	 * @param value
	 * the boxed value.
	 * @return
	 * the unboxed value.
	 */
	protected final float unboxFloat( final Object value ) {
		return (float)unboxDouble(value);
	}
	
	/**
	 * Returns the primitive for the given boxed value.
	 * @param value
	 * the boxed value.
	 * @return
	 * the unboxed value.
	 */
	protected double unboxDouble( final Object value ) {
		if (value==null) return 0d;
		if (value instanceof Number) return ((Number)value).doubleValue();
		return Double.NaN;
	}

	/**
	 * Returns the primitive for the given boxed value.
	 * @param value
	 * the boxed value.
	 * @return
	 * the unboxed value.
	 */
	protected String unboxString( final Object value ) {
		if (value==null) return null;
		if (value instanceof String) return (String)value;
		if (value instanceof CharSequence) return value.toString();
		return null;
	}

	/**
	 * Whenever a value is returned it is previously unboxed using this method. Be aware that this method might be 
	 * called multiple times at the same object.
	 * @param value
	 * the boxed value.
	 * @return
	 * the unboxed value.
	 */
	protected Object unboxValue( final Object value ) {
		return value;
	}

	/**
	 * Any key stored in an map is unboxed using this method. Note that there are only a few methods that will cause
	 * a key to be unboxed, for example <tt>getKeys</tt>.
	 * @param value
	 * the value to box.
	 * @return
	 * the boxed value.
	 */
	protected Object unboxKey( final Object key ) {
		return key;
	}
	
	/**
	 * Casts the value to the provided type and then returns the casted value. If the value is null, no casting is done 
	 * and null is returned. If the value is already of the desired class, no casting is done and the value is 
	 * returned. If casting the value failed the method throws an {@link ClassCastException}.
	 * 
	 * @param value
	 * the value to be casted.
	 * @param valueClass
	 * the class the value must have.
	 * @return
	 * the value as instance of the given class or null.
	 * @throws UClassCastException
	 * if casting the value failed.
	 * @throws NullPointerException
	 * if the provided value class is null.
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	protected <T> T cast( final Object value, final Class<T> valueClass) throws UClassCastException {
		if (valueClass==null) throw new NullPointerException("valueClass");
		if (value==null) return null;
		if (valueClass.isInstance(value)) return (T)value;

		// we need to cast value
		try {
			// value is WMap and valueClass is WMap
			if ((value instanceof UMap) && valueClass.isAssignableFrom(UMap.class) ) {
				return (T) ((UMap)valueClass.newInstance()).map((UMap)value);
			}
			
			// value is WList and valueClass is WList
//			if ((value instanceof UList) && valueClass.isAssignableFrom(UList.class)) {
//				return (T) ((UList)valueClass.newInstance()).map((UList)value);
//			}

			// value is JsonNumber and valueClass is WString
//			if ((value instanceof Number) && valueClass.isAssignableFrom(String.class)) {
//				return (T) UString.of(UNumber.of((Number)value).toString(),UString.NO_HASH).string;
//			}

			// value is JsonString and valueClass is WNumber
//			if ((value instanceof String) && valueClass.isAssignableFrom(Number.class)) {
//				final String v = ((String) value).getString();
//				if (v.indexOf('.') >= 0 || v.indexOf('e') >= 0 || v.indexOf('E') >= 0) {
//					return (T) UNumber.of(Double.parseDouble(v),UString.NO_HASH).number;
//				} else {
//					return (T) UNumber.of(Long.parseLong(v),UString.NO_HASH).number;
//				}
//			}

			// if we should cast the value to an Json value
			if (valueClass.isAssignableFrom(UType.class)) {
				// value is a boolean
				if (value instanceof Boolean) {
					return (T)(value instanceof Boolean ? Boolean.TRUE : Boolean.FALSE);
				}

				// value is a number
//				if (value instanceof Number) {
//					if (value instanceof Double || value instanceof Float) {
//						return (T)UNumber.of( ((Number)value).doubleValue() ).number;
//					}
//					return (T)UNumber.of( ((Number)value).longValue() ).number;
//				}
				
				// value is a string or char-sequence
//				if (value instanceof CharSequence) {
//					final CharSequence chars = (CharSequence)value;
//					return (T)UString.of(chars,0,chars.length(),UString.NO_HASH).string;
//				}

				if (value instanceof Map) {
				}

				if (value instanceof List) {
				}
			}
		} catch (Exception e) {
			// any error that happens is treated as a simple UClassCastException
		}
		// the value is not castable
		throw new UClassCastException(value, valueClass);
	}
}