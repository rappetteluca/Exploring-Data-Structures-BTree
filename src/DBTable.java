/**
 * @author Lucas Rappette
 */
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.LinkedList;

public class DBTable 
{

	private RandomAccessFile rows; //the file that stores the rows in the table
	private long free = 0; //head of the free list space for rows
	private int numOtherFields;
	private int otherFieldLengths[];
	private int rowSize = 4; //Default 4 bytes for keyField
	private BTree tree;
	private final char nonChar = '\0'; //padding characters
	private final char emptyChar = 253; //Not used.. yet

	public DBTable(String filename, int fL[], int bsize ) 
	{
		numOtherFields = fL.length;
		otherFieldLengths = new int[numOtherFields];
		try
		{
			File target = new File(filename + ".table");
			if(target.exists())
			{
				target.delete();
			}
			
			target.createNewFile();
			rows = new RandomAccessFile(target, "rw");
			tree = new BTree(filename, bsize);
			rows.seek(0);
			rows.writeInt(numOtherFields);
			for (int i = 0; i < fL.length; i++)
			{
				otherFieldLengths[i] = fL[i];
				rowSize += (fL[i] * 2);
				rows.writeInt(otherFieldLengths[i]);
			}
			rows.writeLong(free);
		}
		catch(IOException e)
		{
			
		}
	}
	
	public DBTable(String filename) 
	{
		File target = new File(filename + ".table");
		try
		{
			if (!target.exists())
			{
				throw new IOException();
			}
			rows = new RandomAccessFile(target, "rw");
			tree = new BTree(filename);
			rows.seek(0);
			numOtherFields = rows.readInt();
			otherFieldLengths = new int[numOtherFields];
			for (int i = 0; i < numOtherFields; i++)
			{
				otherFieldLengths[i] = rows.readInt();
				rowSize += (otherFieldLengths[i] * 2);
			}
			free = rows.readLong();
		}
		catch(IOException e)
		{
			//Do not initialize, DBTable File associated with calling driver DNE.
			System.exit(1);
		}
	}
	/**
	 * Inserts a key and data in to the DBTable and BTree if the key is not a duplicate
	 * @return true - if key is not a duplicate
	 * @return false - if key is a duplicate.
	 */
	public boolean insert(int key, char fields[][]) 
	{
		//PRE: the length of each row in fields matches the expected length
		long addr;
		try 
		{
			addr = getFree();
			if(tree.insert(key, addr) == true) //Returns true when key is not a duplicate
			{	
				Row r = new Row(key, fields);
				writeRow(addr, r); //Write node to DBTable and return true
				return true;
			}
		}
		catch(IOException e)
		{
				
		}
		return false; //Key was a duplicate so row was not added.

	}
	/**
	 * Searches BTree for the DBTable address and returns a list of the DBTable Row
	 * @param key the key to search for in the B Tree/DBTable
	 * @return a list of elements from the Row associated with key
	 */
	public LinkedList<String> search(int key)
	{
		LinkedList<String> list = new LinkedList<String>();
		Long addr = tree.search(key);
		if (addr != 0) //Key is found
		{
			for (int i = 0; i < numOtherFields; i++)
			{
				list = DBSearch(addr);
			}
		}
		return list;
	}
	/** 
	 * Searches only the DBTable and returns a list of the data associated with the Row
	 * @param addr The address of the row to load
	 * @return a list of elements from the row associated with the address
	 */
	public LinkedList<String> DBSearch(long addr)
	{
		LinkedList<String> list = new LinkedList<String>();
		Row r = new Row(addr);
		for (int i = 0; i < numOtherFields; i++)
		{
			StringBuilder sb = new StringBuilder();
			for (char c: r.otherFields[i])
			{
				sb.append(c);
			}
			String data = sb.toString();
			data = data.replace(nonChar, ' '); 
			list.add(data);
		}
		return list;
	}
	
	/**
	 * Writes a Row class' data to the DBTable file
	 * @param addr the addr to start writing at
	 * @param r Row class to write
	 */
	private void writeRow(long addr, Row r)
	{
		try 
		{
			if (r.isFree) //Writing a free row, not implemented yet
			{
				rows.seek(addr);
				rows.writeLong(r.nextFree);
				rows.writeChar(emptyChar);
				for (int i = 10; i < rowSize; i++)
				{
					
					if (i + 1 == rowSize) //Needed..? for odd numbered row size
					{
						rows.writeByte(127);
					}
					
					rows.writeChar(nonChar);
					i++;
					//Char writes 2 bytes
				}
				
			}
			else //Writing a full row
			{
				rows.seek(addr);
				rows.writeInt(r.keyField);
				for (int i = 0; i < numOtherFields; i++)
				{
					for(int j = 0; j < r.otherFields[i].length; j++)
					{
						rows.writeChar(r.otherFields[i][j]);
					}
				}
			}
		}
		catch (IOException e) 
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	/**
	 * Gets the first free memory address where content can be inserted
	 * @return The first free memory address
	 * @throws IOException
	 */
	private long getFree() throws IOException
	{
			long addr = 0;
			Row temp;
			if (free == 0)
			{
				addr = rows.length();
			}
			else
			{
				addr = free;
				temp = new Row(free);
				free = temp.nextFree; //Next Address in Free List
			}
			return addr;
	}
	/** Adds the memory address to the free list and updates the free list.
	 * 
	 * @param newFree The address of the empty node to be added to the free list
	 * @param temp The node of to be added to the free list
	 * @throws IOException
	 */
	private void addFree(long newFree)
	{
		try
		{
			Row temp = new Row(newFree);
			temp.setToFree();
			temp.nextFree = free; //new Node next Free is the old first free node
			writeRow(newFree, temp);
			free = newFree;
		}
		catch(Exception e)
		{
			
		}
		
	}
	/**
	 * Attempts to remove key from the BTree, if the data in BTree is removed then the data in DBTable is removed
	 * @param key
	 * @return Whether or not the specificed key was successfully removed
	 */
	public boolean remove(int key) 
	{
		Long addr = tree.remove(key);
		if (addr != 0) //Key is found
		{
			addFree(addr);
			return true;
		}
		return false; //Key not found
	}
	/**
	 * Find Row data who's keys are within the range of low to high inclusive.
	 * @param low - The Lowest key val to search for.
	 * @param high - The highest key val to search for.
	 * @return A list of Row Data for each row who's key is within the range of low to high.
	 */
	public LinkedList<LinkedList<String>> rangeSearch(int low, int high) 
	{
		LinkedList<Long> addrs = tree.rangeSearch(low, high);
		LinkedList<LinkedList<String>> output = new LinkedList<LinkedList<String>>();
		for (long l: addrs)
		{
			output.add(DBSearch(l));
		}
		return output; 
	}
	
	/**
	 * Updates the DBTable and closes the B Tree.
	 */
	public void close() 
	{
		try 
		{
			rows.seek(0);
			rows.writeInt(numOtherFields);
			for (int i = 0; i < numOtherFields; i ++)
			{
				rows.writeInt(otherFieldLengths[i]);
			}
			rows.writeLong(free);
			rows.close();
			tree.close();
		} 
		catch (IOException e) 
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private class Row 
	{
		private int keyField;
		private char otherFields[][];
		private long nextFree;
		private boolean isFree;
		
		//Constructors and other Row methods
		public Row(int k, char[][] otf)
		{
			keyField = k;
			otherFields = otf;
			isFree = false;
			nextFree = 0;
		}
		/**
		 * Reads a Row from the DBTable file.
		 * @param addr - the address of the row
		 */
		public Row(long addr)
		{
			try 
			{
				rows.seek(addr);
				rows.skipBytes(8);
				if (rows.readChar() != emptyChar) //Row is not Empty
				{
					rows.seek(addr);
					nextFree = 0;
					isFree = false;
					keyField = rows.readInt();
					otherFields = new char[numOtherFields][];
					for (int i = 0; i < numOtherFields; i++)
					{
						otherFields[i] = new char[otherFieldLengths[i]];
						for(int j = 0; j < otherFieldLengths[i]; j++)
						{
							otherFields[i][j] = rows.readChar();
						}
					}
				}
				else //Row is Empty
				{
					rows.seek(addr);
					nextFree = rows.readLong();
					isFree = true;
					keyField = 0;
					otherFields = null;
				}
			}
			catch (IOException e) 
			{
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		private void setToFree()
		{
			keyField = 0;
			isFree = true;
		}

	}
}