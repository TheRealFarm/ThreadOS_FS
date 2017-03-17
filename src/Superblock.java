import java.util.Arrays;

public class SuperBlock {
	public int totalBlocks;
	public int totalInodes;
	public int freeList;
	private int defaultInodes = 64;
	
	public SuperBlock(int diskSize) {
		byte[] superblock = new byte[Disk.blockSize];
		SysLib.rawread(0, superblock); // read in superblock
		totalBlocks = SysLib.bytes2int(superblock, 0); // convert first 4 bytes to int for totalBlock retrieval
		totalInodes = SysLib.bytes2int(superblock, 4);
		freeList = SysLib.bytes2int(superblock,  8);
		
		// if the superblock hasn't been formatted yet, format
		if (totalBlocks != diskSize || totalInodes == 0 || freeList < 2)
			format(defaultInodes);
	}
	
	public boolean format(int files) {
		if (files < 0)
		{
			SysLib.cerr("Error in formatting: invalid number of files");
			return false;
		}
		
		byte[] superblock = new byte[Disk.blockSize];
		totalBlocks = 1000;
		totalInodes = files;
		freeList = (files % 16) == 0 ? files / 16 + 1: files / 16 + 2;
		
		// init inodes
		for (int i = 0; i < totalInodes; i++)
		{
			Inode node = new Inode();
			node.flag = 0;
			node.toDisk((short)i);
		}
		// clear all blocks
		for (int i = freeList; i < totalBlocks; i++)
		{
			byte[] block = new byte[Disk.blockSize];
			//int sum = 0;
			//for (byte b:block) {
				//sum |= b;
			//}
			//if (sum != 0)
				//SysLib.cout("block not 0!");
			
			SysLib.int2bytes(i+1, block, 0); // set the block number to the first byte for freeList
			SysLib.rawwrite(i, block);
		}
		
		SysLib.int2bytes(totalBlocks, superblock, 0);
		SysLib.int2bytes(totalInodes, superblock, 4);
		SysLib.int2bytes(freeList, superblock, 8);
		
		SysLib.rawwrite(0, superblock);
		SysLib.cerr("Superblock formatted for " + files + " files\n");
		return true;
	}
	
	void sync() {
		byte[] superblock = new byte[Disk.blockSize];
		SysLib.int2bytes(totalBlocks, superblock, 0);
		SysLib.int2bytes(totalInodes, superblock, 4);
		SysLib.int2bytes(freeList, superblock, 8);
		SysLib.rawwrite(0, superblock);
		SysLib.cout("Superblock syncrhonized\n");
	}
	
	// frees a block by zeroing it and writing back to disk
	// free list pointer is set to the block number
	public boolean freeBlock(int blockNumber) {
		if (blockNumber >= 0 && blockNumber < totalBlocks)
		{
			byte[] block = new byte[Disk.blockSize];
			// zero out block
			for (int i = 0; i < block.length; i++)
				block[i] = 0;
			SysLib.int2bytes(freeList, block, 0); // read freeList pointer into first offset of block
			SysLib.rawwrite(blockNumber, block); // write the block number
			freeList = blockNumber; // set the freelist
			return true;
		}
		return false;
	}
	
	// finds a free block from the freeList pointer and returns it if valid, increments freeList to next free block
	public int getFreeBlock() {
		int freeBlock = freeList;
		if (freeBlock != -1 && freeBlock < totalBlocks) // freeList pointer is valid
		{
			byte[] data = new byte[Disk.blockSize];
			SysLib.rawread(freeBlock, data); // read freeBlock from list into array
			
			freeList = SysLib.bytes2int(data, 0); // set freeList to next free block
			SysLib.int2bytes(0, data, 0);
			SysLib.rawwrite(freeBlock, data);
		}
		return freeBlock;
	}
	

}
