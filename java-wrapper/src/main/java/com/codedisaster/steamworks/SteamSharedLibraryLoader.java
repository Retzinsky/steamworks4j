package com.codedisaster.steamworks;

import java.io.*;
import java.util.UUID;

class SteamSharedLibraryLoader {

	enum PLATFORM {
		Windows,
		Linux,
		MacOS
	}

	private static final PLATFORM OS;
	private static final boolean IS_64_BIT;

	private static final String SHARED_LIBRARY_EXTRACT_DIRECTORY = System.getProperty(
			"com.codedisaster.steamworks.SharedLibraryExtractDirectory", "steamworks4j");

	private static final String SHARED_LIBRARY_EXTRACT_PATH = System.getProperty(
			"com.codedisaster.steamworks.SharedLibraryExtractPath");

	private static final String SDK_REDISTRIBUTABLE_BIN_PATH = System.getProperty(
			"com.codedisaster.steamworks.SDKRedistributableBinPath", "sdk/redistributable_bin");

	private static final String SDK_LIBRARY_PATH = System.getProperty(
			"com.codedisaster.steamworks.SDKLibraryPath", "sdk/public/steam/lib");

	static final boolean DEBUG = Boolean.parseBoolean(System.getProperty(
			"com.codedisaster.steamworks.Debug", "false"));

	static {
		String osName = System.getProperty("os.name");
		String osArch = System.getProperty("os.arch");

		if (osName.contains("Windows")) {
			OS = PLATFORM.Windows;
		} else if (osName.contains("Linux")) {
			OS = PLATFORM.Linux;
		} else if (osName.contains("Mac")) {
			OS = PLATFORM.MacOS;
		} else {
			throw new RuntimeException("Unknown host architecture: " + osName + ", " + osArch);
		}

		IS_64_BIT = osArch.equals("amd64") || osArch.equals("x86_64");
	}

	private static String getPlatformLibName(String libName) {
		switch (OS) {
			case Windows:
				return libName + (IS_64_BIT ? "64" : "") + ".dll";
			case Linux:
				boolean append64 = IS_64_BIT && !isSdkLibName(libName);
				return "lib" + libName + (append64 ? "64" : "") + ".so";
			case MacOS:
				return "lib" + libName + ".dylib";
		}

		throw new RuntimeException("Unknown host architecture");
	}

	/**
	 * This hackery is required because on Linux, names for 32-bit and 64-bit shared libraries are the same.
	 */
	private static boolean isSdkLibName(String libName) {
		return libName.equals("steam_api") || libName.equals("sdkencryptedappticket");
	}

	static String getSdkRedistributableBinPath() {
		File path;
		switch (OS) {
			case Windows:
				path = new File(SDK_REDISTRIBUTABLE_BIN_PATH, IS_64_BIT ? "win64" : "");
				break;
			case Linux:
				path = new File(SDK_REDISTRIBUTABLE_BIN_PATH, IS_64_BIT ? "linux64" : "linux32");
				break;
			case MacOS:
				path = new File(SDK_REDISTRIBUTABLE_BIN_PATH, "osx32");
				break;
			default:
				return null;
		}

		return path.exists() ? path.getPath() : null;
	}

	static String getSdkLibraryPath() {
		File path;
		switch (OS) {
			case Windows:
				path = new File(SDK_LIBRARY_PATH, IS_64_BIT ? "win64" : "win32");
				break;
			case Linux:
				path = new File(SDK_LIBRARY_PATH, IS_64_BIT ? "linux64" : "linux32");
				break;
			case MacOS:
				path = new File(SDK_LIBRARY_PATH, "osx32");
				break;
			default:
				return null;
		}

		return path.exists() ? path.getPath() : null;
	}

	static void loadLibrary(String libraryName, String libraryPath) throws SteamException {
		try {
			String librarySystemName = getPlatformLibName(libraryName);

			File librarySystemPath = discoverExtractLocation(
					SHARED_LIBRARY_EXTRACT_DIRECTORY + "/" + Version.getVersion(), librarySystemName);

			if (libraryPath == null) {
				extractLibrary(librarySystemPath, librarySystemName);
			} else {
				extractLibrary(librarySystemPath, new File(libraryPath, librarySystemName));
			}

			String absolutePath = librarySystemPath.getCanonicalPath();
			System.load(absolutePath);
		} catch (IOException e) {
			throw new SteamException(e);
		}
	}

	private static void extractLibrary(File librarySystemPath, String librarySystemName) throws IOException {
		extractLibrary(librarySystemPath,
				SteamSharedLibraryLoader.class.getResourceAsStream("/" + librarySystemName));
	}

	private static void extractLibrary(File librarySystemPath, File librarySourcePath) throws IOException {
		extractLibrary(librarySystemPath, new FileInputStream(librarySourcePath));
	}

	private static void extractLibrary(File librarySystemPath, InputStream input) throws IOException {
		if (input != null) {
			try {
				FileOutputStream output = new FileOutputStream(librarySystemPath);
				byte[] buffer = new byte[4096];
				while (true) {
					int length = input.read(buffer);
					if (length == -1) break;
					output.write(buffer, 0, length);
				}
				output.close();
			} catch (IOException e) {
				/*
					Extracting the library may fail, for example because 'nativeFile' already exists and is in
					use by another process. In this case, we fail silently and just try to load the existing file.
				 */
				if (!librarySystemPath.exists()) {
					throw e;
				}
			} finally {
				input.close();
			}
		} else {
			throw new IOException("Failed to read input stream for " + librarySystemPath.getCanonicalPath());
		}
	}

	private static File discoverExtractLocation(String folderName, String fileName) throws IOException {

		File path;

		// system property

		if (SHARED_LIBRARY_EXTRACT_PATH != null) {
			path = new File(SHARED_LIBRARY_EXTRACT_PATH, fileName);
			if (canWrite(path)) {
				return path;
			}
		}

		// Java tmpdir

		path = new File(System.getProperty("java.io.tmpdir") + "/" + folderName, fileName);
		if (canWrite(path)) {
			return path;
		}

		// NIO temp file

		try {
			File file = File.createTempFile(folderName, null);
			if (file.delete()) {
				// uses temp file path as destination folder
				path = new File(file, fileName);
				if (canWrite(path)) {
					return path;
				}
			}
		} catch (IOException ignored) {

		}

		// user home

		path = new File(System.getProperty("user.home") + "/." + folderName, fileName);
		if (canWrite(path)) {
			return path;
		}

		// working directory

		path = new File(".tmp/" + folderName, fileName);
		if (canWrite(path)) {
			return path;
		}

		throw new IOException("No suitable extraction path found");
	}

	private static boolean canWrite(File file) {

		File folder = file.getParentFile();

		if (file.exists()) {
			if (!file.canWrite() || !canExecute(file)) {
				return false;
			}
		} else {
			if (!folder.exists()) {
				if (!folder.mkdirs()) {
					return false;
				}
			}
			if (!folder.isDirectory()) {
				return false;
			}
		}

		File testFile = new File(folder, UUID.randomUUID().toString());

		try {
			new FileOutputStream(testFile).close();
			return canExecute(testFile);
		} catch (IOException e) {
			return false;
		} finally {
			testFile.delete();
		}
	}

	private static boolean canExecute(File file) {

		try {
			if (file.canExecute()) {
				return true;
			}

			if (file.setExecutable(true)) {
				return file.canExecute();
			}
		} catch (Exception ignored) {

		}

		return false;
	}

}
