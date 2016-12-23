package org.kneelawk.hellojmonkeylauncher;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * CPControl v3.1<br>
 * Sorry about the mess. This should be an entire library or at least a package,
 * but is stuffed into one class for ease of copy-and-paste.
 */
public class CPControl3 {
	protected File baseDir;
	protected String mainClassName;

	protected List<DependencyOperation> operations = new ArrayList<>();

	protected URLClassLoader loader;

	protected ErrorCallback errorCallback = DEFAULT_ERROR_CALLBACK;

	public CPControl3(String mainClassName) {
		this(mainClassName, PARENT);
	}

	public CPControl3(String mainClassName, File baseDir) {
		this.mainClassName = mainClassName;
		this.baseDir = baseDir;

		Thread hook = new Thread(new Runnable() {
			@Override
			public void run() {
				if (loader != null) {
					try {
						loader.close();
					} catch (IOException e) {
						System.err.println("Error closing class loader");
						e.printStackTrace();
					}
				}
			}
		});

		Runtime.getRuntime().addShutdownHook(hook);
	}

	public void addOperation(DependencyOperation operation) {
		operations.add(operation);
	}

	public void addLibrary(File library) {
		operations.add(new LibraryAddOperation(library));
	}

	public void addNativeDir(File nativeDir) {
		operations.add(new NativeAddOperation(nativeDir));
	}

	public LibraryExtractFromClasspathOperation addExtractingFromClasspathLibrary() {
		LibraryExtractFromClasspathOperation operation = new LibraryExtractFromClasspathOperation();
		operations.add(operation);
		return operation;
	}

	public NativeExtractFromClasspathOperation addExtractingFromClasspathNativeDir() {
		NativeExtractFromClasspathOperation operation = new NativeExtractFromClasspathOperation();
		operations.add(operation);
		return operation;
	}

	public LibraryExtractFromFileOperation addExtractingFromFileLibrary(File file) {
		LibraryExtractFromFileOperation operation = new LibraryExtractFromFileOperation(file);
		operations.add(operation);
		return operation;
	}

	public NativeExtractFromFileOperation addExtractingFromFileNativeDir(File file) {
		NativeExtractFromFileOperation operation = new NativeExtractFromFileOperation(file);
		operations.add(operation);
		return operation;
	}

	public void setErrorCallback(ErrorCallback callback) {
		errorCallback = callback;
	}

	public void launch(String[] args) throws IOException, InterruptedException {
		ClassPath path = new ClassPath();

		for (DependencyOperation operation : operations) {
			operation.perform(path, baseDir);
		}

		for (String dir : path.nativeDirs) {
			addNativesDir(dir);
		}

		URL[] urls = copyFilesToClassPath(path.classpath);
		loader = new URLClassLoader(urls);

		Launcher launcher = new Launcher(loader, mainClassName, args, errorCallback);
		launcher.start();
	}

	public static final String LIBS_DIR_NAME = "libs";
	public static final String NATIVES_DIR_NAME = "natives";

	public static final File ME = new File(
			CPControl3.class.getProtectionDomain().getCodeSource().getLocation().getPath());
	public static final File PARENT = ME.getParentFile();

	public static final FileFilter IS_JAR_FILE = new FileFilter() {
		@Override
		public boolean accept(File pathname) {
			return pathname.getName().toLowerCase().endsWith(".jar");
		}
	};

	public static final FileFilter IS_NATIVE_FILE = new FileFilter() {
		@Override
		public boolean accept(File file) {
			String name = file.getName().toLowerCase();
			return name.endsWith(".so") || name.endsWith(".dll") || name.endsWith(".jnilib")
					|| name.endsWith(".dynlib");
		}
	};

	public static final FileFilter IS_ME = new FileFilter() {
		@Override
		public boolean accept(File file) throws IOException {
			return file.getCanonicalPath().equals(ME.getCanonicalPath());
		}
	};

	public static final EntryFilter IS_JAR_ENTRY = new EntryFilter() {
		@Override
		public boolean accept(String path) throws IOException {
			return path.toLowerCase().endsWith(".jar");
		}
	};

	public static final EntryFilter IS_NATIVE_ENTRY = new EntryFilter() {
		@Override
		public boolean accept(String path) throws IOException {
			String lower = path.toLowerCase();
			return lower.endsWith(".so") || lower.endsWith(".dll") || lower.endsWith(".jnilib")
					|| lower.endsWith(".dynlib");
		}
	};

	public static final ResourceDeletionPolicy ALWAYS_DELETE = new ResourceDeletionPolicy() {
		@Override
		public boolean shouldDeleteOnExit(File resource) {
			return true;
		}
	};

	public static final ResourceDeletionPolicy NEVER_DELETE = new ResourceDeletionPolicy() {
		@Override
		public boolean shouldDeleteOnExit(File resource) {
			return false;
		}
	};

	public static final ErrorCallback DEFAULT_ERROR_CALLBACK = new ErrorCallback() {
		@Override
		public void error(Throwable t) {
			t.printStackTrace();
		}
	};

	private static Set<File> librariesOnClasspath;

	public static interface ErrorCallback {
		public void error(Throwable t);
	}

	public static class Launcher {
		protected ClassLoader loader;
		protected String mainClass;
		protected String[] args;
		protected ErrorCallback error;

		public Launcher(ClassLoader loader, String mainClass, String[] args, ErrorCallback error) {
			this.loader = loader;
			this.mainClass = mainClass;
			this.args = args;
			this.error = error;
		}

		public void start() throws InterruptedException {
			Thread t = new Thread(new Runnable() {
				@Override
				public void run() {
					System.out.println("Loading context class loader: "
							+ Thread.currentThread().getContextClassLoader().getClass());
					try {
						Class<?> clazz = Thread.currentThread().getContextClassLoader().loadClass(mainClass);
						Method main = clazz.getMethod("main", String[].class);
						main.invoke(null, new Object[] { args });
					} catch (ClassNotFoundException e) {
						error.error(e);
					} catch (NoSuchMethodException e) {
						error.error(e);
					} catch (SecurityException e) {
						error.error(e);
					} catch (IllegalAccessException e) {
						error.error(e);
					} catch (IllegalArgumentException e) {
						error.error(e);
					} catch (InvocationTargetException e) {
						error.error(e);
					} catch (Exception e) {
						error.error(e);
					}
				}
			}, "Application");
			t.setContextClassLoader(loader);
			t.start();
			t.join();
		}
	}

	public static interface DependencyOperation {
		public void perform(ClassPath cp, File baseDir) throws IOException;
	}

	public static class LibraryAddOperation implements DependencyOperation {
		private File libFile;

		public LibraryAddOperation(File libFile) {
			this.libFile = libFile;
		}

		@Override
		public void perform(ClassPath cp, File baseDir) throws IOException {
			cp.addLibrary(libFile);
		}
	}

	public static class NativeAddOperation implements DependencyOperation {
		private File nativeDir;

		public NativeAddOperation(File nativeDir) {
			this.nativeDir = nativeDir;
		}

		@Override
		public void perform(ClassPath cp, File baseDir) throws IOException {
			cp.addNativeDir(nativeDir.getCanonicalPath());
		}
	}

	public static class LibraryExtractFromClasspathOperation implements DependencyOperation {

		private List<ExtractFromCollectionDescription> descs = new ArrayList<>();

		public LibraryExtractFromClasspathOperation addLibrary(ExtractFromCollectionDescription desc) {
			descs.add(desc);
			return this;
		}

		public LibraryExtractFromClasspathOperation addLibrary(String dirName, FileFilter toSearch,
				EntryFilter searchFor, ResourceDeletionPolicy deletionPolicy) {
			addLibrary(new ExtractFromClasspathDescription(dirName, toSearch, searchFor, deletionPolicy));
			return this;
		}

		@Override
		public void perform(ClassPath cp, File baseDir) throws IOException {
			final File libsDir = new File(baseDir, LIBS_DIR_NAME);
			if (!libsDir.exists())
				libsDir.mkdirs();

			Set<File> libs = new HashSet<>();
			libs.addAll(getLibrariesOnClasspath());
			libs.addAll(cp.classpath);

			final Map<File, List<ExtractFromCollectionDescription>> whoWantsWhat = new HashMap<>();

			Iterator<File> it = libs.iterator();
			while (it.hasNext()) {
				File lib = it.next();
				List<ExtractFromCollectionDescription> who = new ArrayList<>();
				for (ExtractFromCollectionDescription desc : descs) {
					if (desc.getToSearch().accept(lib)) {
						who.add(desc);
					}
				}
				if (who.isEmpty()) {
					it.remove();
				} else {
					whoWantsWhat.put(lib, who);
				}
			}

			for (final File lib : libs) {
				OwnedObjectExtractionHandler handler = new OwnedObjectExtractionHandler(libsDir, whoWantsWhat.get(lib));
				Set<File> extracted = extractFilesMatching(lib, handler, handler);

				cp.classpath.addAll(extracted);
			}
		}
	}

	public static class NativeExtractFromClasspathOperation implements DependencyOperation {
		private List<ExtractFromCollectionDescription> descs = new ArrayList<>();

		public NativeExtractFromClasspathOperation addNative(ExtractFromCollectionDescription desc) {
			descs.add(desc);
			return this;
		}

		public NativeExtractFromClasspathOperation addNative(String dirName, FileFilter toSearch, EntryFilter searchFor,
				ResourceDeletionPolicy deletionPolicy) {
			addNative(new ExtractFromClasspathDescription(dirName, toSearch, searchFor, deletionPolicy));
			return this;
		}

		@Override
		public void perform(ClassPath cp, File baseDir) throws IOException {
			final File nativesDir = new File(baseDir, NATIVES_DIR_NAME);
			if (!nativesDir.exists())
				nativesDir.mkdirs();

			Set<File> libs = new HashSet<>();
			libs.addAll(getLibrariesOnClasspath());
			libs.addAll(cp.classpath);

			final Map<File, List<ExtractFromCollectionDescription>> whoWantsWhat = new HashMap<>();

			Iterator<File> it = libs.iterator();
			while (it.hasNext()) {
				File lib = it.next();
				List<ExtractFromCollectionDescription> who = new ArrayList<>();
				for (ExtractFromCollectionDescription desc : descs) {
					if (desc.getToSearch().accept(lib)) {
						who.add(desc);
					}
				}
				if (who.isEmpty()) {
					it.remove();
				} else {
					whoWantsWhat.put(lib, who);
				}
			}

			for (final File lib : libs) {
				OwnedObjectExtractionHandler handler = new OwnedObjectExtractionHandler(nativesDir,
						whoWantsWhat.get(lib));
				Set<File> extracted = extractFilesMatching(lib, handler, handler);

				for (File f : extracted) {
					cp.nativeDirs.add(f.getParentFile().getCanonicalPath());
				}
			}
		}
	}

	public static class LibraryExtractFromFileOperation implements DependencyOperation {
		private File file;
		private List<ExtractDescription> descs = new ArrayList<>();

		public LibraryExtractFromFileOperation(File file) {
			this.file = file;
		}

		public LibraryExtractFromFileOperation addLibrary(ExtractDescription desc) {
			descs.add(desc);
			return this;
		}

		public LibraryExtractFromFileOperation addLibrary(String dirName, EntryFilter searchFor,
				ResourceDeletionPolicy deletionPolicy) {
			addLibrary(new ExtractFromFileDescription(dirName, searchFor, deletionPolicy));
			return this;
		}

		@Override
		public void perform(ClassPath cp, File baseDir) throws IOException {
			File libsDir = new File(baseDir, LIBS_DIR_NAME);

			OwnedObjectExtractionHandler handler = new OwnedObjectExtractionHandler(libsDir, descs);

			Set<File> extracted = extractFilesMatching(file, handler, handler);

			cp.classpath.addAll(extracted);
		}
	}

	public static class NativeExtractFromFileOperation implements DependencyOperation {
		private File file;
		private List<ExtractDescription> descs = new ArrayList<>();

		public NativeExtractFromFileOperation(File file) {
			this.file = file;
		}

		public NativeExtractFromFileOperation addNative(ExtractDescription desc) {
			descs.add(desc);
			return this;
		}

		public NativeExtractFromFileOperation addNative(String dirName, EntryFilter searchFor,
				ResourceDeletionPolicy deletionPolicy) {
			addNative(new ExtractFromFileDescription(dirName, searchFor, deletionPolicy));
			return this;
		}

		@Override
		public void perform(ClassPath cp, File baseDir) throws IOException {
			File nativesDir = new File(baseDir, NATIVES_DIR_NAME);

			OwnedObjectExtractionHandler handler = new OwnedObjectExtractionHandler(nativesDir, descs);

			Set<File> extracted = extractFilesMatching(file, handler, handler);

			for (File f : extracted) {
				cp.nativeDirs.add(f.getParentFile().getCanonicalPath());
			}
		}
	}

	public static class ClassPath {
		public Set<File> classpath = new HashSet<>();
		public Set<String> nativeDirs = new HashSet<>();

		public void addLibrary(File lib) {
			classpath.add(lib);
		}

		public void addLibraries(Collection<File> libs) {
			classpath.addAll(libs);
		}

		public void addNativeDir(String dir) {
			nativeDirs.add(dir);
		}

		public void addNativeDirs(Collection<String> dirs) {
			nativeDirs.addAll(dirs);
		}
	}

	public static interface FileFilter {
		public boolean accept(File file) throws IOException;
	}

	public static class AndFileFilter implements FileFilter {
		private FileFilter[] filters;

		public AndFileFilter(FileFilter... filters) {
			this.filters = filters;
		}

		@Override
		public boolean accept(File file) throws IOException {
			for (FileFilter filter : filters) {
				if (!filter.accept(file))
					return false;
			}
			return true;
		}
	}

	public static class NameContainsFileFilter implements FileFilter {
		private String contents;

		public NameContainsFileFilter(String contents) {
			this.contents = contents.toLowerCase();
		}

		@Override
		public boolean accept(File file) throws IOException {
			String name = file.getName().toLowerCase();
			return name.contains(contents);
		}
	}

	public static interface EntryFilter {
		public boolean accept(String path) throws IOException;
	}

	public static class AndEntryFilter implements EntryFilter {
		private EntryFilter[] filters;

		public AndEntryFilter(EntryFilter... filters) {
			this.filters = filters;
		}

		@Override
		public boolean accept(String path) throws IOException {
			for (EntryFilter filter : filters) {
				if (!filter.accept(path))
					return false;
			}
			return true;
		}
	}

	public static class NameContainsEntryFilter implements EntryFilter {
		private String contents;

		public NameContainsEntryFilter(String contents) {
			this.contents = contents.toLowerCase();
		}

		@Override
		public boolean accept(String path) {
			String name = getPathName(path).toLowerCase();
			return name.contains(contents);
		}
	}

	public static class DirectoryEntryFilter implements EntryFilter {
		private String dir;

		public DirectoryEntryFilter(String dir) {
			if (!dir.startsWith("/")) {
				dir = "/" + dir;
			}

			this.dir = dir;
		}

		@Override
		public boolean accept(String path) throws IOException {
			if (!path.startsWith("/"))
				path = "/" + path;
			return path.startsWith(dir);
		}
	}

	public static interface DestinationProvider {
		public File getFile(String path);
	}

	public static class FlatDestinationProvider implements DestinationProvider {
		private File parent;
		private ResourceDeletionPolicy policy;

		public FlatDestinationProvider(File parent, ResourceDeletionPolicy policy) {
			this.parent = parent;
			this.policy = policy;
		}

		@Override
		public File getFile(String path) {
			return inactResourceDeletionPolicy(new File(parent, getPathName(path)), policy);
		}
	}

	public static class DirectoryDestinationProvider implements DestinationProvider {
		private File parent;
		private ResourceDeletionPolicy policy;

		public DirectoryDestinationProvider(File parent, ResourceDeletionPolicy policy) {
			this.parent = parent;
			this.policy = policy;
		}

		@Override
		public File getFile(String path) {
			return inactResourceDeletionPolicy(new File(parent, path), policy);
		}
	}

	public static class OwnedObjectExtractionHandler implements EntryFilter, DestinationProvider {
		private File baseDir;
		private Collection<? extends ExtractDescription> descs;

		private Map<String, ExtractDescription> acceptedDirDescriptions = new HashMap<>();

		public OwnedObjectExtractionHandler(File baseDir, Collection<? extends ExtractDescription> descs) {
			this.baseDir = baseDir;
			this.descs = descs;
		}

		@Override
		public File getFile(String path) {
			// getFile should not be called until the path has already been
			// accepted
			if (!acceptedDirDescriptions.containsKey(path))
				throw new RuntimeException("File destination requested before the path has been accepted");
			ExtractDescription desc = acceptedDirDescriptions.get(path);
			File dir = new File(baseDir, desc.getDirName());
			if (!dir.exists())
				dir.mkdir();
			return inactResourceDeletionPolicy(new File(dir, getPathName(path)), desc.getDeletionPolicy());
		}

		@Override
		public boolean accept(String path) throws IOException {
			boolean keep = false;
			for (ExtractDescription desc : descs) {
				if (desc.getSearchFor().accept(path)) {
					keep = true;
					acceptedDirDescriptions.put(path, desc);
					break;
				}
			}
			return keep;
		}

	}

	public static interface ResourceDeletionPolicy {
		public boolean shouldDeleteOnExit(File resource);
	}

	public static interface ExtractDescription {
		public String getDirName();

		public EntryFilter getSearchFor();

		public ResourceDeletionPolicy getDeletionPolicy();
	}

	public static class ExtractFromFileDescription implements ExtractDescription {
		private String dirName;
		private EntryFilter searchFor;
		private ResourceDeletionPolicy deletionPolicy;

		public ExtractFromFileDescription(String dirName, EntryFilter searchFor,
				ResourceDeletionPolicy deletionPolicy) {
			super();
			this.dirName = dirName;
			this.searchFor = searchFor;
			this.deletionPolicy = deletionPolicy;
		}

		public String getDirName() {
			return dirName;
		}

		public EntryFilter getSearchFor() {
			return searchFor;
		}

		public ResourceDeletionPolicy getDeletionPolicy() {
			return deletionPolicy;
		}
	}

	public static interface ExtractFromCollectionDescription extends ExtractDescription {
		public FileFilter getToSearch();
	}

	public static class ExtractFromClasspathDescription extends ExtractFromFileDescription
			implements ExtractFromCollectionDescription {
		private FileFilter toSearch;

		public ExtractFromClasspathDescription(String dirName, FileFilter toSearch, EntryFilter searchFor,
				ResourceDeletionPolicy deletionPolicy) {
			super(dirName, searchFor, deletionPolicy);
			this.toSearch = toSearch;
		}

		public FileFilter getToSearch() {
			return toSearch;
		}
	}

	public static String getPathName(String path) {
		return path.substring(path.lastIndexOf('/') + 1);
	}

	public static File inactResourceDeletionPolicy(File resource, ResourceDeletionPolicy policy) {
		if (policy.shouldDeleteOnExit(resource))
			resource.deleteOnExit();
		return resource;
	}

	public static String[] getClassPath() {
		String classPath = System.getProperty("sun.boot.class.path") + File.pathSeparator
				+ System.getProperty("java.ext.path") + File.pathSeparator + System.getProperty("java.class.path");
		return classPath.split(File.pathSeparator);
	}

	private static Set<File> findLibrariesOnClasspath() throws IOException {
		Set<File> found = new HashSet<>();

		String[] classPath = getClassPath();
		for (String path : classPath) {
			File file = new File(path);
			if (file.exists())
				recursiveSearch(found, new HashSet<>(), file, IS_JAR_FILE);
		}

		return found;
	}

	public static Set<File> recalculateLibrariesOnClasspath() throws IOException {
		return librariesOnClasspath = findLibrariesOnClasspath();
	}

	public static Set<File> getLibrariesOnClasspath() throws IOException {
		if (librariesOnClasspath == null) {
			librariesOnClasspath = findLibrariesOnClasspath();
		}
		return librariesOnClasspath;
	}

	private static void recursiveSearch(Collection<File> found, Set<File> searched, File dir, FileFilter filter)
			throws IOException {
		if (searched.contains(dir))
			return;
		if (dir.isDirectory()) {
			searched.add(dir);
			File[] children = dir.listFiles();
			for (File child : children) {
				if (child.isDirectory()) {
					if (searched.contains(child))
						continue;
					recursiveSearch(found, searched, child, filter);
				} else if (filter.accept(child)) {
					found.add(child);
				}
			}
		} else {
			if (filter.accept(dir)) {
				found.add(dir);
			}
		}
	}

	public static Set<File> filterFiles(Collection<File> files, FileFilter filter) throws IOException {
		Set<File> filteredFiles = new HashSet<>();

		for (File inputFile : files) {
			if (filter.accept(inputFile)) {
				filteredFiles.add(inputFile);
			}
		}

		return filteredFiles;
	}

	public static URL[] copyFilesToClassPath(Collection<File> files) throws MalformedURLException {
		int size = files.size();
		URL[] urls = new URL[size];

		Iterator<File> it = files.iterator();
		for (int i = 0; i < size; i++) {
			File file = it.next();
			urls[i] = file.toURI().toURL();
		}

		return urls;
	}

	public static Set<File> extractFilesMatching(Collection<File> archives, EntryFilter filter,
			DestinationProvider destinations) throws IOException {
		Set<File> extractedFiles = new HashSet<>();
		for (File archive : archives) {
			extractedFiles.addAll(extractFilesMatching(archive, filter, destinations));
		}
		return extractedFiles;
	}

	public static Set<File> extractFilesMatching(File archive, EntryFilter filter, DestinationProvider destinations)
			throws IOException {
		Set<File> extractedFiles = new HashSet<>();

		if (archive.isDirectory()) {
			extractFilesMatchingFromDirectory(archive, new HashSet<>(), extractedFiles, "/", filter, destinations);
		} else {
			ZipInputStream zis = new ZipInputStream(new FileInputStream(archive));
			ZipEntry entry;

			while ((entry = zis.getNextEntry()) != null) {
				String path = entry.getName();
				File dest;
				if (!entry.isDirectory() && filter.accept(path) && (dest = destinations.getFile(path)) != null) {
					// make sure parent dirs exist
					File parent = dest.getParentFile();
					if (!parent.exists())
						parent.mkdirs();

					// copy the file
					FileOutputStream fos = new FileOutputStream(dest);
					copy(zis, fos);
					fos.close();

					// keep track of where we put the files
					extractedFiles.add(dest);
				}
				zis.closeEntry();
			}

			zis.close();
		}

		return extractedFiles;
	}

	private static void extractFilesMatchingFromDirectory(File dir, Set<File> visited, Collection<File> extracted,
			String path, EntryFilter filter, DestinationProvider prov) throws IOException {
		if (visited.contains(dir))
			return;
		visited.add(dir);
		if (dir.isDirectory()) {
			File[] children = dir.listFiles();
			for (File child : children) {
				path += child.getName();
				if (child.isDirectory()) {
					path += "/";
					extractFilesMatchingFromDirectory(child, visited, extracted, path, filter, prov);
				} else {
					File to;
					if (filter.accept(path) && (to = prov.getFile(path)) != null) {
						FileInputStream fis = new FileInputStream(child);
						FileOutputStream fos = new FileOutputStream(to);

						copy(fis, fos);

						fis.close();
						fos.close();
					}
				}
			}
		} else {
			File to = null;
			if (filter.accept(path) && (to = prov.getFile(path)) != null) {
				FileInputStream fis = new FileInputStream(dir);
				FileOutputStream fos = new FileOutputStream(to);

				copy(fis, fos);

				fis.close();
				fos.close();
			}
		}
	}

	public static void extractFileFromSystemClasspath(String path, File to) throws IOException {
		extractFileFromSystemClasspath(CPControl3.class, path, to);
	}

	public static void extractFileFromSystemClasspath(Class<?> relative, String path, File to) throws IOException {
		InputStream is = relative.getResourceAsStream(path);
		if (is == null)
			throw new IOException("File: " + path + " not found on classapth");
		FileOutputStream fos = new FileOutputStream(to);
		copy(is, fos);
	}

	public static void copy(InputStream is, OutputStream os) throws IOException {
		byte[] buf = new byte[8192];
		int read;
		while ((read = is.read(buf)) >= 0) {
			os.write(buf, 0, read);
		}
	}

	public static void addNativesDir(String dirName) throws IOException {
		try {
			// This enables the java.library.path to be modified at runtime
			// From a Sun engineer at
			// http://forums.sun.com/thread.jspa?threadID=707176
			//
			Field field = ClassLoader.class.getDeclaredField("usr_paths");
			field.setAccessible(true);
			String[] paths = (String[]) field.get(null);
			for (int i = 0; i < paths.length; i++) {
				if (dirName.equals(paths[i])) {
					return;
				}
			}
			String[] tmp = new String[paths.length + 1];
			System.arraycopy(paths, 0, tmp, 0, paths.length);
			tmp[paths.length] = dirName;
			field.set(null, tmp);
			System.setProperty("java.library.path",
					System.getProperty("java.library.path") + File.pathSeparator + dirName);
		} catch (IllegalAccessException e) {
			throw new IOException("Failed to get permissions to set library path");
		} catch (NoSuchFieldException e) {
			throw new IOException("Failed to get field handle to set library path");
		}
	}
}