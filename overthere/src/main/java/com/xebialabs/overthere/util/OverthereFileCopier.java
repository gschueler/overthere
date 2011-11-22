package com.xebialabs.overthere.util;

import com.google.common.io.ByteStreams;
import com.google.common.io.InputSupplier;
import com.google.common.io.OutputSupplier;
import com.xebialabs.overthere.OverthereFile;
import com.xebialabs.overthere.RuntimeIOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Stack;

/**
 * OverthereFile copy utility that uses only the input and output streams exposed by the OverthereFile to perform the copying action.
 * 
 * FIXME: Move to its proper place
 */
public final class OverthereFileCopier extends OverthereFileDirectoryWalker {

	private static final String SOURCE = "Source";
	private static final String DESTINATION = "Destination";

	private Stack<OverthereFile> dstDirStack = new Stack<OverthereFile>();
	private OverthereFile srcDir;

	private OverthereFileCopier(OverthereFile srcDir, OverthereFile dstDir) {
		dstDirStack.push(dstDir);
		this.srcDir = srcDir;
		OverthereFileCopier.checkDirectoryExists(srcDir, SOURCE);
	}

	protected void handleDirectoryStart(OverthereFile scrDir, int depth) throws IOException {
		OverthereFile dstDir = getCurrentDestinationDir();
		if (depth != ROOT) {
			dstDir = createSubdirectoryAndMakeCurrent(dstDir, scrDir.getName());
		}

		if (dstDir.exists()) {
			OverthereFileCopier.checkReallyIsADirectory(dstDir, DESTINATION);
			if (logger.isDebugEnabled())
				logger.debug("About to copy files into existing directory " + dstDir);
		} else {
			if (logger.isDebugEnabled())
				logger.debug("Creating destination directory " + dstDir);
			dstDir.mkdir();
		}
	}

	private OverthereFile createSubdirectoryAndMakeCurrent(OverthereFile parentDir, String subdirName) {
		OverthereFile subdir = parentDir.getFile(subdirName);
		dstDirStack.push(subdir);
		return subdir;
	}

	private void startCopy() {
		walk(srcDir);
	}

	private OverthereFile getCurrentDestinationDir() {
		return dstDirStack.peek();
	}

	protected void handleFile(OverthereFile srcFile, int depth) throws IOException {
		OverthereFile dstFile = getCurrentDestinationDir().getFile(srcFile.getName());
		OverthereFileCopier.copyFile(srcFile, dstFile);
	}

	protected void handleDirectoryEnd(OverthereFile directory, int depth) throws IOException {
		if (depth != ROOT) {
			dstDirStack.pop();
		}
	}

	/**
	 * Copies a file or directory.
	 * 
	 * @param src
	 *            the source file or directory.
	 * @param dst
	 *            the destination file or directory. If it exists it must be of the same type as the source. Its parent directory must exist.
	 * @throws RuntimeIOException
	 *             if an I/O error occurred
	 */
	public static void copy(OverthereFile src, OverthereFile dst) {
		if (src.isDirectory()) {
			copyDirectory(src, dst);
		} else {
			copyFile(src, dst);
		}
	}

	/**
	 * Copies a directory recursively.
	 * 
	 * @param srcDir
	 *            the source directory. Must exist and must not be a directory.
	 * @param dstDir
	 *            the destination directory. May exists but must a directory. Its parent directory must exist.
	 * @throws RuntimeIOException
	 *             if an I/O error occurred
	 */
	private static void copyDirectory(OverthereFile srcDir, OverthereFile dstDir) throws RuntimeIOException {
		OverthereFileCopier dirCopier = new OverthereFileCopier(srcDir, dstDir);
		dirCopier.startCopy();
	}

	/**
	 * Copies a regular file.
	 * 
	 * @param srcFile
	 *            the source file. Must exists and must not be a directory.
	 * @param dstFile
	 *            the destination file. May exists but must not be a directory. Its parent directory must exist.
	 * @throws com.xebialabs.deployit.exception.RuntimeIOException
	 *             if an I/O error occurred
	 */
	private static void copyFile(final OverthereFile srcFile, final OverthereFile dstFile) throws RuntimeIOException {
		checkFileExists(srcFile, SOURCE);
		checkReallyIsAFile(dstFile, DESTINATION);

		if (logger.isDebugEnabled()) {
			if (dstFile.exists())
				logger.debug("About to overwrite existing file " + dstFile);
			logger.debug("Copying file " + srcFile + " to " + dstFile);
		}

		try {
	        ByteStreams.copy(new InputSupplier<InputStream>() {
	            public InputStream getInput() throws IOException {
	                return srcFile.getInputStream();
	            }
	        }, new OutputSupplier<OutputStream>() {
	            public OutputStream getOutput() throws IOException {
	                return dstFile.getOutputStream();
	            }
	        });
        } catch (IOException exc) {
        	throw new RuntimeIOException("Cannot copy " + srcFile + " to " + dstFile, exc);
        }
	}

	/**
	 * Assert that the file must exist and it is not a directory.
	 * 
	 * @param file
	 *            to check.
	 * @param sourceDescription
	 *            to prepend to error message.
	 * @throws RuntimeIOException
	 *             if file does not exist or is a directory.
	 */
	private static void checkFileExists(OverthereFile file, String sourceDescription) {
		if (!file.exists()) {
			throw new RuntimeIOException(sourceDescription + " file " + file + " does not exist");
		}
		checkReallyIsAFile(file, sourceDescription);
	}

	/**
	 * Assert that if a file exists, it is not a directory.
	 * 
	 * @param file
	 *            to check.
	 * @param fileDescription
	 *            to prepend to error message.
	 * @throws RuntimeIOException
	 *             if file is a directory.
	 */
	private static void checkReallyIsAFile(OverthereFile file, String fileDescription) {
		if (file.exists() && file.isDirectory()) {
			throw new RuntimeIOException(fileDescription + " file " + file + " exists but is a directory");
		}
	}

	/**
	 * Assert that the directory exists.
	 * 
	 * @param dir
	 *            is the directory to check.
	 * @param dirDescription
	 *            to prepend to error message.
	 * @throws RuntimeIOException
	 *             if directory does not exist or if it a flat file.
	 */
	private static void checkDirectoryExists(OverthereFile dir, String dirDescription) {
		if (!dir.exists()) {
			throw new RuntimeIOException(dirDescription + " directory " + dir + " does not exist");
		}
		checkReallyIsADirectory(dir, dirDescription);
	}

	/**
	 * Assert that if a file exists, it must be a directory.
	 * 
	 * @param dir
	 *            is the directory to check.
	 * @param dirDescription
	 *            to prepend to error message.
	 * @throws RuntimeIOException
	 *             if file is not a directory.
	 */
	private static void checkReallyIsADirectory(OverthereFile dir, String dirDescription) {
		if (dir.exists() && !dir.isDirectory()) {
			throw new RuntimeIOException(dirDescription + " directory " + dir + " exists but is not a directory");
		}
	}

	private static Logger logger = LoggerFactory.getLogger(OverthereFileCopier.class);

}
