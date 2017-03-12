 public class Directory {
	private static int maxChars = 30; // max chars for each file name
	
	private int fsize[]; // each element stores a different file size
	private char fnames[][]; // each element stores a different file name
	
	
	public Directory(int maxInumber) {
		fsize = new int[maxInumber]; // maxInumber = max files
		for (int i = 0; i < maxInumber; i++)
			fsize[i] = 0;
		fnames = new char[maxInumber][maxChars];
		String root = "/";
		fsize[0] = root.length();
		root.getChars(0, fsize[0], fnames[0], 0);
	}
	
	// assumes data[] received directory information from disk
	// initializes the Directory with this data[]
	public int bytes2directory(byte data[]) {
		int fsizeOffset = 0;
		int fnamesOffset = fsize.length * 4;
		int maxCharBytes = maxChars * 2;
		for (int i = 0; i < fsize.length; i++)
		{
			fsize [i] = SysLib.bytes2int(data, fsizeOffset);
			String str = new String(data, fnamesOffset, maxCharBytes);
			str.getChars(0, fsize[i], fnames[i], 0);
			fsizeOffset += 4;
			fnamesOffset += maxCharBytes;
		}
		
		return 0;
	}
	
	// converts and return Directory information into a plain byte array
	// this byte array will be written back to disk
	// only meaningful directory information is converted into bytes
	public byte[] directory2bytes() {
		int maxCharBytes = maxChars * 2;
		int fsizeBytes = 4 * fsize.length;
		int fsizeOffset = 0;
		int fnamesOffset = fsizeBytes;
		byte data[] = new byte[fsizeBytes + fnames.length*maxCharBytes];

		for (int i = 0; i < fsize.length; i++)
		{
			SysLib.int2bytes(fsize[i], data, fsizeOffset);
			System.arraycopy(fnames[i], 0, data, fnamesOffset, fnames[i].length);
			fsizeOffset += 4;
			fnamesOffset += maxCharBytes;
		}
		
		return data;
	}
	
	// filename is the name of a file to be created.
	// allocates a new inode number for this filname
	public short ialloc(String filename) {
		for (short i = 0; i < fsize.length; i++)
		{
			if (fsize[i] == 0)
			{
				fsize[i] = filename.length();
				filename.getChars(0, fsize[i], fnames[i], 0);
				return i;
			}
		}
		return (short)-1;
	}
	
	// deallocates this inumber
	// corresponding file will be deleted
	public boolean ifree(short iNumber) {
		// iNumber cant be 0 or less, greater than maxInodes, and the file size cant be 0 already
		if (iNumber <= 0 || iNumber > fsize.length || fsize[iNumber] == 0)
			return false;
		
		fsize[iNumber] = 0;
		return true;
		
	}
	
	// returns the inumber corresponding to this filename
	public short namei(String filename) {
		for (short i = 0; i < fnames.length; i++) 
		{
			if (fnames[i].length > 0)
			{
				String file = new String(fnames[i]);
				if (file.equals(filename))
					return i;
			}
		}
		return (short)-1;
	}
}
