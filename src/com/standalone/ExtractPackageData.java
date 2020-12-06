package com.standalone;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

/**
 * The Class ExtractPackageData.
 * 
 * @author sudheerdvn
 */
public class ExtractPackageData {

	/** The Constant MAX_SIZE. Update the constant to the display the files greater than the max size. */
	public static final int MAX_SIZE = 3;

	/**
	 * The main method.
	 *
	 * @param args the arguments
	 * @throws IOException Signals that an I/O exception has occurred.
	 */
	public static void main(String[] args) throws IOException {
		readFileData();
	}

	/**
	 * Read file data.
	 */
	private static void readFileData() {
		File file = new File("C:\\data\\packagesinfo.txt");
		BufferedReader bufferredReader = null;
		try {
			bufferredReader = new BufferedReader(new FileReader(file));
			String line;
			String zipFileName = null;
			int count = 0;
			while ((line = bufferredReader.readLine()) != null) {
				if (line.contains(".zip")) {
					zipFileName = line;
				}
				if (line.contains(" MB")) {
					String packageSize = line.substring(line.indexOf("|  ") + 3, line.length());
					/*
					 * Printing only the packages which are more than a specific size, if you want all of them you can
					 * remove the condition
					 */
					if (checkPackageSize(packageSize)) {
						count++;
						System.out.println(zipFileName);
						System.out.println(packageSize);
					}
					zipFileName = null;
				}
			}
			System.out.println("number of mb packages are : " + count);
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			if (null != bufferredReader) {
				try {
					bufferredReader.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}
	}

	/**
	 * Check package size.
	 *
	 * @param packageSize the package size
	 * @return true, if successful
	 */
	private static boolean checkPackageSize(String packageSize) {
		boolean isBig = false;
		String size = packageSize.substring(0, packageSize.indexOf(" MB"));
		Float sizeInFloat = Float.parseFloat(size);
		if (sizeInFloat > MAX_SIZE) {
			isBig = true;
		}
		return isBig;
	}
}
