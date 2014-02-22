import static org.junit.Assert.*;

import java.util.Iterator;

import org.junit.Test;

import com.umpani.util.UMap;
import com.umpani.util.exception.UVisitorException;
import com.umpani.util.exception.UVisitorFailedException;
import com.umpani.util.visitors.UMapVisitor;

public class TMap {
	
	@Test
	public void newInstance() {
		UMap<String,String> map = new UMap<String,String>();
		assertNotNull(map);
		assertEquals(0, map.size());
	}

	@Test
	public void newInstanceOf() {
		final UMap<String,Number> map = UMap.of(String.class,Number.class,"hello",1, "world",2);
		assertNotNull(map);
		assertEquals(2, map.size());
		assertEquals(true, map.containsKey("hello"));
		assertEquals(true, map.containsKey("world"));
		assertEquals(1L, map.getLong("hello"));
		assertEquals(2L, map.getLong("world"));
		assertEquals(1d, map.getDouble("hello"),0d);
		assertEquals(2d, map.getDouble("world"),0d);
	}

	@Test
	public void testKeyAddingReplacingAndRemoving() {
		// this test should force a "compaction"
		final UMap<String,Number> map = new UMap<>();
		assertEquals(0, map.size());
		map.put("a", 1);
		assertEquals(1, map.size());
		map.put("b", 2);
		assertEquals(2, map.size());
		map.put("c", 1.5);
		assertEquals(3, map.size());
		map.put("d", 3);
		assertEquals(4, map.size());
		map.put("e", 4);
		assertEquals(5, map.size());
		assertEquals(1, map.getInt("a"));
		assertEquals(2, map.getInt("b"));
		assertEquals(1.5d, map.getDouble("c"), 0d);
		assertEquals(1, map.getInt("c"));
		assertEquals(3, map.getInt("d"));
		assertEquals(4, map.getInt("e"));


		map.delete("c");
		assertEquals(4, map.size());
		map.put("a", 5);
		assertEquals(4, map.size());
		assertEquals(5, map.getInt("a"));
	}
	
	@Test
	public void testForEach() {
		final UMap<String,Number> map = UMap.of(String.class,Number.class,"a",1, "b",2);
		assertEquals(2, map.size());
		final long result = map.forEach(0L, new UMapVisitor<String,Number,Long>() {
			@Override
			public <T extends UMap<String, Number>> Long visit(
				T map, String key, Number value, Long result, boolean isLastVisit
			) throws UVisitorException {
				if ("a".equals(key)) {
					assertEquals(1L,value.longValue());
					return result += value.longValue();
				} else
				if ("b".equals(key)) {
					assertEquals(2L,value.longValue());
					return result += value.longValue();
				} else {
					throw new UVisitorFailedException("Invalid key found: "+key);
				}
			}
		});
		assertEquals(3L, result);
	}
	
	@Test
	public void testIterateKeys() {
		final UMap<String,Number> map = UMap.of(String.class,Number.class,"a",1, "b",2);
		assertEquals(2, map.size());
		final Iterator<String> it = map.iterateKeys();
		
		String key;
		Number value;
		
		assertTrue(it.hasNext());
		key = it.next();
		assertEquals("a", key);
		value = map.get(key);
		assertEquals(1L, value.longValue());
		
		assertTrue(it.hasNext());
		key = it.next();
		assertEquals("b", key);
		value = map.get(key);
		assertEquals(2L, value.longValue());
		
		assertFalse(it.hasNext());
	}

	@Test
	public void testIterateValues() {
		final UMap<String,Number> map = UMap.of(String.class,Number.class,"a",1, "b",2);
		assertEquals(2, map.size());
		final Iterator<Number> it = map.iterateValues();
		
		Number value;
		
		assertTrue(it.hasNext());
		value = it.next();
		assertEquals(1L, value.longValue());
		
		assertTrue(it.hasNext());
		value = it.next();
		assertEquals(2L, value.longValue());
		
		assertFalse(it.hasNext());
	}
}
