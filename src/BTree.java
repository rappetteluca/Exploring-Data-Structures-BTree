/**
 * @author Lucas Rappette
 */
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.Stack;

public class BTree 
{

	private RandomAccessFile f;
	private int order;
	private int blockSize;
	private long root;
	private long free;
	private Stack<BTreeNode> path;
	private Stack<Long> pathAddresses;
	private int padding;
	private int minKeys;
	
	public BTree(String filename, int bsize) 
	{ 	//All BTreeNodes will use bsize bytes
		root = 0;
		free = 0;
		blockSize = bsize;
		order = blockSize/12;
		padding = blockSize%12;
		minKeys = (order/2) - 1;
		try
		{
			File target = new File(filename + ".tree");
			if(target.exists())
			{
				target.delete();
			}
			target.createNewFile();
			
			f = new RandomAccessFile(target, "rw");
			f.seek(0);
			f.writeLong(root);
			f.writeLong(free);
			f.writeInt(bsize);
		}
		catch(IOException e)
		{
			
		}
	}

	public BTree(String filename) 
	{
		File target = new File(filename + ".tree");
		try
		{
			if (!target.exists())
			{
				//Do not initialize, BTree File associated with calling DBtable DNE.
				throw new IOException();
			}
			f = new RandomAccessFile(target, "rw");
			f.seek(0);
			root = f.readLong();
			free = f.readLong();
			blockSize = f.readInt();
			padding = blockSize%12;
			order = blockSize/12;
			minKeys = (order/2) - 1;
				
		}
		catch(IOException e)
		{
			System.out.println("ERROR: BTree file associated with DBTable does not exist.");
			System.exit(1); //Abnormal Termination
		}
		
	}
	
	/**
	 * Inserts B Tree data in to the BTree
	 * @param key the key of the data to be inserted
	 * @param addr the address in the DBTable where the data is stored
	 * @return true if key is not a duplicate in BTree and is inserted
	 * @return false if key is a duplicate (key not inserted)
	 */
	public boolean insert(int key, long addr) 
	{
		boolean split = true;
		BTreeNode node;
		long nodeAddr;
		if (search(key) == 0) //Key not found, so add key
		{
			while(!path.empty() && split)
			{
				node = path.pop();
				nodeAddr = pathAddresses.pop();
				//If there is room in the node..
				if (Math.abs(node.count) < node.keys.length) 
				{
					insertVal(node, key, addr);
					writeNode(nodeAddr, node);
					split = false;
				}
				else
				{
					addr = splitNode(node, nodeAddr, key, addr); //new address to insert
					node = new BTreeNode(nodeAddr);
					node = new BTreeNode(addr);
					
					while(!node.isLeaf)
						node = new BTreeNode(node.children[0]); //Move to medianKey
						
					key = node.keys[0]; //New key to insert (medianKey)
				}
			}
			if (split) //Root was split.
			{
				BTreeNode newRoot;
				if (root == 0) //No Root exists, first insert, and first node.
				{
					newRoot = new BTreeNode(0, new int[order-1], new long[order]);
					insertVal(newRoot, key, addr);
				}
				else
				{
					newRoot = new BTreeNode(1, new int[order -1], new long[order]);
					newRoot.children[0] = root;
					newRoot.keys[0] = key;
					newRoot.children[1] = addr;
				}
				root = getFree();
				writeNode(root, newRoot);	
			}
			return true;
		}
		return false;
	}
	
	/**
	 * Splits a BTreeNode. Median key is the first key in newNode. Updates both nodes
	 * @param node The node to be split
	 * @param nodeAddr The address of the node to be split
	 * @param key The key to be included in the split.
	 * @param DBaddr The data associated with the key to be included in split
	 * @return The address where the medianKey can be found relative to the child.
	 */
	private long splitNode(BTreeNode node, long nodeAddr, int key, long DBaddr) 
	{
		//Splits node and returns the new address to be used in splitting
		long returnAddr = 0;
		BTreeNode newNode;
		BTreeNode oldNode;
		ArrayList<Integer> keys = new ArrayList<Integer>();
		int medianKey;
		
		if (node.isLeaf)
		{
			newNode = new BTreeNode(-1 * ((order+1)/2), new int[order-1], new long [order]);
			oldNode = new BTreeNode(-1 * (((order)/2)), new int[order-1], new long[order]);
		}
		else
		{
			newNode = new BTreeNode((((order+1)/2)- 1), new int[order-1], new long[order]);
			oldNode = new BTreeNode((((order)/2)), new int[order-1], new long[order]);
		}
		
		keys.add(key);
		for (int k : node.keys)
			keys.add(k);
		Collections.sort(keys);
		medianKey = keys.get(keys.size()/2);
		int keyToInsert;
		long addressToInsert = 0;
		
		for (int i = 0; i < keys.size(); i++)
		{
			keyToInsert = keys.get(i);
			
			if (keyToInsert == key)
			{
				addressToInsert = DBaddr;
			}
			else
			{
				for (int j = 0; j < Math.abs(node.count); j++)
				{
					if (node.keys[j] == keyToInsert)
					{
						if (node.isLeaf)
						{
							addressToInsert = node.children[j];
						}
						else
							addressToInsert = node.children[j+1];
						
						break;
					}
				}
			}
			
			if (keyToInsert < medianKey) //Insert Key in Old Node
			{
				oldNode.keys[i] = keyToInsert;
				if (!oldNode.isLeaf)
				{
					if (oldNode.children[0] == 0)
					{
						oldNode.children[0] = node.children[0];
					}
					
					oldNode.children[i+1] = addressToInsert;
				}
				else
				{
					oldNode.children[i] = addressToInsert;
				}
			}
			else if (keyToInsert == medianKey)
			{
				if (newNode.isLeaf)
				{
					newNode.keys[0] = medianKey;
					newNode.children[0] = addressToInsert;
				}
				else
				{
					newNode.children[0] = addressToInsert;
				}
			}
			else //if(keyToInsert > medianKey) //Insert Key into newNode
			{
				if (!newNode.isLeaf)
				{	
					newNode.keys[i-Math.abs(oldNode.count) - 1] = keyToInsert;
					newNode.children[(i-Math.abs(oldNode.count))] = addressToInsert;
				}
				else
				{
					newNode.keys[i-Math.abs(oldNode.count)] = keyToInsert;
					newNode.children[i-Math.abs(oldNode.count)] = addressToInsert;
				}
			}
		}
		
		node = oldNode;
		writeNode(nodeAddr, node);
		returnAddr = getFree();
		writeNode(returnAddr, newNode);
		return returnAddr;
	}
	
	/**
	 * Inserts data into a BTreeNode in sorted order. Does not update node.
	 * @param node The BTreeNode
	 * @param keyThe key to be inserted into node
	 * @param addr The address to be inserted into node.
	 */
	private void insertVal(BTreeNode node, int key, long addr)
	{
		//Insert into node in sorted Order
		int pos = Math.abs(node.count);
		for(int i = Math.abs(node.count) - 1; i >= 0; i--)
		{
			if(node.keys[i] > key)
			{
				pos = i;
				node.keys[i+1] = node.keys[i];
				if (node.isLeaf)
					node.children[i+1] = node.children[i];
				else
					node.children[i+2] = node.children[i+1];	
			}
		}
		
		if (node.isLeaf)
		{
			node.children[pos] = addr;
			node.count--;
		}
		else //Node is a leaf
		{
			if(pos == 0)
			{
				BTreeNode temp = new BTreeNode(node.children[pos]);
				if (temp.keys[0] >= key)
				{
					node.children[pos + 1] = node.children[0];
					node.children[pos] = addr;
				}
				else
					node.children[pos + 1] = addr;
			}
			else
				node.children[pos + 1] = addr;
			
			node.count++;
		}
		
		node.keys[pos] = key;
	}
	
	/**
	 * Builds Stack path and pathAddresses. Returns DBTable address from key
	 * @param k the key to be searched for
	 * @return	The address associated with the key, otherwise return 0
	 */
	public long search(int k) 
	{
		//Return DBTable "row" address associated with key otherwise return 0.
		//Make a new stack every search.
		path = new Stack<BTreeNode>();
		pathAddresses = new Stack<Long>();
		if (root == 0)
		{
			return 0;
		}
		else
		{
			pathAddresses.push(root);
			return searchAux(new BTreeNode(root), k);
		}
	}
	
	/**
	 * Recursively searches the subTree for the key
	 * @param subTree 
	 * @param key
	 * @return
	 */
	private long searchAux(BTreeNode subTree, int key) 
	{
		path.push(subTree);
		for (int j = 0; j < Math.abs(subTree.count); j++)
		{
			if (key < subTree.keys[j] && !subTree.isLeaf)
			{
				pathAddresses.push(subTree.children[j]);
				return searchAux(new BTreeNode(subTree.children[j]), key);
			}
			else if (key >= subTree.keys[j] && !subTree.isLeaf &&
					(j == (Math.abs(subTree.count)) - 1) )
			{
				pathAddresses.push(subTree.children[j + 1]);
				return searchAux(new BTreeNode(subTree.children[j + 1]), key);
			}
			else if (key == subTree.keys[j] && subTree.isLeaf)
			{
				return subTree.children[j]; //Return DBTable Address
			}
		}
		//Key DNE so return 0;
		return 0;
	}
	
	/**
	 * Writes a BTreeNode's data to the B Tree file
	 * @param addr the address to start writing at
	 * @param n BTreeNode
	 */
	private void writeNode(long addr, BTreeNode n)
	{
		try 
		{
			f.seek(addr);
			if (n.isFree == false)
			{
				f.writeInt(n.count);
				for (int i = 0; i < n.keys.length; i++)
				{
					f.writeInt(n.keys[i]);
				}
				for (int i = 0; i < n.children.length; i++)
				{
					f.writeLong(n.children[i]);
				}
				for (int i = 0; i < padding; i ++)
				{
					f.writeByte(0);
				}
			}
			else
			{
				f.writeInt(n.count); //Flag for free node
				f.writeLong(n.nextFree);
				for (int i = 12; i < blockSize; i++)
				{
					f.writeByte(0); 
				}
			}
		}
		catch (IOException e) 
		{
			e.printStackTrace();
		}
		
	}
	/**
	 * Searches the BTree for keys within range low to high inclusive
	 * @param low The lowest key to search for
	 * @param high The highest key to search for
	 * @return a LinkedList of DBAddresses associated with each key within range
	 */
	public LinkedList<Long> rangeSearch(int low, int high)
	{
		LinkedList<Long> dbAddrs = new LinkedList<Long>();
		rangeAux(dbAddrs, new BTreeNode(root), low, high);
		return dbAddrs;
	}
	/**
	 * Auxillary Method for Range Search
	 * @param dbAddrs
	 * @param node
	 * @param low
	 * @param high
	 */
	private void rangeAux(LinkedList<Long> dbAddrs, BTreeNode node, int low, int high) 
	{
		if (!node.isLeaf)
		{
			for (int i = 0; i < node.count; i++)
			{
				if (node.keys[i] <=high && node.keys[i] >= low)
				{
					if (i != 0 && node.keys[i-1] < low) //Check last child, may contain values in range
					{
						rangeAux(dbAddrs, new BTreeNode(node.children[i]), low, high);
					}
					else if (i == 0)
					{
						rangeAux(dbAddrs, new BTreeNode(node.children[0]), low, high);
					}
					
					rangeAux(dbAddrs, new BTreeNode(node.children[i+1]), low, high);
				}
				
			}
		}
		if (node.isLeaf)
		{
			for (int i = 0; i < Math.abs(node.count); i++)
			{
				if (node.keys[i] <= high && node.keys[i] >= low)
				{
					dbAddrs.add(node.children[i]);
				}
			}
		}
	}
	
	/**
	 * Removes a key from the BTree and updates the BTree
	 * @param key - The key to be removed
	 * @return The DBAddress associated with the removed Key.
	 */
	public long remove(int key) 
	{
		int originalKey = key;
		boolean tooSmall = false;
		boolean canBorrow = false;
		long returnAddr = search(key);
		long nodeAddr;
		long childAddr;
		BTreeNode child;
		BTreeNode node;
		if (returnAddr != 0) //Key found
		{
			node = path.pop();
			nodeAddr = pathAddresses.pop();
			tooSmall = removeVal(node, key, nodeAddr, false);
			while(!path.empty() && tooSmall)
			{
				child = node;
				childAddr = nodeAddr;
				node = path.pop();
				nodeAddr = pathAddresses.pop();
				canBorrow = checkNeighbors(node, childAddr);
				if (canBorrow)
				{
					shiftValues(child, childAddr, node, nodeAddr);
					writeNode(childAddr, child);
					updateParent(node, originalKey);
					tooSmall = false;
					writeNode(nodeAddr, node);
				}
				else
				{
					key = combineChild(node, child, childAddr);
					writeNode(childAddr, child);
					tooSmall = removeVal(node, key, nodeAddr, false);
					updateParent(node, originalKey);
					writeNode(nodeAddr, node);
				}
				
			}
			if (tooSmall) //Root is empty
			{
				long oldRoot = nodeAddr;
				if (!node.isLeaf)
				{
					root = node.children[0];
				}
				else
				{
					root = 0;
				}
				addFree(oldRoot, node);
			}
			else
			{
				updateParent(node, originalKey);
				writeNode(nodeAddr, node);
			}
			while (!path.empty())
			{
				node = path.pop();
				nodeAddr = pathAddresses.pop();
				updateParent(node, originalKey);
				writeNode(nodeAddr, node);
			}
		}
		return returnAddr; //Returns 0 if Key not Found 
		/*
		If  the key is in the Btree, remove the key and return the address of the row
		return 0 if the key is not found in the B+tree
		*/
	}
	
	/**
	 * Updates the ParentNode's keys if one is out of date
	 * @param node
	 * @param key
	 */
	private void updateParent(BTreeNode node, int key) 
	{
		BTreeNode child;
		for (int i = 0; i < Math.abs(node.count); i++)
		{
			if (node.keys[i] == key)
			{
				child = new BTreeNode(node.children[i+1]);
				while (!child.isLeaf)
				{
					child = new BTreeNode(child.children[0]);
				}
				node.keys[i] = child.keys[0];
				break;
			}
		}
	}
	/**
	 * Combines child Node with a suitable neighbor.
	 * @param node
	 * @param child
	 * @param childAddr
	 * @return The new key to be removed/modified from combining children
	 */
	private int combineChild(BTreeNode node, BTreeNode child, long childAddr) 
	{
		BTreeNode temp;
		int key = 0;
		for (int i = 0; i < node.count + 1; i++)
		{
			if (node.children[i] == childAddr)
			{
				if (i != 0)
				{
					temp = new BTreeNode(node.children[i-1]);
					if (temp.isLeaf)
					{
						for (int j = 0; j < Math.abs(child.count); j++)
						{
							insertVal(temp, child.keys[j], child.children[j]);
						}
						key = node.keys[i-1];
						addFree(childAddr, child);
						writeNode(node.children[i-1], temp);
					}
					else
					{
						insertVal(temp, node.keys[i-1], child.children[0]);
						for (int j = 0; j < Math.abs(child.count); j++)
						{
							insertVal(temp, child.keys[j], child.children[j+1]);
						}
						key = node.keys[i-1];
						addFree(childAddr, child);
						writeNode(node.children[i-1], temp);
					}
					break;
					
				}
				else if (i != node.count)
				{
					temp = new BTreeNode(node.children[i+1]);
					if (temp.isLeaf)
					{
						key = temp.keys[0];
						for (int j = 0; j < Math.abs(temp.count); j++)
						{
							insertVal(child, temp.keys[j], temp.children[j]);
						}
						addFree(node.children[i+1], temp);
					}
					else
					{
						insertVal(child, node.keys[i], temp.children[0]);
						for (int j = 0; j < Math.abs(temp.count); j++)
						{
							insertVal(child, temp.keys[j], temp.children[j+1]);
						}
						key = node.keys[i];
						addFree(node.children[i+1], temp);
					}
					break;
				}
			}
		}
		return key;
	}
	/**
	 * Shifts values between child node and a suitable neighbor. Updates parent node
	 * @param child
	 * @param childAddr
	 * @param node
	 * @param nodeAddr
	 */
	private void shiftValues(BTreeNode child, long childAddr, BTreeNode node, long nodeAddr) 
	{
		BTreeNode temp;
		for (int i = 0; i < node.count + 1; i++)
		{
			if (node.children[i] == childAddr)
			{
				if (i != 0)
				{
					temp = new BTreeNode(node.children[i-1]);
					if (Math.abs(temp.count) > minKeys)
					{
						if (temp.isLeaf)
						{
							insertVal(child, temp.keys[Math.abs(temp.count)-1], temp.children[Math.abs(temp.count)-1]);
							removeVal(temp, temp.keys[Math.abs(temp.count)-1], temp.children[Math.abs(temp.count)-1], false);
							writeNode(node.children[i-1], temp);
							node.keys[i-1] = child.keys[0];
						}
						else
						{
							int newKey = 0;
							temp = new BTreeNode(node.children[i]);
							while (!temp.isLeaf)
							{
								temp = new BTreeNode(temp.children[0]);
							}
							newKey = temp.keys[0];
							
							temp = new BTreeNode(node.children[i-1]);
							insertVal(child, newKey, temp.children[temp.count]);
							writeNode(childAddr, child);
							
							//MAKE NEW KEY AGAIN
							
							temp = new BTreeNode(node.children[i]);
							while (!temp.isLeaf)
							{
								temp = new BTreeNode(temp.children[0]);
							}
							newKey = temp.keys[0];
							temp = new BTreeNode(node.children[i-1]);
							node.keys[i-1] = newKey;
							removeVal(temp, temp.keys[temp.count-1], temp.children[temp.count], true);
							writeNode(node.children[i-1], temp);
						}
						break;
					}
				}
				
				if (i != node.count)
				{
					temp = new BTreeNode(node.children[i+1]);
					if (Math.abs(temp.count) > minKeys)
					{
						if(temp.isLeaf)
						{
							insertVal(child, temp.keys[0], temp.children[0]);
							removeVal(temp, temp.keys[0], temp.children[0], false);
							node.keys[i] = temp.keys[0];
							writeNode(node.children[i+1], temp);
						}
						else
						{
							insertVal(child, node.keys[i], temp.children[0]);
							removeVal(temp, temp.keys[0], temp.children[0], true);
							//node.keys[i] = temp.keys[0];
							writeNode(node.children[i+1], temp);
						}
						break;
					}
				}
			}
		}	
		
	}
	/**
	 * Checks neighbors for potential borrowing.
	 * @param node
	 * @param childAddr
	 * @return true if there is a suitable neighbor for borrowing, else return false
	 */
	private boolean checkNeighbors(BTreeNode node, long childAddr) 
	{
		//PRE: node is a non-Leaf.
		BTreeNode temp;
		for (int i = 0; i < node.count + 1; i++)
		{
			if (node.children[i] == childAddr)
			{
				if (i != 0)
				{
					temp = new BTreeNode(node.children[i-1]);
					if (Math.abs(temp.count) > minKeys)
					{
						return true;
					}
				}
				if (i != node.count)
				{
					temp = new BTreeNode(node.children[i+1]);
					if (Math.abs(temp.count) > minKeys)
					{
						return true;
					}
				}
				break;
			}
		}
		return false;
	}
	/**
	 * Removes a value from the node in sorted order. If flag is set true and non-leaf, the 0th child is removed
	 * @param node
	 * @param key
	 * @param nodeAddr
	 * @param flag
	 * @return Whether or not the node in question is now too small from the remove
	 */
	private boolean removeVal(BTreeNode node, int key, long nodeAddr, boolean flag) 
	{
		boolean tooSmall = false;
		if (nodeAddr == root)
		{
			if(Math.abs(node.count) == 1)
			{
				tooSmall = true;
			}
		}
		else if (Math.abs(node.count) <= minKeys)
		{
			tooSmall = true;
		}
		for(int i = 0; i < Math.abs(node.count); i++)
		{
			if(node.keys[i] == key)
			{
				if (node.isLeaf)
				{
					for (int j = i; j < Math.abs(node.count); j++)
					{
						if (j+1 == Math.abs(node.count))
						{
							node.keys[j] = 0;
							node.children[j] = 0;
						}
						else
						{
						node.keys[j] = node.keys[j+1];
						node.children[j] = node.children[j+1];
						}
					}
					node.count++;
				}
				else
				{
					for (int j = i; j < Math.abs(node.count); j++)
					{
						if (j + 1 == Math.abs(node.count))
						{
							node.keys[j] = 0;
							node.children[j+1] = 0;
						}
						else
						{
							if (j == 0 && flag)
							{
								node.children[j] = node.children[j+1];
							}
							node.keys[j] = node.keys[j+1];
							node.children[j+1] = node.children[j+2];
						}
					}
					node.count--;
				}
			}
		}
		return tooSmall;
	}

	/**
	 * Gets the first free memory address where content can be inserted
	 * @return The first free memory address
	 * @throws IOException
	 */
	private long getFree()
	{
		long addr = 0;
		try
		{
			BTreeNode temp;
			if (free == 0)
			{
				addr = f.length();
			}
			else
			{
				addr = free;
				temp = new BTreeNode(free);
				free = temp.nextFree;
			}
		} catch (IOException e) {}
		
		return addr;
	}
	/**
	 * Adds shit to the free list
	 * @param addr
	 * @param newFree
	 */
	private void addFree(long addr, BTreeNode newFree)
	{
		newFree.setToFree();
		newFree.nextFree = free;
		free = addr;
		writeNode(free, newFree);
	}
	/**
	 * Updates the BTree file and closes the file.
	 */
	public void close() 
	{
		try
		{
			f.seek(0);
			f.writeLong(root);
			f.writeLong(free);
			f.writeInt(blockSize);
			f.close();
		}
		catch (IOException e)
		{
			
		}
	}
	private class BTreeNode 
	{
		private int count;	//Number of keys inserted in to the node	
		private int keys[];
		private long children[];
		private boolean isLeaf;
		private boolean isFree;
		private long nextFree;

		//constructors and other method
		public BTreeNode(int c, int[] k, long[] child)
		{
			count = c;
			keys = k;
			isFree = false;
			nextFree = 0;
			children = child;
			if (c <= 0)
				isLeaf = true;
			else
				isLeaf = false;
		}

		public BTreeNode(long addr) 
		{
			isFree = false; //Implement reusing nodes from getFree().
			nextFree = 0;
			try
			{
				f.seek(addr);
				count = f.readInt();
				keys = new int[order -1];
				children = new long[order];
				if (count != 0)
				{
					if (count <= 0)
						isLeaf = true;
					else
						isLeaf = false; 
					for (int i = 0; i < keys.length; i++)
					{
						if(i < Math.abs(count))
							keys[i] = f.readInt();
						else
						{
							keys[i] = 0;
							f.skipBytes(4);
						}
					}
					for (int j = 0; j < children.length; j++)
					{
						if (j < Math.abs(count) || (j == Math.abs(count) && !isLeaf))
							children[j] = f.readLong();
						else
						{
							children[j] = 0;
							f.skipBytes(8);
						}	
					}
				}
				else //BTreeNode is free
				{
					isFree = true;
					nextFree = f.readLong();
					for (int i = 0; i < keys.length; i++)
					{
						keys[i] = 0;
					}
					for (int j = 0; j < children.length; j++)
					{
						children[j] = 0;
					}
				}
			}
			catch(IOException e)
			{
				
			}
		}
		
		/**
		 * @return the BTreeNode in text.
		 */
		public String toString()
		{
			StringBuilder sb = new StringBuilder();
			sb.append("Count: " + count);
			if (!isFree)
			{
				sb.append(" Keys: ");
				for (int k : keys)
				{
					sb.append(k + " ");
				}
				sb.append("Addrs: ");
				for (long j : children)
				{
					sb.append(j + " ");
				}
				sb.append(isLeaf);
			}
			else
				sb.append(" Next Free: " + nextFree);
			
			return sb.toString();
		}
		private void setToFree()
		{
			count = 0;
			isFree = true;
			for (int i = 0; i < keys.length; i++)
			{
				keys[i] = 0;
			}
			for (int j = 0; j < children.length; j++)
			{
				children[j] = 0;
			}
		}
	}
}