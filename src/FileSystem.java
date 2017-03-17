public class FileSystem {
	private SuperBlock superblock;
	private Directory directory;
	private FileTable filetable;
	private final int SEEK_SET = 0;
	private final int SEEK_CUR = 1;
	private final int SEEK_END = 2;
	
	public FileSystem(int diskBlocks) {
		superblock = new SuperBlock(diskBlocks);
		
		directory = new Directory(superblock.totalInodes);
		
		filetable = new FileTable (directory);
		
		FileTableEntry dirEnt = open("/", "r");
		int dirSize = fsize(dirEnt);
		if (dirSize > 0) {
			byte[] dirData = new byte[dirSize];
			read(dirEnt, dirData);
			directory.bytes2directory(dirData);
		}
		close(dirEnt);
	}
	
	void sync() {
		FileTableEntry rootftEnt = open("/", "w");
		byte[]rootftData = directory.directory2bytes();
		write(rootftEnt, rootftData);
		close(rootftEnt);
		
		superblock.sync();
	}
	
	// reformat the file system with the given amount of files
	// The parameter files specifies the maximum number of files to be created 
	// (the number of inodes to be allocated) in the file system. 
	// The return value is true on success, false otherwise
	boolean format(int files) {
		if (!superblock.format(files))
			return false;
		
		directory = new Directory(superblock.totalInodes);
		
		filetable = new FileTable(directory);
		
		return true;
	}
	
	// Gets a pointer to the file table entry from the file table given the filename string and mode
	// All file operations are performed via this pointer
	FileTableEntry open(String filename, String mode) {
		FileTableEntry ftEnt = filetable.falloc(filename, mode);
		
		// if open requests write, ensure blocks are unallocated
		if (mode.equals("w"))
			if(!deallocAllBlocks(ftEnt))
				return null;
		
		return ftEnt;
	}
	
	// Remove the table entry in the per-process table and 
	// decrement the system-wide entry's open count
	boolean close(FileTableEntry ftEnt) {
		synchronized(ftEnt) {
			ftEnt.count--;
			if (ftEnt.count > 0) // file is still in use, return true
				return true;
		}
		return filetable.ffree(ftEnt); // return the result of freeing the entry
	}
	
	// returns the size of the file by returning the inode length
	int fsize(FileTableEntry ftEnt) {
		synchronized(ftEnt)
		{
			return ftEnt.inode.length;
		}
	}
	
	// writes the contents of the buffer into the file from the file entry.
	// location of the write starts from the seek ptr which returns the target block.
	// returns the number of bytes written from the buffer.
	int write(FileTableEntry ftEnt, byte[] buffer) {
		if (ftEnt.mode.equals("r"))
			return -1;
		
		synchronized(ftEnt) 
		{
			int bytesWritten = 0;
			int writeLength = buffer.length;
			
			while(writeLength > 0)
			{
				//SysLib.cout("Seek ptr for Inode:" + ftEnt.iNumber + " = " + ftEnt.seekPtr + "\n");
				int targetBlock = ftEnt.inode.findTargetBlock(ftEnt.seekPtr);
				//SysLib.cout("Target block " + targetBlock + "\n");
				if (targetBlock == -1) // target block has not been set, find a free block to write to
				{
					int freeBlock = superblock.getFreeBlock();
					int retCode = ftEnt.inode.setTargetBlock(ftEnt.seekPtr, (short)freeBlock);
					if (retCode == -1 || retCode == -2) // block has been set or the previous block in the inode is unused, error
					{
						SysLib.cerr("Error on write: block has been set already/previous block in inode unused\n");
						return -1;
					}
					else if (retCode == -3) // indirect pointer is null -> set the index block
					{
						short newLocation = (short)superblock.getFreeBlock(); // get block for indirect
						if (!ftEnt.inode.setIndexBlock(newLocation))
						{
							SysLib.cerr("Error on write: set index block\n");
							return -1;
						}
						if (ftEnt.inode.setTargetBlock(ftEnt.seekPtr, (short)freeBlock) != 0)
						{
							SysLib.cerr("Error on write: set target block\n");
							return -1;
						}
					}
					targetBlock = freeBlock;
				}
				byte[] data = new byte[Disk.blockSize];
				SysLib.rawread(targetBlock, data);
				//SysLib.cout("Target block for writing: " + targetBlock + "\n");
				int dataOffset = ftEnt.seekPtr % Disk.blockSize;
				//SysLib.cout("Data offset = " + dataOffset + "\n");
				int bytesLeftInBlock = Disk.blockSize - dataOffset; // difference of bytes left in block from the offset
				// take min of total writelength and bytesleftinblock so we dont write over into the next block
				int dataWriteLength = Math.min(bytesLeftInBlock, writeLength); 
				
				System.arraycopy(buffer, bytesWritten, data, dataOffset, dataWriteLength);
				SysLib.rawwrite(targetBlock, data);
				
				ftEnt.seekPtr += dataWriteLength;
				bytesWritten += dataWriteLength;
				writeLength -= dataWriteLength;
				
				// update length of inode as we write to it
				if(ftEnt.seekPtr > ftEnt.inode.length)
				{
					ftEnt.inode.length = ftEnt.seekPtr;
				}
			}
			ftEnt.inode.toDisk(ftEnt.iNumber);
			
			return bytesWritten;
		}
	}
	
	// reads a file into the buffer given the file table entry
	// returns the number of bytes read
	int read(FileTableEntry ftEnt, byte[] buffer) {
		if(ftEnt.mode.equals("w") || ftEnt.mode.equals("a"))
			return -1;
		int readLength = buffer.length;
		int bytesRead = 0;
		
		synchronized(ftEnt)
		{
			while (readLength > 0 && ftEnt.seekPtr < fsize(ftEnt))
			{
				//SysLib.cout("Seek ptr for Inode:" + ftEnt.iNumber + " = " + ftEnt.seekPtr + "\n");
				int targetBlock = ftEnt.inode.findTargetBlock(ftEnt.seekPtr);
				if (targetBlock == -1) // target block has not been set, cannot read
					break;
				
				byte[] data = new byte[Disk.blockSize];
				SysLib.rawread(targetBlock, data);
				
				int dataOffset = ftEnt.seekPtr % Disk.blockSize;
				//SysLib.cout("Data offset:" + dataOffset);
				
				int bytesLeftInBlock = Disk.blockSize - dataOffset; // difference of bytes left in block from the offset
				//SysLib.cout("\nbytesLeftInBlock:" + bytesLeftInBlock);
				int bytesLeftInFile = fsize(ftEnt) - ftEnt.seekPtr; // size of file - where the seek ptr is
				//SysLib.cout("\n(fileLeft):" + bytesLeftInFile);
				int dataReadLength = Math.min(Math.min(bytesLeftInBlock, readLength), bytesLeftInFile);
				System.arraycopy(data, dataOffset, buffer, bytesRead, dataReadLength);
				
				ftEnt.seekPtr += dataReadLength;
				bytesRead += dataReadLength;
				readLength -= dataReadLength;
			}
			return bytesRead;
		}
	}
	
	// given the file table entry, deallocates all of the blocks associated with that entry
	private boolean deallocAllBlocks(FileTableEntry ftEnt) {
		if (ftEnt.inode.count != 1)
			return false;
		// free data from index block
		byte[] indirectData = ftEnt.inode.freeIndexBlock();
		if (indirectData != null)
		{
			int indexBlock;
			for (int i = 0; i < indirectData.length; i+=2)
			{
				indexBlock = SysLib.bytes2short(indirectData, i);
				if (indexBlock == -1)
					break;
				superblock.freeBlock(indexBlock);
			}
		}
		// free direct pointer blocks
		for (int i = 0; i < 11; i++)
		{
			if (ftEnt.inode.direct[i] != -1)
			{
				superblock.freeBlock(ftEnt.inode.direct[i]);
				ftEnt.inode.direct[i] = -1;
			}
		}
		ftEnt.inode.toDisk(ftEnt.iNumber);
		return true;
	}
	
	// get the file table entry to find the file to be deleted
	// get the corresponding inode
	// close the file table entry and free from directory
	boolean delete(String filename) {
		FileTableEntry ftEnt = open(filename, "w");
		short iNumber = ftEnt.iNumber;
		return (close(ftEnt) && directory.ifree(iNumber));
	}
	
	// sets the seek pointer for a file entry given the offset and the whence for the seek
	// returns the seek ptr on a successful set of the seek ptr, otherwise returns -1
	int seek(FileTableEntry ftEnt, int offset, int whence) {
		synchronized(ftEnt) {
			switch(whence)
			{
			case SEEK_SET: //start at beginning and move to the offset, must be positive
				if(offset >= 0 && offset <= fsize(ftEnt))
				{
					ftEnt.seekPtr = offset;
				}
				else
					return -1;
				break;
			case SEEK_CUR: // start at current and move on offset
				if(ftEnt.seekPtr + offset >= 0 && ftEnt.seekPtr + offset <= fsize(ftEnt))
				{
					ftEnt.seekPtr += offset;
				}
				else
					return -1;
				break;
			case SEEK_END: // start at end and move on offset
				if (fsize(ftEnt) + offset >= 0 && fsize(ftEnt) + offset <= fsize(ftEnt))
				{
					ftEnt.seekPtr = fsize(ftEnt) + offset;
				}
				else
					return -1;
				break;
			}
			return ftEnt.seekPtr;
		}
	}
}
