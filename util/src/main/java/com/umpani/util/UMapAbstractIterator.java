package com.umpani.util;

import java.util.Iterator;
import java.util.NoSuchElementException;

import com.umpani.util.exception.UReadOnlyException;

/**
 * The base implementation of an key or value iterator.
 */
public abstract class UMapAbstractIterator<K,V,T> implements Iterator<T> {
	/**
	 * Create a new key-value iterator.
	 * @param map
	 * the UMap to be iterated.
	 * @throws NullPointerException
	 * if the provided map parameter is null.
	 */
	protected UMapAbstractIterator( final UMap<K,V> map ) {
		if (map==null) throw new NullPointerException();
		this.map = map;
	}

	/**
	 * Reference to the Json map to be iterated.
	 */
	protected final UMap<K,V> map;
	
	/**
	 * The current index.
	 */
	protected int current = -2;

	@Override
	public boolean hasNext() {
		return map.data.findNextKey(current+2) >= 0;
	}

	@Override
	public void remove() {
		if (current < 0) throw new NoSuchElementException();
		final UMap<K,V> map = this.map;
		final UMap.Data data = map.data;
		if (data==null) throw new NoSuchElementException();
		final Object[] keyValue = data.keyValue;
		if (current >= keyValue.length) throw new NoSuchElementException();
		if (map.isReadOnly()) throw new UReadOnlyException(this,"remove",map);
		if (keyValue[current]!=null) {
			data.size--;
			keyValue[current] = null;
			keyValue[current+1] = null;
		} else {
			throw new NoSuchElementException();
		}
	}
}
