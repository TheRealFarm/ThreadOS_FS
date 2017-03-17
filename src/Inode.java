
public class Inode {
	private final static int iNodeSize = 32;  // each iNode is 32 bytes
	private final static int directSize = 11; // num direct pointers

	public int length;								// size of file in bytes
	public short count;								// number of file table entries pointing to this inode
	public short flag;								// flag if inode in use, 0 = unused, 1 = used
	public short direct[] = new short[directSize];  // array of pointers to blocks of data
	public short indirect;    						// indirect pointer
	
	
	Inode () {
		length = 0;
		count = 0;
		flag = 1;
		for (int i = 0; i < directSize; i++) {
			direct[i] = -1;
		}
		indirect = -1;
	}
	
	Inode (short iNumber ) { // retrieves existing inode from disk
		byte[] inodeBlock = new byte[Disk.blockSize];
		int iNodeBlockNum = (iNumber / 16) + 1; // divide by 16 and add 1 to account for superblock
		SysLib.rawread(iNodeBlockNum, inodeBlock); 
		
		int offset = (iNumber % 16) * iNodeSize;
		length = SysLib.bytes2int(inodeBlock, offset);
		offset += 4;
		count = SysLib.bytes2short(inodeBlock, offset);
		offset += 2;
		flag = SysLib.bytes2short(inodeBlock, offset);
		offset += 2;
		
		for (int i = 0; i < directSize; i++)
		{
			direct[i] = SysLib.bytes2short(inodeBlock, offset);
			offset += 2;
		}
		indirect = SysLib.bytes2short(inodeBlock, offset);
	}
	
	// writes an inode back to the disk given the inode number
	int toDisk(short iNumber) {
		byte[] inodeData = new byte[iNodeSize];
		
		int iNodeBlockNum = iNumber / 16 + 1;
		//SysLib.rawread(iNodeBlockNum, inodeBlock);
		
		int offset = 0;
		
		SysLib.int2bytes(length, inodeData, offset);
		offset += 4;
		SysLib.short2bytes(count, inodeData, offset);
		offset += 2;
		SysLib.short2bytes(flag, inodeData, offset);
		offset += 2;
		
		for (int i = 0; i < directSize; i++)
		{
			SysLib.short2bytes(direct[i], inodeData, offset);
			offset += 2;
		}
		SysLib.short2bytes(indirect, inodeData, offset);
		offset += 2;
		
		byte[] inodeBlock = new byte[Disk.blockSize];
		SysLib.rawread(iNodeBlockNum, inodeBlock);
		offset = (iNumber % 16) * iNodeSize;
		System.arraycopy(inodeData, 0, inodeBlock, offset, iNodeSize);
		
		return SysLib.rawwrite(iNodeBlockNum, inodeBlock);
	}
	
	// return pointer to indirect block
	int findIndexBlock() {
		return indirect;
	}
	
	// sets an index block from the block number with all indices starting at -1.
	// if all of the direct pointers are not in use or the indirect pointer is in use already then false is returned
	// otherwise the index block is written to the disk and true returned
	boolean setIndexBlock(short blockNumber) {
		// check if direct pointers are all used
		for (int i = 0; i < 11; i++)
		{
			if (direct[i] == -1)
				return false;
		}
		if (indirect != -1) // indirect pointer cant be in use
		{
			return false;
		}
		indirect = blockNumber;
		byte[] indexBlock = new byte[Disk.blockSize]; 
		for(int i = 0; i < indexBlock.length; i+=2)
		{
			SysLib.short2bytes((short)-1, indexBlock, i); // set block pointers to -1
		}
		SysLib.rawwrite(blockNumber, indexBlock);
		return true;
	}
	
	// finds the target block given the offset into the file
	// if the target block is not within the scope of the direct pointers, the indirection
	// block is read to find the index which points to the block
	int findTargetBlock(int offset) {
		//SysLib.cerr("Finding target block..\n");
		//SysLib.cerr("offset = " + offset + "\n");
		int i = offset / Disk.blockSize;
		if (i < 11)
		{
			//SysLib.cerr("direct[i] = " + direct[i] + "\n");
			return direct[i];
		}
		else if (indirect == -1)
			return -1;
		
		byte[] blockData = new byte[Disk.blockSize];
		SysLib.rawread(indirect, blockData);
		int j = i - directSize;
		//SysLib.cerr("Found: " + j);
		return SysLib.bytes2short(blockData, j * 2);
		
	}
	
	// gets the offset into the file and the blockNumber to be pointed to
	// if the offset is beyond the scope of the direct pointers, then the indirect pointer is used
	// returns 0 on successfully writing one of the pointers to the block number passed in
	// if the there is an error in the direct pointer array, -1 or -2 is returned depending on the error
	// if the indirect pointer is null, -3 is returned to tell the file system to set the index block
	int setTargetBlock(int offset, short blockNumber) {
		int i = offset / Disk.blockSize;
		if (i < 11)
		{
			if (direct[i] >= 0) // block has been set already
				return -1;
			if ((i > 0) && direct[i-1] == -1) // previous block in direct pointers is unused
				return -2;
			direct[i] = blockNumber;
			return 0;
		}
		if (indirect == -1) // null indirect pointer
			return -3;
		
		byte[] indexBlock = new byte[Disk.blockSize];
		SysLib.rawread(indirect, indexBlock);
		int j = i - 11; // index in indirect block
		if (SysLib.bytes2short(indexBlock, j * 2) > 0) // index is in use
		{
			SysLib.cerr("indexBlock, indirectNumber = " + j + " contents = " + SysLib.bytes2short(indexBlock, j * 2) +"\n");
			return -1;
		}
		// put the index in the block with the block number
		SysLib.short2bytes(blockNumber, indexBlock, j * 2);
		SysLib.rawwrite(indirect, indexBlock);
		return 0;
	}
	
	// returns the index block to FileSystem.java for deallocation if the indirect pointer is in use
	byte[] freeIndexBlock() {
		if (indirect >= 0)
		{
			byte[] indexBlock = new byte[Disk.blockSize];
			SysLib.rawread(indirect, indexBlock);
			indirect = -1;
			return indexBlock;
		}
		return null;
	}
}
