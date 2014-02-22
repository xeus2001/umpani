package com.umpani.util;

import java.util.NoSuchElementException;

/**
 * An value iterator to iterate above all values of an Json map.
 */
public final class UMapValueIterator<K,V> extends UMapAbstractIterator<K,V,V> {
	/**
	 * Create a new value iterator for the provided Json map.
	 * @param map
	 * the Json map that should be iterated.
	 * @throws NullPointerException
	 * if the given map parameter is null.
	 */
	public UMapValueIterator( final UMap<K,V> map ) {
		super(map);
	}

	@SuppressWarnings("unchecked")
	@Override
	public V next() {
		final UMap<K,V> map = this.map;
		final UMap.Data data = map.data;
		if (data==null) throw new NoSuchElementException();
		final Object[] keyValue = data.keyValue;
		final int next = data.findNextKey(current+2);
		if (next < 0) throw new NoSuchElementException();
		return (V)map.unboxValue(keyValue[(current=next)+1]);
	}
}