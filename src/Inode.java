
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
	
	int toDisk(short iNumber) {
		byte[] inodeBlock = new byte[Disk.blockSize];
		
		int iNodeBlockNum = iNumber / 16 + 1;
		//SysLib.rawread(iNodeBlockNum, inodeBlock);
		
		int offset = (iNumber % 16) * iNodeSize;
		
		SysLib.int2bytes(length, inodeBlock, offset);
		offset += 4;
		SysLib.short2bytes(count, inodeBlock, offset);
		offset += 2;
		SysLib.short2bytes(flag, inodeBlock, offset);
		offset += 2;
		
		for (int i = 0; i < directSize; i++)
		{
			SysLib.short2bytes(direct[i], inodeBlock, offset);
			offset += 2;
		}
		SysLib.short2bytes(indirect, inodeBlock, offset);
		offset += 2;
		
		return SysLib.rawwrite(iNodeBlockNum, inodeBlock);
	}
}
