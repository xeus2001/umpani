package com.umpani.util.playground;

import java.lang.ref.WeakReference;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;

@SuppressWarnings("restriction")
public class PrimeGen {
	/**
	 * Returns the first primes unordered.
	 * @param primes
	 * the array to fill with primes, starting with 1. The array must be filled with 0.
	 */
	public static final void getPrimes( final AtomicLongArray primes ) {
		// we know the first three prime numbers
		primes.set(0, 2L); // we fix that later
		primes.set(1, 2L);
		primes.set(2, 3L);

		// search for primes using multiple threads
		final int threadCount = Runtime.getRuntime().availableProcessors();
		final Thread[] threads = new Thread[threadCount];
		final AtomicLong nextBlock = new AtomicLong(5);
		final Object doneMutex = new Object();
		synchronized (doneMutex) {
			for (int i=0; i < threads.length; i++) {
				threads[i] = new Thread() {
					@Override
					public void run() {
						primefind: while (true) {
							final long start = nextBlock.getAndAdd(1_000);
							final long end = start + 1_000;
							long prime = start;
							search: while (prime < end) {
								// try to divide the number by all already found primes (quick exclusion)
								for (int i=0; i < primes.length(); i++) {
									long nextFoundPrime = primes.get(i);
									if (nextFoundPrime==0) break;

									// if it is dividable, it is not prime
									if ((prime % nextFoundPrime)==0) {
										prime+=2; // we don't test even numbers
										continue search;
									}
								}
								
								// if the number is not dividable by any previously found prime it might be prime
								
								// test it
								final long max = (long)Math.ceil(Math.sqrt(prime));
								for (long i=3; i <= max; i+=2) { // we can skip even numbers
									// if it is dividable, it is not prime
									if ((prime % i)==0) {
										prime+=2; // we don't test even numbers
										continue search;
									}
								}

								// we found a new prime, write it back into the list of found primes
								for (int i=0; i < primes.length(); i++) {
									long nextFoundPrime = primes.get(i);
									if (nextFoundPrime==0 && primes.compareAndSet(i, 0, prime)) {
										// done
										prime+=2; // we don't test even numbers
										continue search;
									}
								}
								// if we reached this point the primes array is full, so we're done with the search
								break primefind;
							}
						}
						synchronized (doneMutex) {
							doneMutex.notifyAll();
						}
					}
				};
				threads[i].start();
			}
			try {
				doneMutex.wait();
			} catch (InterruptedException e) {}
		}
		primes.set(0, 1L);
	}
	public static void findPrimes( final int amount ) {
		final WeakReference<Object> gcMe = new WeakReference<Object>( new Object() );
		while (gcMe.get()!=null) {
			System.gc();
			try {
				Thread.sleep(10);
			} catch (InterruptedException e) {
				Thread.interrupted();
			}
		}
		final AtomicLongArray array = new AtomicLongArray(amount);
		final long start = System.nanoTime();
			getPrimes(array);
		final long end = System.nanoTime();
		for (int i=0; i < array.length(); i++) {
			System.out.println("prime #"+i+" = "+array.get(i));
		}
		System.out.println("time consumed: "+TimeUnit.NANOSECONDS.toMillis(end-start)+"ms");
	}

	public static void main(String... args) {
		// warmup
		findPrimes(10_000);
		// performance test, search for the first 50,000 prime numbers
		findPrimes(50_000);
	}
}
