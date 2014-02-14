package com.umpani.util;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import com.umpani.util.exception.UClassCastException;
import com.umpani.util.exception.UReadOnlyException;
import com.umpani.util.exception.UVisitorException;
import com.umpani.util.exception.UVisitorModifiedException;
import com.umpani.util.exception.UVisitorRemoveException;
import com.umpani.util.exception.UVisitorReplaceException;
import com.umpani.util.exception.UVisitorReturnException;
import com.umpani.util.visitors.UMapVisitor;

/**
 * A hash map implementation that is optimized for small volatile not concurrent maps as used for example in JSON
 * processing.
 * 
 * </p><p>This implementation is optimized for modern CPUs and keeps all keys and values in a row to optimize access 
 * to them. The hash of a key is used only as start index from where on the key-value pair is searched. A key is seen 
 * as not found after its bucket has been completely searched. The bucket are simply <i>n</i> elements, starting at
 * the calculated index. Therefore the buckets of this hash-map have collitions with each other, which optimizes space 
 * usage. If there are too many collisions, so all available slots for a certain bucket are used, then the hash map is 
 * doubled in its size. In an optimal case, the hash map can be filled to 100% without any negative performance impact. 
 * Hash maps with a size of four, what is the smallest size possible, are always filled up completely. The general 
 * rule is that the hash maps are more compact as smaller they are. If the keys are optimal, then the hash map will not 
 * wast a single byte of memory and it may be filled up to 100% without any performance impact. Due to the exponential 
 * growth of the hash map there is no need to start with an optimal size. This is the reason why this hash map has no 
 * parameters, nothing like load factor or any other setting. The hash map is automatically optimized.
 *
 * </p><p><b>WARNING</b>: This hash map is not thread safe and one instance of this hash map <b>must</b> only be used 
 * by one thread at a time. It is recommended to keep long living objects as {@link UOffHeapMap} in memory and only to 
 * de-serialize them temporary to modify them. In that case modification is easy, because only one thread has the 
 * de-serialized instance of the object. This is called transactional memory pattern or optimistic modification. You
 * create a copy of the object, then you modify it and finally you write the modification back, hoping that the 
 * write back will be successfull and that the object was not modified in the mean time by another thread. If it was, 
 * the whole modification is simply repeated, therefore every modification runs in such a loop:
 * <pre>
 *	while(true) {
 *		// start a transaction
 *		try (UTransaction t = db.beginTransaction()) {
 *			// read foo from db, add it into the transaction
 *			// t.add will clone foo and return a writeable version
 *			final Foo foo = t.add( db.get(key) );
 *
 *			// modify foo
 *			foo.setName("test");
 *			foo.setValue(100);
 *
 *			// commit the changes and repeat if commit failed
 *			if (t.commit()) break;
 *		}
 *	}
 * </pre>
 * Doing it this way in an concurrent environment gurantees that at least one thread will be able to successfully 
 * perform a modification, even with multiple objects being modified. Additionally <tt>db</tt> should gurantee that 
 * threads are treated fair in a volatile environment, so it should prevent that any thread is very unlucky and 
 * therefore executing an expensive transaction in an endless loop, for example because it is always slower as other 
 * threads.
 *
 * @param <K>
 * the key type.
 * @param <V>
 * the value type.
 */
public class UMap<K,V> extends UDuckTyped implements Iterable<Map.Entry<K,V>>, Map<K,V> {
	/**
	 * A problem is that with a growing number of key-value pairs being stored in an hash map we can't avoid to 
	 * encounter more and more hash collisions, basically because hashes are not guranteed to be optimal. Therefore 
	 * we need to increase the amount of key-value pairs in an bucket to increase the amount of allowed collisions, 
	 * as the hash map grows.
	 * @param length
	 * the length of the keyValue array.
	 * @return
	 * the size of the buckets; in other words, the amount of collisions to allow before doubling the hash map size.
	 */
	protected static final int _bucketSize( final int length ) {
		// as long as there are less then 1024 key-value pairs we stick to maximal 4 collisions, from there on we
		// add 1 more accepted collision for every further 1024 key-value pairs, therefore we have 
		// 4 + (length/(1024*2)), because we store two objects per key-value pair
		return 4 + (length >>> 10);
	}

	/**
	 * The option bit to signal that the view or data is read-only.
	 */
	protected static final int OPT_READONLY = 1 << 0;

	/**
	 * The internal data that the UMap has a view on. The methods of this internal data structure will not check if
	 * the data is sealed!
	 * @author Alexander Weber <xeus2001@gmail.com>
	 */
	protected static class Data {
		/**
		 * Creates and empty data class, be aware that this is a not valid data instance and therefore only useful if
		 * for example another data object should be cloned.
		 * @param sure
		 * must be set to true; otherwise throws a {@link IllegalArgumentException}
		 * @throws IllegalArgumentException
		 * if the provided sure parameter is not true
		 */
		protected Data( final boolean sure ) {
			if (!sure) throw new IllegalArgumentException("sure must be true");
		}
		
		/**
		 * Create a new data record of the specified initial.
		 * @param size
		 * the desired size of the data.
		 */
		public Data( int size ) {
			size = Integer.highestOneBit(size-1)<<1;
			if (size < 4) size = 4;

			this.keyValue = new Object[size];
			this.size = 0;
			// the lowest bit must be 0, because we mask to keys and they can only be found at even indices
			this.mask = (size - 1) & 0xFFFFFFFE;
		}
		
		/**
		 * An array that stores the key-value pairs of the map.
		 */
		protected Object[] keyValue;
		
		/**
		 * The amount of valid key-value pairs in the map.
		 */
		protected int size;
		
		/**
		 * The bit mask to be used to find the index of the next key-value pair.
		 */
		protected int mask;
		
		/**
		 * Options of this data.
		 */
		protected int options;
		
		/**
		 * Returns true if this data array is sealed.
		 */
		protected final boolean isSealed() {
			return (options & OPT_READONLY) == OPT_READONLY;
		}

		/**
		 * Returns the index of the provided key in the keyValue array or -1 if the key is not contained.
		 * @param key
		 * the key to search for.
		 * @return
		 * the index in the keyValue array or -1 if no such key exists.
		 */
		protected final int findExistingKey( final Object key ) {
			final Object[] keyValue = this.keyValue;
			int i = key.hashCode() & mask;
			for (int m=_bucketSize(keyValue.length); m > 0; m--) {
				final Object k = keyValue[i];
				if (k!=null && (k==key || k.equals(key))) return i;
				i = (i+2) & mask;
			}
			return -1;
		}
		
		/**
		 * Returns the index of the next value occurrence in the keyValue array. The search stars at the provided value 
		 * index. If no further occurrence is found -1 is returned.
		 * @param value
		 * the value to be searched for.
		 * @param start
		 * the index of the first value from where to start the search, must be an odd number (1,3,5...).
		 * @return
		 * the index within the keyValue array of the found value or -1 if no further occurrence is found. The 
		 * returned index identifies the value and is therefore always odd (1,3,5...).
		 */
		protected final int findExistingValue( final Object value, final int start ) {
			final Object[] keyValue = this.keyValue;
			if (start < 0 || start >= keyValue.length) return -1;
			for (int i=start-1; i < keyValue.length;i++) {
				final Object key = keyValue[i++];
				if (key!=null) {
					final Object v = keyValue[i];
					if (value==v || (value!=null && value.equals(v))) return i;
				}
			}
			return -1;
		}
		
		/**
		 * Returns the index of the next key-value pair in the keyValue array or -1 if no further valid key-value pair 
		 * is contained in the array.
		 * @param start
		 * the index where to start the search, must be an even value (0,2,4...).
		 * @return
		 * the index of the key of the next valid key-value pair or -1 if no further valid key-value pair is contained 
		 * in the keyValue array.
		 */
		protected final int findNextKey( final int start ) {
			final Object[] keyValue = this.keyValue;
			if (start < 0 || start >= keyValue.length) return -1;
			for (int i=start; i < keyValue.length;i+=2) {
				if (keyValue[i]!=null) return i;
			}
			return -1;
		}
	
		/**
		 * Tries to find either the used slot where the key-value pair of the given key is stored or, if this key is
		 * not yet stored in the keyValue array, returns the index of a new empty slot where the given key and value 
		 * may be placed. This operation will only fail (returning -1) if the given key is not contained in the 
		 * keyValue array and all possible slots are filled with other key-value pairs. This means the bucket for the
		 * hash of the key is full. In that case there are too many collisions and therefore the keyValue array must 
		 * be expanded to be able to insert the key-value pair with the given key.
		 * @param key
		 * the key for which to return the index.
		 * @return
		 * either the index where the given key should be placed (value is placed behind it) or -1 if there is no slot 
		 * available, which means the provided key is not contained in the keyValue array and there is no space left
		 * for it to be added either.
		 */
		protected final int indexForKey( final Object key ) {
			final Object[] keyValue = this.keyValue;
			int firstEmpty = -1;
			int i = key.hashCode() & mask;
			for (int m=_bucketSize(keyValue.length); m > 0; m--) {
				final Object k = keyValue[i];
				if (k==null && firstEmpty < 0) firstEmpty = i;
				if (k==key || k.equals(key)) return i;
				i = (i+2) & mask;
			}
			return firstEmpty;
		}
	
		/**
		 * Compacts the keyValue array. If the compaction fails, because the provided minimal size is to small, the
		 * algorithm will automatically adjust up.
		 * @param minSize
		 * the minimal size the new keyValue array shall have.
		 */
		protected final void compact( int minSize ) {
			// cache keyValue array at stack for faster access
			final Object[] oldKeyValue = this.keyValue;

			// ensure that minSize is a 2^n value
			minSize = Integer.highestOneBit(minSize-1)<<1;
			if (minSize < 4) minSize = 4;
			
			// otherwise re-index
			resize: while(true) {
				final Object[] keyValue = this.keyValue = new Object[minSize];
				for (int i=0; i < oldKeyValue.length;) {
					final Object key = oldKeyValue[i++];
					final Object value = oldKeyValue[i++];
					if (key!=null) {
						final int j = indexForKey(key);
						if (j < 0) {
							// shit, too many collisions, double the minimal size and re-start the resize operation
							minSize <<= 1;
							continue resize;
						}
						keyValue[j] = key;
						keyValue[j+1] = value;
					}
				}
				return;
			}
		}
	}
	
	/**
	 * The options of this view to the underlying data.
	 */
	protected int options;
	
	/**
	 * The data to which 
	 */
	protected Data data;

	/**
	 * Returns true if this map or the underlying data is a read-only.
	 * @return
	 * true if this map or the underlying data is read-only.
	 */
	public final boolean isReadOnly() {
		return ((options & OPT_READONLY)==OPT_READONLY) || (data!=null && data.isSealed());
	}

	/**
	 * Makes this map and optionally the underlying data read-only. Be aware that making a map read-only means not that 
	 * the underlying data can't be modified, it means that the map that is made read-only will not allow to modify the
	 * underlying data. However, if another map refers to the same underlying data and the data itself is not made
	 * read-only, the underlying data may be changed using the other view.
	 * @param data
	 * true if this map and the underlying data should become read-only. Settings this parameter to true forces all 
	 * maps that refer to the same data to become read-only, doing so will make the access to the data thread safe.
	 */
	public final UMap<K,V> setReadOnly( final boolean data ) {
		options |= OPT_READONLY;
		if (data && this.data!=null) this.data.options |= OPT_READONLY;
		return this;
	}

	/**
	 * A helper method that can be used to create a new map from an array of key-value pairs.
	 *
	 * @param keyType
	 * the type of the keys, if null the keys are copied unchecked.
	 * @param valueType
	 * the type of the values, if null the values are copied unchecked.
	 * @param objects
	 * the data to be used to create an map. The provided arguments must be alternation between key (even index) and 
	 * value (odd index) and therefore must have a length that is a multiple of 2. If a key is null, the following 
	 * value is ignored.
	 * @return
	 * the new map for the given key-value pairs.
	 * @throws ClassCastException
	 * if any expected key or value is not of the provided types.
	 * @throws IllegalArgumentException
	 * if the given objects are an odd amount (1,3,5..).
	 */
	@SuppressWarnings("unchecked")
	public static final <A,B> UMap<A,B> of( final Class<A> keyType, final Class<B> valueType, final Object... objects ) {
		if (objects==null || objects.length==0) return new UMap<A,B>();
		if ((objects.length&1)==1) throw new IllegalArgumentException("The 'objects' arguments must be a multiple of 2");

		final UMap<A,B> map = new UMap<A,B>(false);
		for (int i=0; i < objects.length;) {
			final Object key = objects[i++];
			final Object value = objects[i++];
			if (key!=null) {
				if (keyType!=null && !keyType.isInstance(key)) throw new ClassCastException("Key at index "+(i-2)+" is of an invalid type");
				if (value!=null && valueType!=null && !valueType.isInstance(value)) throw new ClassCastException("Value at index "+(i-1)+" is of an invalid type");
				map.put((A)key, (B)value);
			}
		}
		map.init();
		return map;
	}

	/**
	 * Create a new empty map. This map will not allocate any memory until the first key-value pair is added.
	 * @param callInit
	 * if false, then the init method is not called.
	 */
	protected UMap( boolean callInit ) {
		if (!callInit) init();
	}

	/**
	 * Create a new empty map. This map will not allocate any memory until the first key-value pair is added.
	 */
	public UMap() {
		init();
	}

	/**
	 * This method forces this map to copy the data reference from the given map into this map. If the given map is 
	 * read-only, then this map will become read-only as well.
	 * @param other
	 * the other map from which to copy the reference to the underlying data.
	 * @return
	 * this.
	 * @throws NullPointerException
	 * if the provided other map is null.
	 */
	@SuppressWarnings("unchecked")
	public <T extends UMap<K,V>> T map( final UMap<?,?> other ) throws NullPointerException {
		this.data = other.data;
		this.options = other.options;
		init();
		return (T)this;
	}
		
	/**
	 * This method forces this map to copy the data reference from the given map into this map and to make this map
	 * read-only.
	 *
	 * @param other
	 * the other map from which to copy the reference to the underlying data.
	 * @return
	 * this.
	 * @throws NullPointerException
	 * if the provided other map is null.
	 */
	@SuppressWarnings("unchecked")
	public <T extends UMap<K,V>> T mapReadOnly( final UMap<?,?> other ) {
		this.data = other.data;
		this.options |= OPT_READONLY;
		init();
		return (T)this;
	}

	/**
	 * This method forces this map to copy the data from the given map and to refer to the copied data. Be aware that
	 * the copy is not recursive, therefore modifying a stored value, if it is not immutable, will have an effect
	 * to the other map as well, but for example deleting a key-value pair from the map or replacing a value will not 
	 * be reflected in the other map.
	 *
	 * @param other
	 * the other map from which to copy the data.
	 * @return
	 * this.
	 */
	@SuppressWarnings("unchecked")
	public <T extends UMap<K,V>> T copy( final UMap<?,?> other ) {
		if (other!=null && other.data!=null) {
			final Data otherData = other.data; 
			final Object[] otherKeyValue = otherData.keyValue;
			final Data data = this.data = new Data(true);
			data.keyValue = Arrays.copyOf(otherKeyValue, otherKeyValue.length);
			data.mask = otherData.mask;
			data.size = otherData.size;
		} else {
			this.data = null;
		}
		this.options = 0;
		init();
		return (T)this;
	}
	
	/**
	 * A method that is called whenever the map is initialized. An initialization means that the data to which the map 
	 * refers is changed, so the map refers to other data. This happens for example if <tt>map</tt>, 
	 * <tt>mapReadOnly</tt> or after the static method <tt>of</tt> was called.
	 * 
	 * </p><p>The method is guaranteed to be invoked after the change is done and it may be overloaded to perform some 
	 * arbitrary initialization. The default implementation will do nothing.
	 */
	protected void init() {}

	/**
	 * Returns true if this map contains the given key.
	 *
	 * @param key
	 * the key to search for.
	 * @return
	 * true if this object contains this key; false otherwise.
	 * @throws NullPointerException
	 * if the given key is null.
	 */
	@Override
	public final boolean containsKey( final Object key ) {
		return (data!=null && data.findExistingKey(key) >= 0);
	}

	/**
	 * Returns an array with all keys of this map. If no key-value pairs are set, an empty array is returned.
	 * @return
	 * an array with all keys of this map.
	 */
	public final Object[] getKeys() {
		final Data data = this.data;
		final int size;
		if (data==null || (size=data.size)==0) return new Object[0];
		final Object[] keyValue = data.keyValue;
		final Object[] keys = new Object[size];
		for (int j=0,i=0; i < keyValue.length; i++) {
			final Object key = keyValue[i++];
			if (key!=null) keys[j++] = key;
		}
		return keys;
	}

	/**
	 * Returns an array containing all of the keys stored in this map; the runtime type of the returned array is that 
	 * of the specified array. If the keys fits in the specified array, they are returned therein. Otherwise, a new 
	 * array is allocated with the runtime type of the specified array and the size of the amount of stored key-value 
	 * pairs.
	 *
	 * </p><p>If this keys fits in the specified array with room to spare (i.e., the array has more elements than this 
	 * map), the element in the array immediately following the end of the keys is set to <tt>null</tt>. This is 
	 * useful in determining the length of this collection.
	 *
	 * </p><p>This map makes no guarantees as in what order its keys are returned. Example:
	 * <pre>
	 *	String[] y = map.getKeys(new String[0]);</pre>
	 *
	 * @param a
	 * the array into which the keys of this map are to be stored, if it is big enough; otherwise, a new array of the 
	 * same runtime type is allocated for this purpose.
	 * @return
	 * an array containing all of the elements in this map.
	 * @throws ArrayStoreException
	 * if the runtime type of the specified array is not a supertype of the runtime type of the key of this map.
	 * @throws NullPointerException
	 * if the specified array is null.
	 */
	@SuppressWarnings("unchecked")
	public final <T> T[] getKeys( T[] a ) {
		// see: AbstractCollection for implementation details
		final Data data = this.data;
		final int size;
		if (data==null || (size=data.size)==0) {
			Arrays.fill(a, null);
			return a;
		}
		if (a.length < size) {
			a = (T[]) Array.newInstance(a.getClass().getComponentType(), size);
		}

		// copy keys
		final Object[] keyValue = data.keyValue;
		int j=0;
		for (int i=0; i < keyValue.length; i+=2) {
			final Object key = keyValue[i];
			if (key!=null) {
				a[j++] = (T)key;
			}
		}
		while (j < a.length) a[j++] = null;
		return a;
	}

	/**
	 * Returns an array with all values of this map. If no key-value pairs are set, an empty array is returned.
	 * @return
	 * an array with all values of this Json map.
	 */
	public final Object[] getValues() {
		final Data data = this.data;
		final int size;
		if (data==null || (size=data.size)==0) return new Object[0];
		final Object[] keyValue = data.keyValue;
		final Object[] values = new Object[size];
		for (int j=0,i=0; i < keyValue.length;) {
			final Object key = keyValue[i++];
			final Object value = keyValue[i++];
			if (key!=null) values[j++] = value;
		}
		return values;
	}
	
	/**
	 * Returns an array containing all of the values stored in this map; the runtime type of the returned array is 
	 * that of the specified array. If the values fits in the specified array, they are returned therein. Otherwise, 
	 * a new array is allocated with the runtime type of the specified array and the size of the amount of stored 
	 * key-value pairs.
	 *
	 * </p><p>If the values fit in the specified array with room to spare (i.e., the array has more elements than 
	 * this map), the elements in the array immediately following the end of the values are set to <tt>null</tt>. This 
	 * is only useful in determining the length of this collection, if it known that there are no null values stored.
	 *
	 * </p><p>This map makes no guarantees as in what order its values are returned. Example:
	 * <pre>
	 *	String[] y = map.getValues(new String[0]);</pre>
	 *
	 * @param a
	 * the array into which the values of this map are to be copied, if it is big enough; otherwise, a new array of 
	 * the same runtime type is allocated for this purpose.
	 * @return
	 * an array containing all of the values stored in this map.
	 * @throws ArrayStoreException
	 * if the runtime type of the specified array is not a supertype of the runtime type of every value in this map.
	 * @throws NullPointerException
	 * if the specified array is null.
	 */
	@SuppressWarnings("unchecked")
	public final <T> T[] getValues( T[] a ) {
		// see: AbstractCollection for implementation details
		final Data data = this.data;
		final int size;
		if (data==null || (size=data.size)==0) {
			Arrays.fill(a, null);
			return a;
		}
		if (a.length < size) {
			a = (T[]) Array.newInstance(a.getClass().getComponentType(), size);
		}

		// copy keys
		final Object[] keyValue = data.keyValue;
		int j=0;
		for (int i=0; i < keyValue.length;) {
			final Object key = keyValue[i++];
			final Object value = keyValue[i++];
			if (key!=null) {
				a[j++] = (T)value;
			}
		}
		while (j < a.length) a[j++] = null;
		return a;
	}

	/**
	 * Returns an array with all key-value pairs stored in this Json object. This array will contain the keys and 
	 * values stored in this Json map in an alternating order.
	 * @return
	 * an array with all key-value pairs of this Json map, alternating key,value,key,value,...
	 */
	public final Object[] getKeyValuePairs() {
		final Data data = this.data;
		final int size;
		if (data==null || (size=data.size)==0) return new Object[0];
		final Object[] keyValue = data.keyValue;
		final Object[] keyValueCopy = new Object[size<<1];
		for (int j=0,i=0; i < keyValue.length;) {
			final Object key = keyValue[i++];
			final Object value = keyValue[i++];
			if (key!=null) {
				keyValueCopy[j++] = key;
				keyValueCopy[j++] = value;
			}
		}
		return keyValueCopy;
	}

	/**
	 * Returns an key iterator.
	 * @return
	 * the key iterator.
	 */
	public final Iterator<K> iterateKeys() {
//		return new UMapKeyIterator<K,V>(this);
		return null;
	}
	
	/**
	 * Returns an value iterator.
	 * @return
	 * the value iterator.
	 */
	public final Iterator<V> iterateValues() {
//		return new UMapValueIterator<K,V>(this);
		return null;
	}

	@Override
	public final boolean isEmpty() {
		return data==null || data.size==0;
	}

	@Override
	public final boolean containsValue(Object value) {
		return (data!=null && data.findExistingValue(value,1)>=0);
	}

	/**
	 * Iterates all key-value pairs of this map and returns some result. This method guarantees that the iteration
	 * is done thread local. If the map is modified by the visitor, then the visitor must throw an 
	 * {@link WVisitorModifiedException}. Be aware that removing a key-value pair can be done more efficient using 
	 * the {@link WVisitorRemoveException} and replacing the value can be done using the 
	 * {@link WVisitorReplaceValueException}. Only if you for example want to swap values or you want to modify an
	 * key (like lowercasing it), then you must modify the map and afterwards throw an {@link WVisitorModifiedException}.
	 * 
	 * </p><p>The method does not gurantee that the same key is not visited twice, if the underlying map is modified.
	 *
	 * @param visitor
	 * the visitor to be called for every key-value pair of this map.
	 * @param initialValue
	 * the initial value for the result to be passed into the first visitor call.
	 * @return
	 * the result of the visit.
	 * @throws IllegalStateException
	 * if an unknown {@link WVisitorException} is throw.
	 */
	@SuppressWarnings("unchecked")
	public final <R> R forEach( final UMapVisitor<K,V,R> visitor ) {
		return forEach(visitor, null);
	}
		
	/**
	 * Iterates all key-value pairs of this map and returns some result. This method guarantees that the iteration
	 * is done thread local. If the map is modified by the visitor, then the visitor must throw an 
	 * {@link UVisitorModifiedException}. Be aware that removing a key-value pair can be done more efficient using 
	 * the {@link UVisitorRemoveException} and replacing the value can be done using the 
	 * {@link UVisitorReplaceException}. Only if you want, for example, to swap values or you want to modify an
	 * key (like lowercasing it), then you must modify the map and afterwards throw an {@link UVisitorModifiedException}.
	 * 
	 * </p><p>The method does not gurantee that the same key is not visited twice, if the underlying map is modified.
	 *
	 * @param visitor
	 * the visitor to be called for every key-value pair of this map.
	 * @param initialValue
	 * the initial value for the result to be passed into the first visitor call.
	 * @return
	 * the result of the visit.
	 * @throws IllegalStateException
	 * if an unknown {@link UVisitorException} is throw.
	 */
	@SuppressWarnings("unchecked")
	public final <R> R forEach( final UMapVisitor<K,V,R> visitor, R initialValue ) {
		final Data data = this.data;
		int size;
		if (data==null || (size=data.size)==0) return initialValue;

		iterator: while(true) {
			final Object[] keyValue = data.keyValue;
			R result = initialValue;
			for (int j=0,i=0; i < keyValue.length; ) {
				final K key = (K)keyValue[i++];
				final V value = (V)unboxValue(keyValue[i++]);
				if (key!=null) {
					try {
						result = visitor.visit(this,(K)unboxKey(key),value,result,++j==size);
					} catch (UVisitorRemoveException re) {
						if (isReadOnly()) throw new UReadOnlyException(visitor,"forEach",this);
						keyValue[i-2] = null;
						keyValue[i-1] = null;
						data.size = --size;
					} catch (UVisitorReplaceException e) {
						if (isReadOnly()) throw new UReadOnlyException(visitor,"forEach",this);
						keyValue[i-1] = boxValue(e.replacement);
					} catch (UVisitorModifiedException e) {
						// this is sick, if the items have beein re-index
						if (keyValue != data.keyValue) {
							// in that case we've no other possibility, we need to restart the visit
							continue iterator;
						}
					} catch (UVisitorReturnException e) {
						 return (R)e.result;
					} catch (UVisitorException e) {
						e.unknow();
					}
				}
			}
			return result;
		}
	}
	
	/**
	 * Casts the value of the provided key to the provided class, writes the casted value back into the map and 
	 * then returns the casted value. If the value is null, no casting is done and null is returned. If the value is
	 * already of the desired class, no casting is done and the value is returned. If casting the value failed the 
	 * method throws an exception and will not modify the stored value.
	 * @param key
	 * the key for which the value should be returned.
	 * @param valueClass
	 * the class the value must have.
	 * @return
	 * the value as instance of the given class or null.
	 * @throws UClassCastException
	 * if casting the value failed.
	 * @throws UReadOnlyException
	 * if this object is read-only and the casted value failed while trying to write it back.
	 * @throws NullPointerException
	 * if the provided valueClass parameter is null.
	 */
	@SuppressWarnings("unchecked")
	public final <T extends V> T castAndGetOrThrow( final K key, final Class<T> valueClass)
		throws UClassCastException, UReadOnlyException, NullPointerException
	{
		final V value = get(key);
		if (value==null) return null;
		if (valueClass.isInstance(value)) return (T)value;
		if (isReadOnly()) throw new UReadOnlyException(this,"fixAndGetOrThrow",this);

		try {
			put(key, cast(value,valueClass));
		} catch( Exception e ) {
			throw new UClassCastException(value,valueClass);
		}
		return (T)get(key);
	}

	/**
	 * Casts the value of the provided key to the provided class, writes the casted value back into the map and 
	 * then returns the casted value. If the value is null, no casting is done and null is returned. If the value is
	 * already of the desired class, no casting is done and the value is returned. If casting the value failed the 
	 * value is converted to null, written back and this value is returned.
	 * @param key
	 * the key for which the value should be returned.
	 * @param valueClass
	 * the class the value must have.
	 * @return
	 * the value as instance of the given class or null, if casting the value failed.
	 * @throws UReadOnlyException
	 * if this map is read-only.
	 * @throws NullPointerException
	 * if the provided valueClass parameter is null.
	 */
	@SuppressWarnings("unchecked")
	public final <T extends V> T castAndGet( final K key, final Class<T> valueClass )
		throws UReadOnlyException, NullPointerException
	{
		final V value = get(key);
		if (value==null) return null;
		if (valueClass.isInstance(value)) return (T)value;
		if (isReadOnly()) throw new UReadOnlyException(this,"fixAndGetOrThrow",this);

		try {
			put(key, cast(value,valueClass));
			return (T)get(key);
		} catch (Exception e) {
			// if casting failed we ignore that and return null
			put(key, null);
			return null;
		}
	}

	/**
	 * Reads the value of the key and returns it. If no such key exists or the value of the key is null then a new 
	 * instance of the requested value class is created, written back into the map and returned. If the currently 
	 * stored value is not castable to the desired class, it is removed and the same applies as if the value would 
	 * have been null. This method may be used to get a guaranteed return value of the desired typ.
	 * @param key
	 * the key for which to return the value.
	 * @param valueClass
	 * the desired type of the return value.
	 * @return
	 * the value.
	 * @throws UReadOnlyException
	 * if this object is sealed.
	 * @throws UClassCastException
	 * if casting the value and creating a new instance of the provided class failed.
	 * @throws NullPointerException
	 * if the provided valueClass parameter is null.
	 */
	@SuppressWarnings("unchecked")
	public final <T extends V> T castAndGetOrCreate(final K key, final Class<T> valueClass)
		 throws UReadOnlyException, UClassCastException, NullPointerException
	{
		final V value = get(key);
		if (valueClass.isInstance(value)) return (T)value;

		// now we need to modify the map, ensure that this is allowed
		if (isReadOnly()) throw new UReadOnlyException(this,"createOrGet",this);
		
		// cast the value and write 
		try {
			put(key, cast(value, valueClass));
		} catch( Exception e) {
			// we are requested to not throw an exception
			try {
				put(key, valueClass.newInstance());
			} catch( Exception ee ) {
				throw new UClassCastException(value,valueClass);
			}
		}
		return (T)get(key);
	}

	/**
	 * Casts the value at the provided key to the provided type and then returns the casted value. If the value is 
	 * null, no casting is done and null is returned. If the value is already of the desired class, no casting is done 
	 * and the value is returned. If casting the value failed the method returns null.
	 * @param key
	 * the key of the value to return.
	 * @param valueClass
	 * the class the value must have.
	 * @return
	 * the value as instance of the given class or null.
	 */
	@SuppressWarnings({ "unchecked", "rawtypes" })
	public final <T extends V> T getAndCast( final K key, final Class<T> valueClass) {
		try {
			return cast(get(key),valueClass);
		} catch (Exception e) {
			return null;
		}
	}

	@Override
	@SuppressWarnings("unchecked")
	public V get(final Object key) {
		final Data data = this.data;
		if (data==null || data.size==0) return null;
		final Object[] keyValue = data.keyValue;
		final int index = data.findExistingKey(key);
		return index < 0 ? null : (V)unboxValue(keyValue[index+1]);
	}

	/**
	 * Returns the value of the given key if it is a string; null otherwise.
	 * @param key
	 * the key.
	 * @return
	 * the value.
	 */
	public final String getString( final K key ) {
		return unboxString(get(key));
	}
	
	/**
	 * Returns the value of the given key if it is a string; defaultValue otherwise.
	 * @param key
	 * the key.
	 * @param defaultValue
	 * if the key is no string, this value is returned.
	 * @return
	 * the value.
	 */
	public String getString( final K key, final String defaultValue ) {
		final Object value = get(key);
		return !(value instanceof CharSequence) ? defaultValue : unboxString(value);
	}
	
	/**
	 * Returns the value of the given key if it is a number; null otherwise.
	 *
	 * @param key
	 * the key.
	 * @return
	 * the value.
	 */
	public final double getDouble( final K key ) {
		return unboxDouble(get(key));
	}

	/**
	 * Returns the value of the given key if it is a double; defaultValue otherwise.
	 * @param key
	 * the key.
	 * @param defaultValue
	 * if the key is no double, this value is returned.
	 * @return
	 * the value.
	 */
	public final double getDouble( final K key, final double defaultValue ) {
		final Object value = get(key);
		return !(value instanceof Number) ? defaultValue : unboxDouble(value);
	}
	
	/**
	 * Returns the value of the given key if it is a number; null otherwise.
	 *
	 * @param key
	 * the key.
	 * @return
	 * the value.
	 */
	public final float getFloat( final K key ) {
		return unboxFloat(get(key));
	}

	/**
	 * Returns the value of the given key if it is a float; defaultValue otherwise.
	 * @param key
	 * the key.
	 * @param defaultValue
	 * if the key is no float, this value is returned.
	 * @return
	 * the value.
	 */
	public float getFloat( final K key, final float defaultValue ) {
		final Object value = get(key);
		return !(value instanceof Number) ? defaultValue : unboxFloat(value);
	}
	
	/**
	 * Returns the value of the given key if it is a number; null otherwise.
	 *
	 * @param key
	 * the key.
	 * @return
	 * the value.
	 */
	public final long getLong( final K key ) {
		return unboxLong(get(key));
	}
	
	/**
	 * Returns the value of the given key if it is a long; defaultValue otherwise.
	 * @param key
	 * the key.
	 * @param defaultValue
	 * if the key is no long, this value is returned.
	 * @return
	 * the value.
	 */
	public long getLong( final K key, final long defaultValue ) {
		final Object value = get(key);
		return !(value instanceof Number) ? defaultValue : unboxLong(value);
	}
	
	/**
	 * Returns the value of the given key if it is a number; null otherwise.
	 *
	 * @param key
	 * the key.
	 * @return
	 * the value.
	 */
	public final int getInt( final K key ) {
		return unboxInt(get(key));
	}
	
	/**
	 * Returns the value of the given key if it is a int; defaultValue otherwise.
	 * @param key
	 * the key.
	 * @param defaultValue
	 * if the key is no int, this value is returned.
	 * @return
	 * the value.
	 */
	public int getInt( final K key, final int defaultValue ) {
		final Object value = get(key);
		return !(value instanceof Number) ? defaultValue : unboxInt(value);
	}
	
	/**
	 * Returns the value of the given key if it is a number; null otherwise.
	 *
	 * @param key
	 * the key.
	 * @return
	 * the value.
	 */
	public final char getChar( final K key ) {
		return unboxChar(get(key));
	}
	
	/**
	 * Returns the value of the given key if it is a character; defaultValue otherwise.
	 * @param key
	 * the key.
	 * @param defaultValue
	 * if the key is no character, this value is returned.
	 * @return
	 * the value.
	 */
	public char getChar( final K key, final char defaultValue ) {
		final Object value = get(key);
		return !(value instanceof Number) ? defaultValue : unboxChar(value);
	}
	
	/**
	 * Returns the value of the given key if it is a number; null otherwise.
	 *
	 * @param key
	 * the key.
	 * @return
	 * the value.
	 */
	public final short getShort( final K key ) {
		return unboxShort(get(key));
	}
	
	/**
	 * Returns the value of the given key if it is a short; defaultValue otherwise.
	 * @param key
	 * the key.
	 * @param defaultValue
	 * if the key is no short, this value is returned.
	 * @return
	 * the value.
	 */
	public short getShort( final K key, final short defaultValue ) {
		final Object value = get(key);
		return !(value instanceof Number) ? defaultValue : unboxShort(value);
	}
	
	/**
	 * Returns the value of the given key if it is a number; null otherwise.
	 *
	 * @param key
	 * the key.
	 * @return
	 * the value.
	 */
	public final byte getByte( final Object key ) {
		return unboxByte(get(key));
	}
	
	/**
	 * Returns the value of the given key if it is a byte; defaultValue otherwise.
	 * @param key
	 * the key.
	 * @param defaultValue
	 * if the key is no byte, this value is returned.
	 * @return
	 * the value.
	 */
	public byte getByte( final K key, final byte defaultValue ) {
		final Object value = get(key);
		return !(value instanceof Number) ? defaultValue : unboxByte(value);
	}
	
	/**
	 * Returns the value of the given key if it is a boolean; null otherwise.
	 *
	 * @param key
	 * the key.
	 * @return
	 * the value.
	 */
	public final boolean getBoolean( final Object key ) {
		return unboxBoolean(get(key));
	}

	/**
	 * Returns the value of the given key if it is a boolean; defaultValue otherwise.
	 * @param key
	 * the key.
	 * @param defaultValue
	 * if the key is no boolean, this value is returned.
	 * @return
	 * the value.
	 */
	public boolean getBoolean( final K key, final boolean defaultValue ) {
		final Object value = get(key);
		return !(value instanceof Boolean) ? defaultValue : unboxBoolean(value);
	}
	
	/**
	 * Returns the value of the given key if it is an instance of {@link UMap}; null otherwise.
	 * @param key
	 * the key.
	 * @return
	 * the value.
	 */
	@SuppressWarnings("unchecked")
	public final <T extends Map<?,?>> T getMap( final Object key ) {
		final Object value = get(key);
		return (value instanceof Map) ? (T)value : null;
	}

	/**
	 * Returns the value of the given key if it is an instance of {@link UMap}; defaultValue otherwise.
	 * @param key
	 * the key.
	 * @param defaultValue
	 * if the value is no instanceof of an {@link UMap}, then this value is returned.
	 * @return
	 * the value.
	 */
	@SuppressWarnings("unchecked")
	public final <T extends Map<?,?>> T getMap( final Object key, final T defaultValue ) {
		final Object value = get(key);
		return (value instanceof Map) ? (T)value : defaultValue;
	}

	/**
	 * Returns the value of the given key if it is an instance of Json map; null otherwise.
	 * @param key
	 * the key.
	 * @return
	 * the value.
	 */
	@SuppressWarnings("unchecked")
	public final <T extends UList<?>> T getList( final Object key ) {
		final V value = get(key);
		return (value instanceof UList) ? (T)value : null;
	}

	/**
	 * Returns the value of the given key if it is an instance of {@link UList}; defaultValue otherwise.
	 * @param key
	 * the key.
	 * @param defaultValue
	 * if the value is no instanceof of an {@link UList}, then this value is returned.
	 * @return
	 * the value.
	 */
	@SuppressWarnings("unchecked")
	public final <T extends UList<?>> T getList( final Object key, final T defaultValue ) {
		final Object value = get(key);
		return (value instanceof Map) ? (T)value : defaultValue;
	}

	@SuppressWarnings("unchecked")
	@Override
	public final V remove( final Object key) { // we must use object here, because of the stupid List interface
		return delete((K)key);
	}

	/**
	 * Removes the provided key from the map.
	 * @param key
	 * the key to be removed.
	 * @return
	 * the previous value that was assigned to this key.
	 * @throws UReadOnlyException
	 * if this map is read-only.
	 */
	@SuppressWarnings("unchecked")
	public V delete( final K key ) {
		if (key==null) throw new NullPointerException();
		if (isReadOnly()) throw new UReadOnlyException(this,"delete",this);
		final Data data = this.data;
		if (data==null || data.size==0) return null;

		final Object[] keyValue = data.keyValue;
		int index = data.findExistingKey(key);
		if (index < 0) return null;
		
		keyValue[index++] = null;
		final Object value = keyValue[index];
		keyValue[index] = null;
		--data.size;
		return (V)unboxValue(value);
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public final void putAll( final Map<? extends K, ? extends V> m ) {
		if (isReadOnly()) throw new UReadOnlyException(this,"putAll",this);
		if (m!=null) {
			Map<K,V> mm = (Map<K,V>) m;
			for( Map.Entry<K,V> item : mm.entrySet() ) {
				put(item.getKey(), item.getValue());
			}
		}
	}

	@Override
	public final void clear() {
		if (isReadOnly()) throw new UReadOnlyException(this,"clear",this);
		if (data!=null && data.size!=0) {
			data.size = 0;
			Arrays.fill(data.keyValue,null);
		}
	}

	@Override
	public final Set<K> keySet() {
//		return new UMapKeySet<K,V>(this);
		return null;
	}

	@Override
	public final Collection<V> values() {
//		return new UMapValueCollection<K,V>(this);
		return null;
	}

	@Override
	public final Set<Map.Entry<K, V>> entrySet() {
//		return new UMapEntrySet<K,V>(this);
		return null;
	}
	
	@SuppressWarnings("unchecked")
	@Override
	public V put( final K key, final V newValue) {
		if (isReadOnly()) throw new UReadOnlyException(this,"put",this);

		final Data data;
		if (this.data==null) {
			data = this.data = new Data(4);
		} else {
			data = this.data;
		}
		int index = data.indexForKey(key);
		while (index < 0) {
			data.compact(data.size<<1);
			index = data.indexForKey(key);
		}
		final Object[] keyValue = data.keyValue;
		if (keyValue[index]!=null) {
			++data.size;
		} else {
			keyValue[index] = key;
		}
		final Object oldValue = unboxValue(keyValue[++index]);
		keyValue[index] = boxValue(newValue);
		return (V)oldValue;
	}

	/**
	 * Put the given key to the given value, the value is wrapped into a Long.
	 * @param key
	 * the key to set.
	 * @param value
	 * the value to set.
	 * @return
	 * the old value.
	 */
	@SuppressWarnings("unchecked")
	public final V put( final K key, final boolean value) {
		return put(key, (V)boxBoolean(value));
	}
	
	/**
	 * Put the given key to the given value, the value is wrapped into a Long.
	 * @param key
	 * the key to set.
	 * @param value
	 * the value to set.
	 * @return
	 * the old value.
	 */
	@SuppressWarnings("unchecked")
	public final V put( final K key, final byte value) {
		return put(key, (V)boxByte(value));
	}
	
	/**
	 * Put the given key to the given value, the value is wrapped into a Long.
	 * @param key
	 * the key to set.
	 * @param value
	 * the value to set.
	 * @return
	 * the old value.
	 */
	@SuppressWarnings("unchecked")
	public final V put( final K key, final short value) {
		return put(key, (V)boxShort(value));
	}
	
	/**
	 * Put the given key to the given value, the value is wrapped into a Long.
	 * @param key
	 * the key to set.
	 * @param value
	 * the value to set.
	 * @return
	 * the old value.
	 */
	@SuppressWarnings("unchecked")
	public final V put( final K key, final char value) {
		return put(key, (V)boxChar(value));
	}
	
	/**
	 * Put the given key to the given value, the value is wrapped into a Long.
	 * @param key
	 * the key to set.
	 * @param value
	 * the value to set.
	 * @return
	 * the old value.
	 */
	@SuppressWarnings("unchecked")
	public final V put( final K key, final int value) {
		return put(key, (V)boxInt(value));
	}
	
	/**
	 * Put the given key to the given value, the value is wrapped into a Long.
	 * @param key
	 * the key to set.
	 * @param value
	 * the value to set.
	 * @return
	 * the old value.
	 */
	@SuppressWarnings("unchecked")
	public final V put( final K key, final long value) {
		return put(key, (V)boxLong(value));
	}
	
	/**
	 * Put the given key to the given value, the value is wrapped into a Double.
	 * @param key
	 * the key to set.
	 * @param value
	 * the value to set.
	 * @return
	 * the old value.
	 */
	@SuppressWarnings("unchecked")
	public final V put( final K key, final double value) {
		return put(key, (V)boxDouble(value));
	}
	
	/**
	 * Put the given key to the given value, the value is wrapped into a Double.
	 * @param key
	 * the key to set.
	 * @param value
	 * the value to set.
	 * @return
	 * the old value.
	 */
	@SuppressWarnings("unchecked")
	public final V put( final K key, final float value) {
		return put(key, (V)boxFloat(value));
	}

	/**
	 * Put the given key to the given value, if the value is null, the key is being removed.
	 * @param key
	 * the key to be set.
	 * @param newValue
	 * the value to which the key should be set or null if the key should be removed.
	 * @return
	 * the old value.
	 */
	public final V putOrRemove( final K key, final V newValue ) {
		if (newValue==null) {
			return delete(key);
		}
		return put(key, newValue);
	}

	@Override
	public boolean equals( final Object other ) {
		if (this==other) return true;
		if (!(other instanceof Map)) return false;

		final Map<?,?> otherMap = (Map<?,?>) other;
		final Data thisData = this.data;
		
		// if this map is empty
		if (thisData==null || thisData.size==0) {
			// the other map must be empty either
			return otherMap.size()==0;
		} else
		// if this map is not empty, the the other map must not be empty either, to be more exact, it must contain
		// the same amount of keys that this map has
		if (thisData.size != otherMap.size()) {
			return false;
		}
		
		// we know that we've the same amount of valid keys in both maps, however, they need to have the same 
		// key-value pairs
		final Object[] thisKeyValue = thisData.keyValue;
		for (int i=0; i < thisKeyValue.length;) {
			final Object key = thisKeyValue[i++];
			final Object value = thisKeyValue[i++];

			// if this key is valid
			if (key!=null) {
				// it must as well be contained in the other map
				if (!otherMap.containsKey(key)) return false;
				
				// and it must be the same value
				final Object otherValue = otherMap.get(key);
				if (value==null) {
					if (otherValue!=null) return false;
				} else
				if (!value.equals(otherValue)) {
					return false;
				}
			}
		}
		return true;
	}

	@Override
	public final Iterator<Map.Entry<K, V>> iterator() {
//		return new UMapEntryIterator<K,V>(this);
		return null;
	}

	@Override
	public int size() {
		return data==null ? 0 : data.size;
	}
}