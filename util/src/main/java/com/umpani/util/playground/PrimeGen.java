package com.umpani.util.playground;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@SuppressWarnings("restriction")
public class PrimeGen {
	public static final sun.misc.Unsafe unsafe;
	static {
		java.lang.reflect.Field field;
		try {
			// Note: in other VMs the name of this property may be different, but as this code anyways will only work 
			// with JDK this is a perfect way to throw an exception if the field is not found
			field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
			field.setAccessible(true);
			unsafe = (sun.misc.Unsafe) field.get(null);
		} catch (Exception e) {
			throw new AssertionError("Access to sun.misc.Unsafe is requires, please use Sun JDK or Open JDK 7+!",e);
		}
	}
	public static final long LONG_ARRAY_OFFSET = unsafe.arrayBaseOffset(long[].class);
	public static final long LONG_ARRAY_SCALE = unsafe.arrayIndexScale(long[].class);
	public static final int BLOCK_SHIFT = 12;
	public static final long BLOCK_SIZE = 1 << BLOCK_SHIFT;

	/**
	 * Returns the first primes unordered.
	 * @param primes
	 * the array to fill with primes, starting with 1. The array must be filled with 0.
	 * @param maxTime
	 * the maximal time to be consumed.
	 * @param maxTimeUnit
	 * the time unit in which the maxTime was provided.
	 * @return
	 * the found primes.
	 */
	public static final long[] getPrimes( final long[] thePrimes, final long maxTime, final TimeUnit maxTimeUnit ) {
		// we know the first three prime numbers
		thePrimes[0] = 2L; // we fix that later
		thePrimes[1] = 2L;
		thePrimes[2] = 3L;

		// search for primes using multiple threads
		final long STOP_TIME = System.currentTimeMillis() + maxTimeUnit.toMillis(maxTime);
		final int threadCount = Runtime.getRuntime().availableProcessors();
		final Thread[] threads = new Thread[threadCount];
		final AtomicLong nextBlock = new AtomicLong(0);
		final boolean[] processed = new boolean[1024*1024];
		for (int i=0; i < threads.length; i++) {
			threads[i] = new Thread() {
				@Override
				public void run() {
					long lastKnownValidatedPrime = 3;
					int lastKnownValidatedPrimeIndex = 2;
					primefind: while (true) {
						if (System.currentTimeMillis() >= STOP_TIME) break;
						final long start = nextBlock.getAndAdd(BLOCK_SIZE);
						final long end = start + BLOCK_SIZE;
						long prime = start < 5 ? 5 : start+1; // start with odd number!
						search: while (prime < end) {
							// maximal and minimal divisor to test for this prime
							final long max = (long)Math.ceil(Math.sqrt(prime));

							// try to divide the number by all already found primes (quick exclusion)
							int thePrimesIndex = 0;
							for (; thePrimesIndex < thePrimes.length; thePrimesIndex++) {
								long nextFoundPrime = thePrimes[thePrimesIndex];
								if (nextFoundPrime==0) break;

								// if it is dividable, it is not prime
								if ((prime % nextFoundPrime)==0) {
									prime+=2; // we don't test even numbers
									continue search;
								}
							}

							// if the number is not dividable by any previously found prime it might be prime
							
							// test all unvalidated primes
							for (long i=lastKnownValidatedPrime; i <= max; i+=2) { // we can skip even numbers
								// if it is dividable, it is not prime
								if ((prime % i)==0) {
									prime+=2; // we don't test even numbers
									continue search;
								}
							}

							// we found a new prime, write it back into the list of found primes
							for (int i=thePrimesIndex; i < thePrimes.length; i++) {
								long nextFoundPrime = thePrimes[i];
								if (nextFoundPrime==0 &&
									unsafe.compareAndSwapLong(thePrimes,LONG_ARRAY_OFFSET+i*LONG_ARRAY_SCALE, 0, prime)
								) {
									// done
									prime+=2; // we don't test even numbers
									continue search;
								}
							}
							// if we reached this point the primes array is full, so we're done with the search
							break primefind;
						}

						// tell other threads that the primes of this thread are validated
						processed[(int)(start>>>BLOCK_SHIFT)] = true;
						
						// find the value of the biggest validated number
						long biggestValidatedNumber = lastKnownValidatedPrime >>> BLOCK_SHIFT;
						for (int i=(int)biggestValidatedNumber; i < processed.length; i++) {
							if (!processed[i]) break;
							biggestValidatedNumber += BLOCK_SIZE;
						}

						// find the biggest validated prime number below the biggest validates number
						for (int i=lastKnownValidatedPrimeIndex; i < thePrimes.length; i++) {
							final long validatedPrim = thePrimes[i];
							if (validatedPrim==0 || validatedPrim > biggestValidatedNumber) break;
							if (validatedPrim > lastKnownValidatedPrime) {
								lastKnownValidatedPrime = validatedPrim;
								lastKnownValidatedPrimeIndex = i;
							}
						}
					}
				}
			};
			threads[i].start();
		}
		for (int i=0; i < threads.length; i++) {
			try {
				Thread.interrupted();
				threads[i].join();
			} catch (InterruptedException e) {}
		}
		thePrimes[0]=1L;
		
		// if the array is full, return it
		int i=thePrimes.length-1;
		if (thePrimes[i]!=0) {
			Arrays.sort(thePrimes);
			return thePrimes;
		}
		
		// otherwise truncate it
		for (;i >= 0; i--) {
			if (thePrimes[i]!=0) break;
		}
		final long[] theFinalPrimes = Arrays.copyOf(thePrimes,i);
		Arrays.sort(theFinalPrimes);
		return theFinalPrimes;
	}
	
	/**
	 * Calculte primes single threaded using the current thread.
	 * @param maxTime
	 * the maximal time to be consumed.
	 * @param maxTimeUnit
	 * the time unit in which the maxTime was provided.
	 * @return
	 * the found primes.
	 */
	public static final long[] getPrimes(final long maxTime, final TimeUnit maxTimeUnit ) {
		final long STOP_TIME = System.currentTimeMillis() + maxTimeUnit.toMillis(maxTime);
		long[] primes = new long[65536];
		primes[0] = 2L; // fix later
		primes[1] = 2L;
		primes[2] = 3L;
		int nextPrimeIndex = 3;
		long test = 5;
		search: while (true) {
			// only check time every 256 numbers
			if ((test & 0xFF)==1 && System.currentTimeMillis() >= STOP_TIME) {
				break search;
			}

			// try to divide the number by all already found primes (quick exclusion)
			for (int i=0; i < nextPrimeIndex; i++) {
				// if it is dividable, it is not prime
				if ((test % primes[i])==0) {
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
	
	
	public static void findPrimes(
		final int amount, final long maxTime, final TimeUnit maxTimeUnit, final boolean showResult
	) {
		final WeakReference<Object> gcMe = new WeakReference<Object>( new Object() );
		while (gcMe.get()!=null) {
			System.gc();
			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
				Thread.interrupted();
			}
		}
		long[] array = new long[amount];
		final long start = System.nanoTime();
			array = getPrimes(array,maxTime,maxTimeUnit);
		final long end = System.nanoTime();
		if (showResult) {
			System.out.println("Calculated "+array.length+" primes multi threaded in "+TimeUnit.NANOSECONDS.toMillis(end-start)+"ms");
			System.out.println("The biggest found prime was: "+array[array.length-1]);
		}
	}

	public static void findPrimes(
		final long maxTime, final TimeUnit maxTimeUnit, final boolean showResult
	) {
		final WeakReference<Object> gcMe = new WeakReference<Object>( new Object() );
		while (gcMe.get()!=null) {
			System.gc();
			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
				Thread.interrupted();
			}
		}
		long[] array;
		final long start = System.nanoTime();
			array = getPrimes(maxTime,maxTimeUnit);
		final long end = System.nanoTime();
		if (showResult) {
			System.out.println("Calculated "+array.length+" primes single threaded in "+TimeUnit.NANOSECONDS.toMillis(end-start)+"ms");
			System.out.println("The biggest found prime was: "+array[array.length-1]);
		}
	}

	public static void main(String... args) {
		System.out.print("Warming up... ");
		findPrimes(300,TimeUnit.MILLISECONDS,false); // warmup single threaded
		findPrimes(300,TimeUnit.MILLISECONDS,false); // warmup single threaded
		findPrimes(10_000,10,TimeUnit.SECONDS,false); // warmup multi threaded
		findPrimes(100_000,1,TimeUnit.SECONDS,false); // warmup multi threaded
		System.out.println("done");

		System.out.println("Running performance tests...");
		findPrimes(15,TimeUnit.SECONDS,true); // single threaded
		findPrimes(300_000,15,TimeUnit.SECONDS,true); // multi threaded
	}
}
