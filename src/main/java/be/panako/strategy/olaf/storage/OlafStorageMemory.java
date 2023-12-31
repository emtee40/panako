/***************************************************************************
*                                                                          *
* Panako - acoustic fingerprinting                                         *
* Copyright (C) 2014 - 2022 - Joren Six / IPEM                             *
*                                                                          *
* This program is free software: you can redistribute it and/or modify     *
* it under the terms of the GNU Affero General Public License as           *
* published by the Free Software Foundation, either version 3 of the       *
* License, or (at your option) any later version.                          *
*                                                                          *
* This program is distributed in the hope that it will be useful,          *
* but WITHOUT ANY WARRANTY; without even the implied warranty of           *
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the            *
* GNU Affero General Public License for more details.                      *
*                                                                          *
* You should have received a copy of the GNU Affero General Public License *
* along with this program.  If not, see <http://www.gnu.org/licenses/>     *
*                                                                          *
****************************************************************************
*    ______   ________   ___   __    ________   ___   ___   ______         *
*   /_____/\ /_______/\ /__/\ /__/\ /_______/\ /___/\/__/\ /_____/\        *
*   \:::_ \ \\::: _  \ \\::\_\\  \ \\::: _  \ \\::.\ \\ \ \\:::_ \ \       *
*    \:(_) \ \\::(_)  \ \\:. `-\  \ \\::(_)  \ \\:: \/_) \ \\:\ \ \ \      *
*     \: ___\/ \:: __  \ \\:. _    \ \\:: __  \ \\:. __  ( ( \:\ \ \ \     *
*      \ \ \    \:.\ \  \ \\. \`-\  \ \\:.\ \  \ \\: \ )  \ \ \:\_\ \ \    *
*       \_\/     \__\/\__\/ \__\/ \__\/ \__\/\__\/ \__\/\__\/  \_____\/    *
*                                                                          *
****************************************************************************
*                                                                          *
*                              Panako                                      *
*                       Acoustic Fingerprinting                            *
*                                                                          *
****************************************************************************/

package be.panako.strategy.olaf.storage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * An in memory storage system: mainly used for debugging.
 */
public class OlafStorageMemory implements OlafStorage {

	/**
	 * The single instance of the storage.
	 */
	private static OlafStorageMemory instance;

	/**
	 * A mutex for synchronization purposes
	 */
	private static final Object mutex = new Object();

	/**
	 * Uses a singleton pattern.
	 * @return Returns or creates a storage instance. This should be a thread
	 *         safe operation.
	 */
	public synchronized static OlafStorageMemory getInstance() {
		if (instance == null) {
			synchronized (mutex) {
				if (instance == null) {
					instance = new OlafStorageMemory();
				}
			}
		}
		return instance;
	}
	
	
	private final TreeMap<Long, List<int[]>> fingerprints;
	private final HashMap<Long, OlafResourceMetadata> resourceMap;
	
	final Map<Long,List<Long>> queryQueue;

	/**
	 * Create a new empty memory storage instance
	 */
	public OlafStorageMemory() {
		fingerprints = new TreeMap<>();
		resourceMap = new HashMap<>();
		queryQueue = new HashMap<>();
	}
	
	@Override
	public void storeMetadata(long resourceID, String resourcePath, float duration, int fingerprints) {
		OlafResourceMetadata r = new OlafResourceMetadata();
		r.duration = duration;
		r.numFingerprints = fingerprints;
		r.path = resourcePath;
		r.identifier = (int) resourceID;
		
		resourceMap.put(resourceID, r);
	}
	
	@Override
	public OlafResourceMetadata getMetadata(long identifier) {
		return resourceMap.get(identifier);
	}
	

	@Override
	public void addToStoreQueue(long fingerprintHash, int resourceIdentifier, int t1) {
		int[] val = {resourceIdentifier,t1};
		if(!fingerprints.containsKey(fingerprintHash)) {
			List<int[]> list = new ArrayList<>();
			fingerprints.put(fingerprintHash,list);
		}
		fingerprints.get(fingerprintHash).add(val);
	}

	@Override
	public void processStoreQueue() {
		//NOOP
	}

	@Override
	public void clearStoreQueue() {
		//noop
	}

	@Override
	public void printStatistics(boolean printDetailedStats) {

	}

	public void addToQueryQueue(long queryHash) {
		long threadID = Thread.currentThread().getId();
		if(!queryQueue.containsKey(threadID))
			queryQueue.put(threadID, new ArrayList<>());
		queryQueue.get(threadID).add(queryHash);
	}

	@Override
	public void processQueryQueue(Map<Long,List<OlafHit>> matchAccumulator,int range,Set<Integer> resourcesToAvoid) {
		if (queryQueue.isEmpty())
			return;
		
		long threadID = Thread.currentThread().getId();
		if(!queryQueue.containsKey(threadID))
			return;
		
		List<Long> queue = queryQueue.get(threadID);
		
		if (queue.isEmpty())
			return;
		
		for (long originalKey : queue) {
			long startKey = originalKey - range;
			long stopKey = originalKey + range;
			for (long key = startKey; key <= stopKey; key++) {
				List<int[]> results = fingerprints.get(key);
				if (results != null) {
					for (int[] result : results) {
						if (!matchAccumulator.containsKey(originalKey))
							matchAccumulator.put(originalKey, new ArrayList<OlafHit>());
						long fingerprintHash = key;
						long resourceID = result[0];
						long t = result[1];
						if(!resourcesToAvoid.contains((int) resourceID))
						matchAccumulator.get(originalKey)
								.add(new OlafHit(originalKey, fingerprintHash, t, resourceID));
					}
				}
			}
		}
		queue.clear();
	}

	@Override
	public void addToDeleteQueue(long fingerprintHash, int resourceIdentifier, int t1) {
		System.err.println("Delete not implemented for Memory storage");
	}

	@Override
	public void processDeleteQueue() {
		System.err.println("Delete not implemented for Memory storage");
	}

	@Override
	public void deleteMetadata(long resourceID) {

	}

	@Override
	public void clear() {
		fingerprints.clear();
		resourceMap.clear();
	}
}
