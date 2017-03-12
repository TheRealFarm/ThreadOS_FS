import java.util.Vector;

public class FileTable {
	private Vector<FileTableEntry> table;
	private Directory dir;
	
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
		Inode inode = null;
		short iNumber = filename.equals("/") ? 0 : dir.namei(filename);
		if (iNumber >= 0) // inode exists
		{
			if (mode.equals("r")) { // if the file is requesting read
				
			}
			else { // requesting write
				
			}
		}
		else // create new inode
		{
			inode = new Inode();
		}
	}
	
	public synchronized boolean ffree(FileTableEntry e) {
		
	}
	
	private synchronized boolean fempty() {
		return table.isEmpty();
	}
}
