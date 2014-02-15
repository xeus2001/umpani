package com.umpani.util.playground;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@SuppressWarnings("restriction")
public class PrimeGen {
//	public static final sun.misc.Unsafe unsafe;
//	static {
//		java.lang.reflect.Field field;
//		try {
//			field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
//			field.setAccessible(true);
//			unsafe = (sun.misc.Unsafe) field.get(null);
//		} catch (Exception e) {
//			throw new AssertionError("Access to sun.misc.Unsafe is requires, please use Sun JDK or Open JDK 7+!",e);
//		}
//	}
//	public static final long LONG_ARRAY_OFFSET = unsafe.arrayBaseOffset(long[].class);
//	public static final long LONG_ARRAY_SCALE = unsafe.arrayIndexScale(long[].class);

	/**
	 * Estimate the inverse square root in a very fast way (used in vectors a lot).
	 * @param number
	 * the number to calculate the inverse square root from.
	 * @return
	 * the estimated inverse square root.
	 * @see http://en.wikipedia.org/wiki/Fast_inverse_square_root
	 */
	public static final double isqrt( final double number ) {
		final long MAGIC = 0x5fe6eb50c7b537a9L;
		final double THREE_HALFS = 1.5d;
		final double x2 = number * 0.5d;

		double y = number;
		long i = Double.doubleToRawLongBits(y);
		i = MAGIC - (i >> 1);
		y = Double.longBitsToDouble(i);
		y = y * (THREE_HALFS - (x2 * y * y));
		// Wikipedia is wrong, the 2nd iteration MUST NOT be removed!
		y = y * (THREE_HALFS - (x2 * y * y));
		return y;
	}

	/**
	 * Calculte primes single threaded using the current thread.
	 * @param maxTime
	 * the maximal time to be consumed.
	 * @param maxTimeUnit
	 * the time unit in which the maxTime was provided.
	 * @param maxAmount
	 * the maximal amount of primes to calculate.
	 * @return
	 * the found primes.
	 */
	protected static final long[] getPrimesSingleThreaded(final long maxTime, final TimeUnit maxTimeUnit,final int maxAmount) {
		final long STOP_TIME = System.currentTimeMillis() + maxTimeUnit.toMillis(maxTime);
		long[] primes = new long[65536];
		primes[0] = 2L;
		primes[1] = 3L;
		int nextPrimeIndex = 2;
		long test = 5;
		search: while (nextPrimeIndex < maxAmount) {
			// only check time every 256 numbers
			if ((test & 0xFF)==1 && System.currentTimeMillis() >= STOP_TIME) {
				break search;
			}

			// maximal and minimal divisor to test for this prime
			final long MAX = (long)Math.ceil(isqrt(test)*(double)test);
//			final long MAX = (long)Math.ceil(Math.sqrt(test));

			// try to divide the number by all already found primes (quick exclusion)
			for (int i=0; i <= primes.length; i++) {
				final long prime = primes[i];
				if (prime==0 || prime > MAX) break;

				// if it is dividable, it is not prime
				if ((test % prime)==0) {
					test+=2; // we don't test even numbers
					continue search;
				}
			}

			// if we reached the end of the primes array, expand it
			if (nextPrimeIndex >= primes.length) {
				primes = Arrays.copyOf(primes,primes.length<<1);
			}

			// add the new prime number
			primes[nextPrimeIndex++] = test;
			
			// check next number
			test += 2;
		}
		return Arrays.copyOf(primes,nextPrimeIndex);
	}

	/**
	 * Use to defined the amount numbers each PrimeNumberCalculator thread will process at its own.
	 */
	private static final long BLOCK_SIZE = Integer.highestOneBit(65536 -1)<<1;

	/**
	 * The number of cores to use in multi-threaded calculations.
	 */
	private static final int CORES = Runtime.getRuntime().availableProcessors();

	/**
	 * One of the threads will hols the first 16,384 prime numbers, because for the first 16,384 primes it doesn't 
	 * make sense to use multi threading. Additionally our algorithm relies on the fact all prime numbers that are
	 * less/equal to sqrt(test) are always found, which can't be guaranteed at all, but we can reduce the risk of
	 * running out of sync and missing a prime number doing it this way, because with the first 16,384 primes we've
	 * a situation where such big numbers are already pre-calculated that we don't need to care about slow threads,
	 * just if a real long stall happens (for multiple seconds) we might run into the problem that one chunk that
	 * might contain some prime numbers is not calculated.
	 */
	protected static final class PrimeNumberCalculator extends Thread {
		protected PrimeNumberCalculator(final AtomicLong theLong, final long[] basePrimes, final long endMillis) {
			this.basePrimes = basePrimes;
			this.nextBlock = theLong;
			this.STOP_TIME = endMillis;
		}

		private final long STOP_TIME;
		private final AtomicLong nextBlock;
		private final long[] basePrimes;
		public volatile long[] primes = new long[16384];
		public volatile int putIndex;

		@Override
		public void run() {
			// copy to stack to be faster
			final long[] basePrimes = this.basePrimes;
			final long MIN = basePrimes[65535];
			
			// the index where to put the next found prime number
			int putIndex = this.putIndex;
			long[] primes = this.primes;

			// the block loop
			while (System.currentTimeMillis() < STOP_TIME) {
				// grab a couple of numbers we have to check
				final long START = nextBlock.getAndAdd(BLOCK_SIZE);
				final long END = START + BLOCK_SIZE;
				long test = START+1; // start with odd number!
				if (test <= MIN) test = MIN+2;
	
				// the test loop for the numbers in the current block
				search: while (test < END) {
					// maximal and minimal divisor to test for this prime
					final long MAX = (long)Math.ceil(isqrt(test)*(double)test);
//					final long MAX = (long)Math.ceil(Math.sqrt(test));
	
					// try to divide the number by all base primes (quick exclusion)
					for (int i=0; i < basePrimes.length; i++) {
						// copy to stack for faster access
						final long prime = basePrimes[i];

						// if the prime is too large, abort
						if (prime > MAX) break;

						// test against this prime
						if ((test % prime)==0) {
							// not prime as devidable
							test += 2;
							continue search;
						}
					}

					// if the value is not dividable by any previous prime, add it into our local primes
					// (ensure there is space)
					if (putIndex >= primes.length) {
						primes = Arrays.copyOf(primes,primes.length<<1);
					}
					primes[putIndex++] = test;
					test += 2;
				}
			}
			// export primes and index
			this.primes = primes;
			this.putIndex = putIndex;
		}
	}
	
	/**
	 * Returns the first primes unordered.
	 * @param maxTime
	 * the maximal time to be consumed.
	 * @param maxTimeUnit
	 * the time unit in which the maxTime was provided.
	 * @param maxAmount
	 * the maximal amount of primes to calculate.
	 * @return
	 * the found primes.
	 */
	public static final long[] getPrimesMultiThreaded( final long maxTime, final TimeUnit maxTimeUnit, final int maxAmount ) {
		if (maxAmount < 65537) return getPrimesSingleThreaded(maxTime,maxTimeUnit,maxAmount);
		final long END = System.currentTimeMillis() + maxTimeUnit.toMillis(maxTime);
		final long[] basePrimes = getPrimesSingleThreaded(10,TimeUnit.SECONDS,65536);

		final AtomicLong blockCounter = new AtomicLong(basePrimes[65535] & (~(BLOCK_SIZE-1)));
		final PrimeNumberCalculator[] threads = new PrimeNumberCalculator[CORES];
		// create all threads
		for (int i=0; i < threads.length; i++) {
			threads[i] = new PrimeNumberCalculator(blockCounter,basePrimes,END);
			threads[i].start();
		}
		// wait for all threads to be done
		for (int i=0; i < threads.length; i++) {
			try {
				Thread.interrupted();
				threads[i].join();
			} catch (InterruptedException e) {}
		}

		// copy together the results
		int foundPrimes = 65536;
		for (int i=0; i < threads.length; i++) foundPrimes += threads[i].putIndex;
		final long[] allPrimes = Arrays.copyOf(basePrimes,foundPrimes);
		for (int i=0,n=65536; i < threads.length; i++) {
			System.arraycopy(threads[i].primes,0, allPrimes,n, threads[i].putIndex);
			n += threads[i].putIndex;
		}
		
		// sort them, fix the first prime number which is current 2 and then return
		Arrays.sort(allPrimes);
		return allPrimes;
	}


	// -------------------------------------------- test code ----------------------------------------------------------
	
	
	public static final void realGC() {
		final WeakReference<Object> gcMe = new WeakReference<Object>( new Object() );
		while (gcMe.get()!=null) {
			System.gc();
			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
				Thread.interrupted();
			}
		}
	}
	public static final void showResults( final int cores, final long[] array, final boolean show,long nanos ) {
		final long ms = TimeUnit.NANOSECONDS.toMillis(nanos);
		final long s = TimeUnit.MILLISECONDS.toSeconds(ms);
		System.out.println("Calculated "+array.length+" primes at "+cores+" cores in "+ms+"ms");
		long sum = 0;
		if (show) {
			for (int i=1; i <= array.length; i*=10) {
				System.out.println("prime #"+i+" = "+array[i-1]);
			}
		}
		for (int i=0; i < array.length; i++) {
			final long prime = array[i];
//			if (show && prime==15485863) System.out.println("15485863 is prime #"+i);
			sum += prime;
		}
		final long biggestPrimeFound = array[array.length-1];
		System.out.println("The biggest found prime was prime #"+array.length+" and is "+biggestPrimeFound+", sum = "+sum);
		System.out.println("Checked "+((biggestPrimeFound/ms)/cores)+" numbers per millisecond per core");
		System.out.println("Checked "+((biggestPrimeFound/s)/cores)+" numbers per second per core");
	}
	public static final void findPrimesMultiThreaded(final long maxTime, final TimeUnit maxTimeUnit, final boolean show) {
		realGC();
		long[] array;
		final long start = System.nanoTime();
			array = getPrimesMultiThreaded(maxTime,maxTimeUnit,Integer.MAX_VALUE);
		final long end = System.nanoTime();
		showResults(CORES,array,show,end-start);
	}

	public static void findPrimesSingleThreadedPrimes(final long maxTime, final TimeUnit maxTimeUnit, final boolean show) {
		realGC();
		long[] array;
		final long start = System.nanoTime();
			array = getPrimesSingleThreaded(maxTime,maxTimeUnit,Integer.MAX_VALUE);
		final long end = System.nanoTime();
		showResults(1,array,show,end-start);
	}

	public static void main(String... args) {
		System.out.println("Warming up... ");
		findPrimesMultiThreaded(2000,TimeUnit.MILLISECONDS,false); // warms up multi and single threaded
		System.out.println("done\n\n");

		System.out.println("Running performance tests...");
		findPrimesSingleThreadedPrimes(15,TimeUnit.SECONDS,true); // single threaded
		findPrimesMultiThreaded(15,TimeUnit.SECONDS,true); // multi threaded
	}
}
