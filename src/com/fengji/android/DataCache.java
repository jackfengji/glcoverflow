package com.fengji.android;

import java.util.LinkedHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class DataCache<K, E> {
	private int mCapacity;
	private LinkedHashMap<K, E> mCache;
	private final ReadWriteLock mReadWriteLock = new ReentrantReadWriteLock();

	public DataCache(int capacity) {
		mCapacity = capacity;
		mCache = new LinkedHashMap<K, E>(mCapacity) {
			private static final long serialVersionUID = -9165777183357349715L;

			@Override
			protected boolean removeEldestEntry(Entry<K, E> eldest) {
                if (size() > mCapacity) {
                    mReadWriteLock.writeLock().lock();
                    remove(eldest.getKey());
                    mReadWriteLock.writeLock().unlock();
                }

                return false;
			}
		};
	}
	
	public E objectForKey(K key) {
		mReadWriteLock.readLock().lock();
		final E result = mCache.get(key);
        //android.util.Log.i("DataCache", "get key: " + key.toString() + " " + result);
		mReadWriteLock.readLock().unlock();
		
		return result;
	}
	
	public void putObjectForKey(final K key, final E value) {
		if (key != null && value != null) {
			mReadWriteLock.writeLock().lock();
			mCache.put(key, value);
            //android.util.Log.i("DataCache", "put key: " + key.toString() + " " + value.toString());
			mReadWriteLock.writeLock().unlock();
		}
	}
	
	public boolean containsKey(final K key) {
		mReadWriteLock.readLock().lock();
		final boolean result = mCache.containsKey(key);
		mReadWriteLock.readLock().unlock();
		
		return result;
	}
	
	public void clear() {
		mReadWriteLock.writeLock().lock();
		mCache.clear();
		mReadWriteLock.writeLock().unlock();
	}

    public void setCapacity(int capacity) {
        mCapacity = capacity;
    }
}
