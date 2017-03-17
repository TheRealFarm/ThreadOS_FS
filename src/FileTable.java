import java.security.KeyStore.Entry;
import java.util.Vector;

public class FileTable {
	private Vector<FileTableEntry> table;
	private Directory dir;
	private final static short UNUSED = 0;
	private final static short USED = 1;
	private final static short READ = 2;
	private final static short WRITE = 3;
	
	public FileTable(Directory directory) {
		table = new Vector<FileTableEntry>();
		dir = directory;
	}
	
	// allocate a new file table entry for the file name
	// allocate/retrieve and register the corresponding inode using dir
	// increment the inode's count
	// immediately write back this inode to disk
	// return a references to this file table entry
	public synchronized FileTableEntry falloc(String filename, String mode) {
		Inode inode;
		short iNumber = filename.equals("/") ? 0 : dir.namei(filename);
		
		if (iNumber >= 0) {
			while (true) {
				inode = new Inode(iNumber);
				if (mode.equals("r")) { // requesting read
					if (inode.flag == UNUSED || inode.flag == READ) // file is able to be read
					{
						inode.flag = READ; // set flag to read and break
						break;
				
					}
					else { // file is in use or being written 
						try {
							wait();
						} catch (InterruptedException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
				}
				else { // requesting write/append
					if (inode.flag == UNUSED || inode.flag == USED) // file can be written
					{
						inode.flag = WRITE;
						break;
					}
					else if (inode.flag == READ || inode.flag == WRITE) // file is being read/written so wait
					{
						try {
							wait();
						} catch (InterruptedException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
					}
				}
			}
		}
		else {
			if (mode.equals("r")) // cannot read from nonexistent file
				return null;
			// write operation
			inode = new Inode();
			iNumber = dir.ialloc(filename);
			inode.flag = WRITE;
			if (iNumber < 0) // out of file space
				return null;
		}
		
		inode.count++;
		inode.toDisk(iNumber);
		FileTableEntry ftEnt = new FileTableEntry(inode, iNumber, mode);
		table.add(ftEnt);
		return ftEnt;
	}
	
	// receive a file table entry reference
	// save the corresponding inode to disk
	// free this file table entry
	// return true if this file table entry found in the table
	public synchronized boolean ffree(FileTableEntry e) {
		if (table.remove(e)) 
		{
			e.inode.count--;
			if (e.inode.flag == READ || e.inode.flag == WRITE)
			{
				e.inode.flag = UNUSED;
			}
			e.inode.toDisk(e.iNumber);
			e = null;
			notify();
			return true;
		}
		return false;
	}
	
	public synchronized boolean fempty() {
		return table.isEmpty();
	}
}
