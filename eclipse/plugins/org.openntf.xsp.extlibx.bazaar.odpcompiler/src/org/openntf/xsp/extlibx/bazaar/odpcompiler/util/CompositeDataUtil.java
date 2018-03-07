package org.openntf.xsp.extlibx.bazaar.odpcompiler.util;

import static org.openntf.xsp.extlibx.bazaar.odpcompiler.util.ODSConstants.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.activation.MimetypesFileTypeMap;

public enum CompositeDataUtil {
	;

	public static byte[] getFileResourceData(InputStream is, int fileLength) throws IOException {
		// Spec out the structure
		int segCount = fileLength / FILE_SEGMENT_SIZE_CAP;
		if (fileLength % FILE_SEGMENT_SIZE_CAP > 0) {
			segCount++;
		}
		
		int totalSize = SIZE_CDFILEHEADER + (SIZE_CDFILESEGMENT * segCount) + fileLength + (fileLength % 2);
		
		// Now create a CD record for the file data
		// TODO this could be a little more efficient by writing to a Base64 wrapper and
		//   cutting off and making a new item node at size intervals
		ByteBuffer buf = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN) ;
		// CDFILEHEADER
		{
			buf.putShort(SIG_CDFILEHEADER);// Header.Signature
			buf.putInt(SIZE_CDFILEHEADER); // Header.Length
			buf.putShort((short)0);        // FileExtLen
			buf.putInt(fileLength);        // FileDataSize
			buf.putInt(segCount);          // SegCount
			buf.putInt(0);                 // Flags
			buf.putInt(0);                 // Reserved
		}
		for(int i = 0; i < segCount; i++) {
			// Each chunk begins with a CDFILESEGMENT
	
			// Figure out our data and segment sizes
			int dataOffset = FILE_SEGMENT_SIZE_CAP * i;
			short dataSize = (short)Math.min((fileLength - dataOffset), FILE_SEGMENT_SIZE_CAP);
			short segSize = (short)(dataSize + (dataSize % 2));
	
			// CDFILESEGMENT
			{
				buf.putShort(SIG_CDFILESEGMENT);          // Header.Signature
				buf.putInt(segSize + SIZE_CDFILESEGMENT); // Header.Length
				buf.putShort((short)dataSize);            // DataSize
				buf.putShort((short)segSize);             // SegSize
				buf.putInt(0);                            // Flags
				buf.putInt(0);                            // Reserved
				
				byte[] segData = new byte[dataSize];
				is.read(segData);
				buf.put(segData);
				if(segSize > dataSize) {
					buf.put((byte)0);
				}
			}
		}
		return buf.array();
	}

	public static byte[] getImageResourceData(Path file) throws IOException {
		int fileLength = (int)Files.size(file);
		// Load image info
		File imageFile = file.toFile();
		int height = 0; // true value not actually stored
		int width = 0; // true value not actually stored
		String mimeType = new MimetypesFileTypeMap().getContentType(imageFile);
		if(mimeType == null) {
			throw new RuntimeException("Cannot determine MIME type for " + file);
		}
		short imageType = 0;
		switch(mimeType) {
		case "image/gif":
			imageType = 1; // CDIMAGETYPE_GIF
			break;
		case "image/jpeg":
		case "image/png": // for some reason
			imageType = 2; // CDIMAGETYPE_JPEG
			break;
		case "image/bmp":
			imageType = 3; // CDIMAGETYPE_BMP
			break;
		default:
			// Everything else is 0
			break;
		}
		
		// Spec out the structure
		int segCount = fileLength / IMAGE_SEGMENT_SIZE_CAP;
		if (fileLength % IMAGE_SEGMENT_SIZE_CAP > 0) {
			segCount++;
		}
		
		int totalSize = SIZE_CDGRAPHIC + SIZE_CDIMAGEHEADER + (SIZE_CDIMAGESEGMENT * segCount) + fileLength + (fileLength % 2);
		
		// Now create a CD record for the file data
		// TODO this could be a little more efficient by writing to a Base64 wrapper and
		//   cutting off and making a new item node at size intervals
		ByteBuffer buf = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN) ;
		// CDGRAPHIC
		{
			buf.putShort(SIG_CDGRAPHIC); // Header.Signature
			buf.putInt(SIZE_CDGRAPHIC);  // Header.Length
			buf.putShort((short)0);      // DestSize.width
			buf.putShort((short)0);      // DestSize.height
			buf.putShort((short)0);      // CropSize.height
			buf.putShort((short)0);      // CropSize.width
			buf.putShort((short)0);      // CropOffset.left
			buf.putShort((short)0);      // CropOffset.top
			buf.putShort((short)0);      // CropOffset.right
			buf.putShort((short)0);      // CropOffset.bottom
			buf.putShort((short)0);      // fResize
			buf.put(CDGRAPHIC_VERSION3); // Version
			buf.put((byte)0);            // bFlags;
			buf.putShort((short)0);      // wReserved
		}
		// CDIMAGEHEADER
		{
			buf.putShort(SIG_CDIMAGEHEADER);// Header.Signature
			buf.putInt(SIZE_CDIMAGEHEADER); // Header.Length
			buf.putShort(imageType);        // ImageType
			buf.putShort((short)width);     // Width
			buf.putShort((short)height);    // Height
			buf.putInt(fileLength);         // ImageDataSize
			buf.putInt(segCount);           // SegCount
			buf.putInt(0);                  // Flags
			buf.putInt(0);                  // Reserved
		}
		try(InputStream is = Files.newInputStream(file)) {
			for(int i = 0; i < segCount; i++) {
				// Each chunk begins with a CDIMAGESEGMENT
	
				// Figure out our data and segment sizes
				int dataOffset = IMAGE_SEGMENT_SIZE_CAP * i;
				short dataSize = (short)Math.min((fileLength - dataOffset), IMAGE_SEGMENT_SIZE_CAP);
				short segSize = (short)(dataSize + (dataSize % 2));
	
				// CDIMAGESEGMENT
				{
					buf.putShort(SIG_CDIMAGESEGMENT);          // Header.Signature - SIG_CDIMAGESEGMENT
					buf.putInt(segSize + SIZE_CDIMAGESEGMENT); // Header.Length
					buf.putShort((short)dataSize);             // DataSize
					buf.putShort((short)segSize);              // SegSize
					
					byte[] segData = new byte[dataSize];
					is.read(segData);
					buf.put(segData);
					if(segSize > dataSize) {
						buf.put((byte)0);
					}
				}
			}
		}
		return buf.array();
	}
	
	public static byte[] getJavaScriptLibraryData(Path file) throws IOException {
		int fileLength = (int)Files.size(file);
		
		// Spec out the structure
		int segCount = fileLength / BLOBPART_SIZE_CAP;
		if (fileLength % BLOBPART_SIZE_CAP > 0) {
			segCount++;
		}
		
		int paddedLength = fileLength + 1; // Make sure there's at least one \0 at the end(?)
		int totalSize = SIZE_CDEVENT + (SIZE_CDBLOBPART * segCount) + paddedLength + (paddedLength % 2);
		
		// Now create a CD record for the file data
		// TODO this could be a little more efficient by writing to a Base64 wrapper and
		//   cutting off and making a new item node at size intervals
		ByteBuffer buf = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN) ;
		// CDEVENT
		{
			buf.putShort(SIG_CDEVENT);            // Header.Signature
			buf.putShort(SIZE_CDEVENT);           // Header.Length
			buf.putInt(0);                        // Flags
			buf.putShort(HTML_EVENT_LIBRARY);     // EventType
			buf.putShort(ACTION_TYPE_JAVASCRIPT); // ActionType
			buf.putInt(paddedLength);               // ActionLength
			buf.putShort((short)0);               // SignatureLength
			buf.put(new byte[14]);                // Reserved
		}
		try(InputStream is = Files.newInputStream(file)) {
			for(int i = 0; i < segCount; i++) {
				// Each chunk begins with a CDBLOBPART
	
				// Figure out our data and segment sizes
				int dataOffset = BLOBPART_SIZE_CAP * i;
				short dataSize = (short)Math.min((paddedLength - dataOffset), BLOBPART_SIZE_CAP);
				short segSize = (short)(dataSize + (dataSize % 2));
	
				// CDBLOBPART
				{
					buf.putShort(SIG_CDBLOBPART);                     // Header.Signature
					buf.putShort((short)(segSize + SIZE_CDBLOBPART)); // Header.Length
					buf.putShort(SIG_CDEVENT);                        // OwnerSig
					buf.putShort((short)segSize);                     // Length
					buf.putShort((short)BLOBPART_SIZE_CAP);           // BlobMax
					buf.put(new byte[8]);                             // Reserved
					
					byte[] segData = new byte[dataSize];
					is.read(segData);
					buf.put(segData);
					if(segSize > dataSize) {
						buf.put(new byte[segSize-dataSize]);
					}
				}
			}
		}
		return buf.array();
	}
}