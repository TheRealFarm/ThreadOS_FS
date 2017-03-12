public class Superblock {
	public int totalBlocks;
	public int totalInodes;
	public int freeList;
	private int numInodes = 64;
	
	public Superblock(int diskSize) {
		byte[] superblock = new byte[Disk.blockSize];
		SysLib.rawread(0, superblock); // read in superblock
		totalBlocks = SysLib.bytes2int(superblock, 0); // convert first 4 bytes to int for totalBlock retrieval
		totalInodes = SysLib.bytes2int(superblock, 4);
		freeList = SysLib.bytes2int(superblock,  8);
		
		// if the superblock hasn't been formatted yet, format
		if (totalBlocks != 1000 || totalInodes == 0 || freeList < 3)
			format(numInodes);
	}
	
	public void format(int files) {
		if (files < 0)
			files = numInodes;
		
		byte[] superblock = new byte[Disk.blockSize];
		totalBlocks = 1000;
		totalInodes = files;
		freeList = (files % 16) == 0 ? (files/16) + 1 : (files/16) + 2;
		
		SysLib.int2bytes(totalBlocks, superblock, 0);
		SysLib.int2bytes(totalInodes, superblock, 4);
		SysLib.int2bytes(freeList, superblock, 8);
		
		SysLib.rawwrite(0, superblock);
		
	}

}
