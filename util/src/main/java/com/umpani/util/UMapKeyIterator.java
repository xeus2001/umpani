package com.umpani.util;

import java.util.NoSuchElementException;

/**
 * An key iterator to iterate above all keys of a {@link UMap}.
 */
public final class UMapKeyIterator<K,V> extends UMapAbstractIterator<K,V,K> {
	/**
	 * Create a new key iterator for the provided Json map.
	 * @param map
	 * the Json map that should be iterated.
	 * @throws NullPointerException
	 * if the map parameter is null.
	 */
	public UMapKeyIterator( final UMap<K,V> map ) {
		super(map);
	}

	@SuppressWarnings("unchecked")
	@Override
	public K next() {
		final UMap<K,V> map = this.map;
		final UMap.Data data = map.data;
		if (data==null) throw new NoSuchElementException();
		final Object[] keyValue = data.keyValue;
		final int next = data.findNextKey(current+2);
		if (next < 0) throw new NoSuchElementException();
		return (K)map.unboxKey(keyValue[current=next]);
	}
}