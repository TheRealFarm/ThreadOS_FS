import java.util.Vector;

public class FileTable {
	private Vector<FileTableEntry> table;
	private Directory dir;
	
	public FileTable(Directory directory) {
		table = new Vector<FileTableEntry>();
		dir = directory;
	}
	
	public synchronized FileTableEntry falloc(String filename, String mode) {
		
	}
	
	public synchronized boolean ffree(FileTableEntry e) {
		
	}
	
	private synchronized boolean fempty() {
		return table.isEmpty();
	}
}
