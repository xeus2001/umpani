package com.umpani.util.visitors;

import com.umpani.util.UMap;
import com.umpani.util.exception.UVisitorException;

/**
 * An interface that can be implemented to inspect all key-value pairs of an map.
 */
public interface UMapVisitor<K,V,R> {
	/**
	 * This method is called for every key-value pair being contained in an map.
	 * @param map
	 * the map that is visited.
	 * @param key
	 * the key that is visited.
	 * @param value
	 * the value that is visited.
	 * @param result
	 * the result from the previous visit.
	 * @param isLastVisit
	 * true if this is the last time the visitor is called.
	 * @return
	 * the result of the visit or the input for the next visit call.
	 * @throw UVisitorException
	 * to signal something.
	 */
	public <T extends UMap<K,V>> R visit( final T map, final K key, final V value, final R result, final boolean isLastVisit ) throws UVisitorException;
}