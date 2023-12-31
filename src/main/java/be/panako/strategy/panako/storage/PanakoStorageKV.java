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


package be.panako.strategy.panako.storage;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.lmdbjava.Cursor;
import org.lmdbjava.Dbi;
import org.lmdbjava.DbiFlags;
import org.lmdbjava.Env;
import org.lmdbjava.GetOp;
import org.lmdbjava.SeekOp;
import org.lmdbjava.Stat;
import org.lmdbjava.Txn;

import be.panako.cli.Application;
import be.panako.util.Config;
import be.panako.util.FileUtils;
import be.panako.util.Key;

/**
 * A storage in a key value store
 */
public class PanakoStorageKV implements PanakoStorage{
	
	/**
	 * The single instance of the storage.
	 */
	private static PanakoStorageKV instance;

	/**
	 * A mutex for synchronization purposes
	 */
	private static final Object mutex = new Object();

	/**
	 * Uses a singleton pattern.
	 * @return Returns or creates a storage instance. This should be a thread
	 *         safe operation.
	 */
	public synchronized static PanakoStorageKV getInstance() {
		if (instance == null) {
			synchronized (mutex) {
				if (instance == null) {
					instance = new PanakoStorageKV();
				}
			}
		}
		return instance;
	}
	
	
	final Dbi<ByteBuffer> fingerprints;
	final Dbi<ByteBuffer> resourceMap;
	final Env<ByteBuffer> env;
	
	final Map<Long,List<long[]>> storeQueue;
	final Map<Long,List<long[]>> deleteQueue;
	final Map<Long,List<Long>> queryQueue;

	/**
	 * Create a new storage instance
	 */
	public PanakoStorageKV() {
		String folder = Config.get(Key.PANAKO_LMDB_FOLDER);
		folder = FileUtils.expandHomeDir(folder);
		
		if(!new File(folder).exists()) {
			FileUtils.mkdirs(folder);
		}
		if(!new File(folder).exists()) {
			throw new RuntimeException("Could not create LMDB folder: " + folder);
		}
		
		env =  org.lmdbjava.Env.create()
        .setMapSize(1024l * 1024l * 1024l * 1024l)//1 TB max!
        .setMaxDbs(2)
        .setMaxReaders(Application.availableProcessors())
        .open(new File(folder));
		
		final String fingerprintName = "panako_fingerprints";
		fingerprints = env.openDbi(fingerprintName, DbiFlags.MDB_CREATE, DbiFlags.MDB_INTEGERKEY, DbiFlags.MDB_DUPSORT, DbiFlags.MDB_DUPFIXED);
		
		final String resourceName = "panako_resource_map";		
		resourceMap = env.openDbi(resourceName,DbiFlags.MDB_CREATE, DbiFlags.MDB_INTEGERKEY);
		
		storeQueue = new HashMap<Long,List<long[]>>();
		deleteQueue = new HashMap<Long,List<long[]>>();
		queryQueue = new HashMap<Long,List<Long>>();
	}

	/**
	 * Closes the database environment.
	 */
	public void close() {
		env.close();
	}
	
	public void storeMetadata(long resourceID,String resourcePath,float duration, int fingerprints) {
		final ByteBuffer key = ByteBuffer.allocateDirect(8);
		byte[] resourcePathBytes = resourcePath.getBytes(java.nio.charset.StandardCharsets.UTF_8);
		final ByteBuffer val = ByteBuffer.allocateDirect(resourcePathBytes.length + 16); 
		key.putLong(resourceID).flip();
		
		val.putFloat(duration);
		val.putInt(fingerprints);
	    val.put(resourcePathBytes).flip();
	    
	    resourceMap.put(key, val);
	}
	

	public PanakoResourceMetadata getMetadata(long resourceID) {
		
		PanakoResourceMetadata metadata = null;
	    
		try (Txn<ByteBuffer> txn = env.txnRead()) {
			final ByteBuffer key = ByteBuffer.allocateDirect(8);
			key.putLong(resourceID).flip();
			
		    final ByteBuffer found = resourceMap.get(txn, key);
		    
		    if(found != null) {
		    	metadata = new PanakoResourceMetadata();
		    	final ByteBuffer fetchedVal = txn.val();
		    	metadata.duration = fetchedVal.getFloat();
		    	metadata.numFingerprints = fetchedVal.getInt();
		    	metadata.path = StandardCharsets.UTF_8.decode(fetchedVal).toString();
		    	metadata.identifier =(int) resourceID;
		    }
		    txn.close();
		    
		}catch(Exception e) {
			e.printStackTrace();
		}
		
		return metadata;    
	}
	

	public void addToStoreQueue(long fingerprintHash, int resourceIdentifier, int t1,int f1) {
		long[] data = {fingerprintHash,resourceIdentifier,t1,f1};
		long threadID = Thread.currentThread().getId();
		if(!storeQueue.containsKey(threadID))
			storeQueue.put(threadID, new ArrayList<long[]>());
		storeQueue.get(threadID).add(data);
	}
	

	public void processStoreQueue() {
		if (storeQueue.isEmpty())
			return;
		
		long threadID = Thread.currentThread().getId();
		if(!storeQueue.containsKey(threadID))
			return;
		
		List<long[]> queue = storeQueue.get(threadID);
		
		if (queue.isEmpty())
			return;
		
		try (Txn<ByteBuffer> txn = env.txnWrite()) {
			
			final ByteBuffer key = ByteBuffer.allocateDirect(8).order(java.nio.ByteOrder.LITTLE_ENDIAN);
		    final ByteBuffer val = ByteBuffer.allocateDirect(3*4);
		    
		      // A cursor always belongs to a particular Dbi.
		      final Cursor<ByteBuffer> c = fingerprints.openCursor(txn);
		      
		      for(long[] data : queue) {
		    	  key.putLong(data[0]).flip();
		    	  val.putInt((int) data[1]).putInt((int) data[2]).putInt((int) data[3]).flip();
		    	  
		    	  c.put(key, val);

		    	  key.clear();
		    	  val.clear();
		      }  
		            
		      c.close();
		      txn.commit();
		      queue.clear();
		    }catch (Exception e) {
		    	e.printStackTrace();
		    }
	}

	@Override
	public void addToDeleteQueue(long fingerprintHash, int resourceIdentifier, int t1,int f1) {
		long[] data = {fingerprintHash,resourceIdentifier,t1,f1};
		long threadID = Thread.currentThread().getId();
		if(!deleteQueue.containsKey(threadID))
			deleteQueue.put(threadID, new ArrayList<long[]>());
		deleteQueue.get(threadID).add(data);
	}

	@Override
	public void processDeleteQueue() {
		if (storeQueue.isEmpty())
			return;
		
		long threadID = Thread.currentThread().getId();
		if(!storeQueue.containsKey(threadID))
			return;
		
		List<long[]> queue = storeQueue.get(threadID);
		
		if (queue.isEmpty())
			return;
		
		try (Txn<ByteBuffer> txn = env.txnWrite()) {
			
			final ByteBuffer key = ByteBuffer.allocateDirect(8).order(java.nio.ByteOrder.LITTLE_ENDIAN);
		    final ByteBuffer val = ByteBuffer.allocateDirect(3*4);
		    
		      // A cursor always belongs to a particular Dbi.
		      final Cursor<ByteBuffer> c = fingerprints.openCursor(txn);
		      
		      for(long[] data : queue) {
		    	  key.putLong(data[0]).flip();
		    	  val.putInt((int) data[1]).putInt((int) data[2]).putInt((int) data[3]).flip();
		    	  if(c.get(key,val,SeekOp.MDB_GET_BOTH)) {
		    		  c.delete();
		    	  }
		    	  key.clear();
		    	  val.clear();
		      }  
		      
		      c.close();
		      txn.commit();
		      queue.clear();
		    }catch (Exception e) {
		    	e.printStackTrace();
		    }
	}

	@Override
	public void addToQueryQueue(long queryHash) {
		long threadID = Thread.currentThread().getId();
		if(!queryQueue.containsKey(threadID))
			queryQueue.put(threadID, new ArrayList<Long>());
		queryQueue.get(threadID).add(queryHash);
	}

	@Override
	public void processQueryQueue(Map<Long,List<PanakoHit>> matchAccumulator,int range) {
		processQueryQueue(matchAccumulator, range, new HashSet<Integer>());
	}

	@Override
	public void processQueryQueue(Map<Long,List<PanakoHit>> matchAccumulator,int range,Set<Integer> resourcesToAvoid) {
		
		if (queryQueue.isEmpty())
			return;
		
		long threadID = Thread.currentThread().getId();
		if(!queryQueue.containsKey(threadID))
			return;
		
		List<Long> queue = queryQueue.get(threadID);
		
		if (queue.isEmpty())
			return;
		
		try (Txn<ByteBuffer> txn = env.txnRead()) {
			  // A cursor always belongs to a particular Dbi.
		      final Cursor<ByteBuffer> c = fingerprints.openCursor(txn);
		      
		      final ByteBuffer keyBuffer = ByteBuffer.allocateDirect(8).order(java.nio.ByteOrder.LITTLE_ENDIAN);
		      
		      for(long originalKey : queue) {
		    	  
		    	  long startKey = originalKey - range;
		    	  long stopKey = originalKey + range;
		    	  
		    	  keyBuffer.putLong(startKey).flip();
			      
			      if(c.get(keyBuffer, GetOp.MDB_SET_RANGE)) {
			    	  long fingerprintHash =  c.key().order(java.nio.ByteOrder.LITTLE_ENDIAN).getLong();
		    		  long resourceID = c.val().getInt();
				      long t = c.val().getInt();
				      long f = c.val().getInt();
				      
				      //System.out.printf("Direct match: %d id %d t1 %d f1 %d\n",fingerprintHash , resourceID,t, f);
				      
				      if(fingerprintHash <= stopKey) {
				    	  if(!resourcesToAvoid.contains((int) resourceID)) {
				    		  if(!matchAccumulator.containsKey(originalKey))
				    			  matchAccumulator.put(originalKey,new ArrayList<PanakoHit>());
				    		  matchAccumulator.get(originalKey).add(new PanakoHit(originalKey, fingerprintHash, t, resourceID, f));
				    	  }
				   
					      while(true) {
					    	  while(c.seek(SeekOp.MDB_NEXT_DUP)) {
					    		  fingerprintHash =  c.key().order(java.nio.ByteOrder.LITTLE_ENDIAN).getLong();
					    		  resourceID = c.val().getInt();
							      t = c.val().getInt();
							      f = c.val().getInt();
							      //System.out.printf("Dup Hash: %d id %d t1 %d\n",fingerprintHash , resourceID,t);
							      
							      if(!resourcesToAvoid.contains((int) resourceID)) {
						    		  if(!matchAccumulator.containsKey(originalKey))
						    			  matchAccumulator.put(originalKey,new ArrayList<PanakoHit>());
						    		  matchAccumulator.get(originalKey).add(new PanakoHit(originalKey, fingerprintHash, t, resourceID,f));
						    	  }
						      }
						      
						      if(c.seek(SeekOp.MDB_NEXT)) {
						    	  fingerprintHash =  c.key().order(java.nio.ByteOrder.LITTLE_ENDIAN).getLong();
						    	  resourceID = c.val().getInt();
							      t = c.val().getInt();
							      f = c.val().getInt();
							      if(fingerprintHash > stopKey)
							    	  break;
							      
							      //System.out.printf("Next Hash: %d id %d t1 %d\n",fingerprintHash , resourceID,t);
							      
							      
							      if(!resourcesToAvoid.contains((int) resourceID)) {
						    		  if(!matchAccumulator.containsKey(originalKey))
						    			  matchAccumulator.put(originalKey,new ArrayList<PanakoHit>());
						    		  matchAccumulator.get(originalKey).add(new PanakoHit(originalKey, fingerprintHash, t, resourceID,f));
						    	  }
						      } else {
						    	  //no next found, end of db
						    	  break;
						      }
					      }
				      }
			      }
		      }
		      c.close();
		      txn.commit();
		      queue.clear();
		}
		
	}

	@Override
	public void printStatistics(boolean detailedStats){
		long entries = 0;
		final Stat stats;
	    try (Txn<ByteBuffer> txn = env.txnRead()) {
	      stats = fingerprints.stat(txn);
	      entries = stats.entries;
	      
	      if(detailedStats) {
	    	  
	    	  String folder = Config.get(Key.OLAF_LMDB_FOLDER);
	    	  String dbpath = FileUtils.combine(folder,"data.mdb");
	    	  long dbSizeInMB = new File(dbpath).length() / (1024 * 1024);
	    	  
		      System.out.printf("[MDB INDEX statistics]\n");
		      System.out.printf("=========================\n");
		      System.out.printf("> Size of database page:        %d\n", stats.pageSize);
		      System.out.printf("> Depth of the B-tree:          %d\n", stats.depth);
		      System.out.printf("> Number of items in databases: %d\n", stats.entries);
		      System.out.printf("> File size of the databases:   %dMB\n", dbSizeInMB);
		      System.out.printf("=========================\n\n");
	      }
	      txn.close();     
	    }
	    
	    try (Txn<ByteBuffer> txn = env.txnRead()) {
			  // A cursor always belongs to a particular Dbi.
		      final Cursor<ByteBuffer> c = resourceMap.openCursor(txn);
		      
		      final ByteBuffer keyBuffer = ByteBuffer.allocateDirect(8);
		      keyBuffer.putLong(0l).flip();
		      
		      double totalDuration = 0;
		      long totalPrints = 0;
		      long totalResources = 0;
		      
		      double maxPrintsPerSecond = 0;
		      String maxPrintsPerSecondPath = "";
		      double minPrintsPerSecond = 100000;
		      String minPrintsPerSecondPath = "";
		      
		      while(c.seek(SeekOp.MDB_NEXT)) {
		    	  
		    	 //long resourceIdentifier =  c.key().getLong();
			     float duration = c.val().getFloat();
			     int numFingerprints =  c.val().getInt();
			     float printsPerSecond =  (float) numFingerprints / duration;
			     String path = StandardCharsets.UTF_8.decode(c.val()).toString();
			     
			     if(printsPerSecond > maxPrintsPerSecond) {
			    	 maxPrintsPerSecond = printsPerSecond;
			    	 maxPrintsPerSecondPath = path;
			     }
			     
			     if(printsPerSecond < minPrintsPerSecond) {
			    	 minPrintsPerSecond = printsPerSecond;
			    	 minPrintsPerSecondPath = path;
			     }
		    	 
			     //System.out.printf("> %13d   %.3fs   %7dfp   %5.1ffp/s   '%s'\n",resourceIdentifier,duration,numFingerprints,printsPerSecond,path);
			     
			     totalDuration += duration;
			     totalPrints += numFingerprints;
			     totalResources++;
		      }
		      
		      double avgPrintsPerSecond =   totalPrints / totalDuration;
		      //System.out.printf("=========================\n\n");
		      
		      System.out.printf("[MDB INDEX TOTALS]\n");
		      System.out.printf("=========================\n");
		      System.out.printf("> %d audio files \n",totalResources);
		      System.out.printf("> %.3f seconds of audio\n",totalDuration);
		      System.out.printf("> %d fingerprint hashes \n",totalPrints);
		      System.out.printf("=========================\n\n");
		      
		      System.out.printf("[MDB INDEX INFO]\n");
		      System.out.printf("=========================\n");
		      System.out.printf("> Avg prints per second: %5.1ffp/s \n",avgPrintsPerSecond);
		      System.out.printf("> Min prints per second: %5.1ffp/s '%s'\n",minPrintsPerSecond,minPrintsPerSecondPath);
		      System.out.printf("> Max prints per second: %5.1ffp/s '%s'\n",maxPrintsPerSecond,maxPrintsPerSecondPath);
		      System.out.printf("=========================\n\n");
		      
		      c.close();
		      txn.close();
	    }
	}

	@Override
	public void deleteMetadata(long resourceID) {	
		try (Txn<ByteBuffer> txn = env.txnWrite()) {
			
			final ByteBuffer key = ByteBuffer.allocateDirect(8);
			key.putLong(resourceID).flip();
			
			final ByteBuffer found = resourceMap.get(txn, key);
			if(found !=null) {
				resourceMap.delete(txn, key);
			}else {
				//not found, not deleted
			}
			
		    txn.commit();
		 
	    }catch (Exception e) {
	    	e.printStackTrace();
	    }
	}

	public void clear() {
		fingerprints.close();
		resourceMap.close();
		env.close();
		
		String folder = Config.get(Key.PANAKO_LMDB_FOLDER);
		folder = FileUtils.expandHomeDir(folder);
		
		if(!FileUtils.exists(folder))
			return;
	
		for(File f : new File(folder).listFiles()) {
			FileUtils.rm(f.getAbsolutePath());
			System.out.println("Removed " + f.getAbsolutePath());
		}
		
	}
}
