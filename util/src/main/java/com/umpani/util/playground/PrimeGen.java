package com.umpani.util.playground;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@SuppressWarnings("restriction")
public class PrimeGen {
	/**
	 * 	Estimate the inverse square root in a very fast way (used in vectors a lot).
	 *
	 *	@param number
	 *		the number to calculate the inverse square root from.
	 *	@return
	 *		the estimated inverse square root.
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
	public static final long[] getPrimesSingleThreaded(final long maxTime, final TimeUnit maxTimeUnit,final int maxAmount) {
		final long STOP_TIME = System.currentTimeMillis() + maxTimeUnit.toMillis(maxTime);
		long[] primes = new long[65536];
		primes[0] = 2L; // fix later
		primes[1] = 2L;
		primes[2] = 3L;
		int nextPrimeIndex = 3;
		long test = 5;
		search: while (nextPrimeIndex < maxAmount) {
			// only check time every 256 numbers
			if ((test & 0xFF)==1 && System.currentTimeMillis() >= STOP_TIME) {
				break search;
			}

			// maximal and minimal divisor to test for this prime
			final long max = (long)Math.ceil(isqrt(test)*(double)test);

			// try to divide the number by all already found primes (quick exclusion)
			for (int i=0; i <= max; i++) {
				final long prime = primes[i];
				if (prime==0) break;

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
		primes[0]=1L;
		return Arrays.copyOf(primes,nextPrimeIndex);
	}

	/**
	 * One of the threads will hols the first 16,384 prime numbers, because for the first 16,384 primes it doesn't 
	 * make sense to use multi threading. Additionally our algorithm relies on the fact all prime numbers that are
	 * less/equal to sqrt(test) are always found, which can't be guaranteed at all, but we can reduce the risk of
	 * running out of sync and missing a prime number doing it this way, because with the first 16,384 primes we've
	 * a situation where such big numbers are already pre-calculated that we don't need to care about slow threads,
	 * just if a real long stall happens (for multiple seconds) we might run into the problem that one chunk that
	 * might contain some prime numbers is not calculated.
	 */
	public static final class PrimeNumberCalculator extends Thread {
		private static final int BLOCK_SHIFT = 10;
		private static final long BLOCK_SIZE = 1 << BLOCK_SHIFT;

		public PrimeNumberCalculator(
			final AtomicLong theLong, final PrimeNumberCalculator[] theThreads,
			final long[] initialValues, final long endMillis
		) {
			allThreads = theThreads;
			nextBlock = theLong;
			STOP_TIME = endMillis;
			
			if (initialValues!=null) {
				primes = Arrays.copyOf(initialValues,Integer.highestOneBit(initialValues.length-1)<<2);
				putIndex = initialValues.length;
			} else {
				primes = new long[16384];
				putIndex = 0;
			}
		}

		private final long STOP_TIME;
		private final AtomicLong nextBlock;
		private final PrimeNumberCalculator[] allThreads;
		public volatile long[] primes;
		public volatile int putIndex;

		@Override
		public void run() {
			// copy to stack to be faster
			final PrimeNumberCalculator[] allThreads = this.allThreads;
			
			// the index where to put the next found prime number
			int putIndex = this.putIndex;
			long[] myPrimes = this.primes;

			// the block loop
			while (System.currentTimeMillis() < STOP_TIME) {
				// grab a couple of numbers we have to check
				final long start = nextBlock.getAndAdd(BLOCK_SIZE);
				final long end = start + BLOCK_SIZE;
				long test = start+1; // start with odd number!
	
				// the test loop for the numbers in the current block
				search: while (test < end) {
					// maximal and minimal divisor to test for this prime
					final long MAX = (long)Math.ceil(isqrt(test)*(double)test);
	
					// try to divide the number by all already found primes (quick exclusion)
					// question: will this work? the good this is that 
					for (int t=0; t < allThreads.length; t++) {
						// copy reference to the prime numbers of the thread to the stack for faster access
						final long[] primes = allThreads[t].primes;
						for (int i=0; i< primes.length; i++) {
							// copy to stack for faster access
							final long prime = primes[i];
	
							// a workaround to skip already found primes, which only happens in the begin
							if (test==prime) continue search;
							
							// if no further prime in the array or the prime is larger as the maximal prime
							if (prime==0 || prime > MAX) break;
							
							// test against this prime
							if ((test % prime)==0) {
								// not prime as devidable
								test += 2;
								continue search;
							}
						}
					}
					
					// if the value is not dividable by any previous prime, add it into our local primes
					// (ensure there is space)
					if (putIndex >= myPrimes.length) {
						this.primes = myPrimes = Arrays.copyOf(myPrimes,myPrimes.length<<1);
					}
					myPrimes[putIndex++] = test;
					test += 2;
				}
			}
			this.putIndex = putIndex;
		}
	}
	
	/**
	 * Returns the first primes unordered.
	 * @param maxTime
	 * the maximal time to be consumed.
	 * @param maxTimeUnit
	 * the time unit in which the maxTime was provided.
	 * @return
	 * the found primes.
	 */
	public static final long[] getPrimesMultiThreaded( final long maxTime, final TimeUnit maxTimeUnit ) {
		final long[] firstPrimes = getPrimesSingleThreaded(10,TimeUnit.SECONDS,16384);
		firstPrimes[0]=2L; // we need to so that, because a division by 1 is always possible
//		final int maxThreads = Runtime.getRuntime().availableProcessors();
		final int maxThreads = 1;
		final AtomicLong blockCounter = new AtomicLong(firstPrimes[16383]&0xFFFFFFFFFFFFC000L);
		final PrimeNumberCalculator[] threads = new PrimeNumberCalculator[maxThreads];
		final long END = System.currentTimeMillis() + maxTimeUnit.toMillis(maxTime);
		for (int i=0; i < threads.length; i++) {
			threads[i] = new PrimeNumberCalculator(blockCounter,threads,i==0?firstPrimes:null,END);
			threads[i].start();
		}
		for (int i=0; i < threads.length; i++) {
			try {
				Thread.interrupted();
				threads[i].join();
			} catch (InterruptedException e) {}
		}

		int foundPrimes = 0;
		for (int i=0; i < threads.length; i++) foundPrimes += threads[i].putIndex;
		long[] allPrimes = new long[foundPrimes];
		for (int i=0,n=0; i < threads.length; i++) {
			System.arraycopy(threads[i].primes,0, allPrimes,n, threads[i].putIndex);
			n += threads[i].putIndex;
		}
		Arrays.sort(allPrimes);
		allPrimes[0]=1L; // fix what we have broken before
		return allPrimes;
	}
	
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
	public static final void showResults( final String which, final long[] array, final boolean show,long nanos ) {
		System.out.println("Calculated "+array.length+" primes "+which+" threaded in "+TimeUnit.NANOSECONDS.toMillis(nanos)+"ms");
		long sum = 0;
//		for (int i=0; i < 10; i++) System.out.println("prime #"+i+" = "+array[i]);
		for (int i=0; i < array.length; i++) sum += array[i];
		System.out.println("The biggest found prime was: "+array[array.length-1]+", sum = "+sum);
	}
	public static final void findPrimesMultiThreaded(final long maxTime, final TimeUnit maxTimeUnit, final boolean show) {
		realGC();
		long[] array;
		final long start = System.nanoTime();
			array = getPrimesMultiThreaded(maxTime,maxTimeUnit);
		final long end = System.nanoTime();
		showResults("multi",array,show,end-start);
	}

	public static void findPrimesSingleThreadedPrimes(final long maxTime, final TimeUnit maxTimeUnit, final boolean show) {
		realGC();
		long[] array;
		final long start = System.nanoTime();
			array = getPrimesSingleThreaded(maxTime,maxTimeUnit,Integer.MAX_VALUE);
		final long end = System.nanoTime();
		showResults("single",array,show,end-start);
	}

	public static void main(String... args) {
		System.out.println("Warming up... ");
		findPrimesSingleThreadedPrimes(100,TimeUnit.MILLISECONDS,false); // warmup single threaded
		findPrimesSingleThreadedPrimes(100,TimeUnit.MILLISECONDS,false); // warmup single threaded
		findPrimesMultiThreaded(100,TimeUnit.MILLISECONDS,false); // warmup multi threaded
		findPrimesMultiThreaded(100,TimeUnit.MILLISECONDS,false); // warmup multi threaded
		System.out.println("done\n\n");

		System.out.println("Running performance tests...");
		findPrimesSingleThreadedPrimes(1,TimeUnit.SECONDS,true); // single threaded
		findPrimesMultiThreaded(1,TimeUnit.SECONDS,true); // multi threaded
	}
}
