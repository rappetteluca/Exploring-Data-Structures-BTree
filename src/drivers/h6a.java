import java.io.*;
import java.util.*;

public class h6a {

		DBTable t1;  //stores keys with a first name and last name

		int t1Fields[] = {15, 30};

	private void insert_t1(String filename) throws IOException {
		System.out.println("Inserts into t1");
		BufferedReader b = new BufferedReader(new FileReader(filename));
		String line;
		while ((line = b.readLine()) != null) {
			String fields[] = line.split(",");
			int key = new Integer(fields[0]);
			char f[][] = new char[2][];
			f[0] = Arrays.copyOf(fields[1].toCharArray(), 15);
			f[1] = Arrays.copyOf(fields[2].toCharArray(), 30);
			t1.insert(key, f);
		}
	}
	private void search(int val)  throws IOException {

		LinkedList<String> fields1;

		fields1 = t1.search(val);
		print(fields1, val);

	}

	private void rangeSearch (int low, int high) throws IOException {
		LinkedList<LinkedList<String>> list1 = t1.rangeSearch(low, high);
		while (list1.size() > 0) {
			LinkedList<String> list2 = list1.remove(); 
			while (list2.size() > 0) {
				System.out.print(list2.remove()+" ");
			}
			System.out.println();
		}

	}


	private void print(LinkedList<String> f, int k) {
		if (f.size() == 0) { 
			System.out.println("Not Found "+k);
			return;
		}
		System.out.print(""+k+" ");
		for (int i = 0; i < f.size(); i++)
			System.out.print(f.get(i)+" ");
		System.out.println();
	}
			
	public h6a() throws IOException {
		int limit;

		t1 = new DBTable("testdata/f1", t1Fields, 60);


		//Insert data into t1
		insert_t1("testdata/faculty2.txt");

		for (int i = 0; i < 24; i++) {
			search(i);
		}

		System.out.println("Range Search 0 to 24");
		rangeSearch(0, 24);

		//remove rows 2 and 22

		t1.remove(2);
		t1.remove(22);

		search(2);
		search(4);
		search(22);

		System.out.println("Range Search 0 to 24");
		rangeSearch(0, 24);

		t1.close();

		t1 = new DBTable("testdata/f1");
		//Reuse table and insert more data into t1
		insert_t1("testdata/faculty1.txt");

		for (int i = 0; i < 24; i++) {
			search(i);
		}

		System.out.println("Range Search 0 to 24");
		rangeSearch(0, 24);

		//remove all the odd rows
		for (int i = 1; i < 24; i = i+2) {
			t1.remove(i);
		}

		System.out.println("search for rows after removes");
		for (int i = 0; i < 24; i++) {
			search(i);
		}

		System.out.println("Range Search 15 to 30");
		rangeSearch(20, 30);

		t1.close();
	}



	public static void main(String args[])  throws IOException  {
		new h6a();
	}
}