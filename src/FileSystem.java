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
	
	boolean format(int files) {
		if (!superblock.format(files))
			return false;
		
		directory = new Directory(superblock.totalInodes);
		
		filetable = new FileTable(directory);
		
		return true;
	}
	
	// Gets a pointer to the file table entry from the file table and returns it
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
	
	int fsize(FileTableEntry ftEnt) {
		synchronized(ftEnt)
		{
			return ftEnt.inode.length;
		}
	}
	
	int read(FileTableEntry ftEnt, byte[] buffer) {
		if(ftEnt.mode.equals("w") || ftEnt.mode.equals("a"))
			return -1;
		int readLength = buffer.length;
		int bytesRead = 0;
		
		synchronized(ftEnt)
		{
			while (readLength > 0 && ftEnt.seekPtr < fsize(ftEnt))
			{
				SysLib.cout("Seek ptr for Inode:" + ftEnt.iNumber + " = " + ftEnt.seekPtr);
				int currentBlock = ftEnt.inode.findTargetBlock(ftEnt.seekPtr);
				if (currentBlock == -1)
					break;
				
				byte[] data = new byte[Disk.blockSize];
				SysLib.rawread(currentBlock, data);
				
				int dataOffset = ftEnt.seekPtr % Disk.blockSize;
				SysLib.cout("\nData offset:" + dataOffset);
				
				int something = Disk.blockSize - dataOffset;
				SysLib.cout("\nSomething(blocksLeft?):" + something);
				int another = fsize(ftEnt) - ftEnt.seekPtr;
				SysLib.cout("\nAnother(fileLeft?):" + another);
				int dataReadLength = Math.min(Math.min(something, readLength), another);
				System.arraycopy(data, dataOffset, buffer, bytesRead, dataReadLength);
				
				ftEnt.seekPtr += dataReadLength;
				bytesRead += dataReadLength;
				readLength -= dataReadLength;
			}
			return bytesRead;
		}
	}
	
	// rewrite portions and understand
	int write(FileTableEntry ftEnt, byte[] buffer) {
		if (ftEnt.mode.equals("r"))
			return -1;
		
		synchronized(ftEnt) 
		{
			int bytesWritten = 0;
			int writeLength = buffer.length;
			
			while(writeLength > 0)
			{
				SysLib.cout("Inode = " + ftEnt.iNumber + "\n");
				int currentBlock = ftEnt.inode.findTargetBlock(ftEnt.seekPtr);
				SysLib.cout("Target(current) block? " + currentBlock + "\n");
				if (currentBlock == -1)
				{
					int freeBlock = superblock.getFreeBlock();
					int retCode = ftEnt.inode.setTargetBlock(ftEnt.seekPtr, (short)freeBlock);
					switch(retCode)
					{
						case 0:
							break;
							
						case -2:
						case -1:
							SysLib.cerr("ThreadOS: filesystem panic on write\n");
							break;
						
						case -3:
							short newLocation = (short)superblock.getFreeBlock();
							if (!ftEnt.inode.setIndexBlock(newLocation))
							{
								SysLib.cerr("ThreadOS: panic on write\n");
								return -1;
							}
							if (ftEnt.inode.setTargetBlock(ftEnt.seekPtr, (short)freeBlock) != 0)
							{
								SysLib.cerr("ThreadOS: panic on write\n");
								return -1;
							}
							break;
					}
					currentBlock = freeBlock;
				}
				byte[] data = new byte[Disk.blockSize];
				if(SysLib.rawread(currentBlock, data) == -1) {
					System.exit(2);
				}
				
				int dataOffset = ftEnt.seekPtr % Disk.blockSize;
				SysLib.cout("Data offset = " + dataOffset + "\n");
				int diff = 512 - dataOffset;
				int dataWriteLength = Math.min(diff, writeLength);
				
				System.arraycopy(buffer, bytesWritten, data, dataOffset, dataWriteLength);
				SysLib.rawwrite(currentBlock, data);
				
				ftEnt.seekPtr += dataWriteLength;
				bytesWritten += dataWriteLength;
				writeLength -= dataWriteLength;
				
				if(ftEnt.seekPtr > ftEnt.inode.length)
				{
					ftEnt.inode.length = ftEnt.seekPtr;
				}
			}
			ftEnt.inode.toDisk(ftEnt.iNumber);
			
			return bytesWritten;
		}
	}
	
	// rewrite and understand
	private boolean deallocAllBlocks(FileTableEntry ftEnt) {
		if (ftEnt.inode.count != 1)
			return false;
		
		byte[] delData = ftEnt.inode.freeIndexBlock();
		if (delData != null)
		{
			int i = 0;
			int j;
			while ((j = SysLib.bytes2short(delData,  i)) != -1)
				superblock.freeBlock(j);
		}
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
	
	// open the file to be deleted with write 
	// get the corresponding inode
	// close the file table entry and free from directory
	boolean delete(String filename) {
		FileTableEntry ftEnt = open(filename, "w");
		short iNumber = ftEnt.iNumber;
		return (close(ftEnt) && directory.ifree(iNumber));
	}
	
	int seek(FileTableEntry ftEnt, int offset, int whence) {
		return 0;
	}
}
