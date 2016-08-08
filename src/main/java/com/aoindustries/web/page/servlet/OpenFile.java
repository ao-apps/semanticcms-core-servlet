/*
 * ao-web-page-servlet - Java API for modeling web page content and relationships in a Servlet environment.
 * Copyright (C) 2013, 2014, 2015, 2016  AO Industries, Inc.
 *     support@aoindustries.com
 *     7262 Bull Pen Cir
 *     Mobile, AL 36695
 *
 * This file is part of ao-web-page-servlet.
 *
 * ao-web-page-servlet is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * ao-web-page-servlet is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with ao-web-page-servlet.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.aoindustries.web.page.servlet;

import com.aoindustries.io.FileUtils;
import com.aoindustries.lang.ProcessResult;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.jsp.SkipPageException;

final public class OpenFile {

	private static final Logger logger = Logger.getLogger(OpenFile.class.getName());

	private static final String ENABLE_INIT_PARAM = OpenFile.class.getName() + ".enabled";

	private static final String FILE_OPENERS_REQUEST_ATTRIBUTE_NAME = OpenFile.class.getName()+".fileOpeners";

	/**
	 * Checks if the given host address is allowed to open files on the server.
	 */
	private static boolean isAllowedAddr(String addr) {
		return "127.0.0.1".equals(addr);
	}

	/**
	 * Checks if the given request is allowed to open files on the server.
	 * The servlet init param must have it enabled, as well as be from an allowed IP.
	 */
	public static boolean isAllowed(ServletContext servletContext, ServletRequest request) {
		return
			Boolean.parseBoolean(servletContext.getInitParameter(ENABLE_INIT_PARAM))
			&& isAllowedAddr(request.getRemoteAddr())
		;
	}

	private static String getJdkPath() {
		try {
			String hostname = InetAddress.getLocalHost().getCanonicalHostName();
			if(
				"francis.aoindustries.com".equals(hostname)
				|| "freedom.aoindustries.com".equals(hostname)
			) return "/opt/jdk1.8.0-i686";
		} catch(UnknownHostException e) {
			// Fall-through to default 64-bit
		}
		return "/opt/jdk1.8.0";
	}

	public static boolean isWindows() {
		String osName = System.getProperty("os.name");
		return osName!=null && osName.toLowerCase(Locale.ROOT).contains("windows");
	}

	/**
	 * Additional file openers may be registered to the application context.
	 */
	public static interface FileOpener {
		/**
		 * Gets the command that will open the given file.
		 *
		 * @return  The command or null to fall-through to default behavior.
		 */
		String[] getCommand(java.io.File resourceFile) throws IOException;
	}

	private static final Object fileOpenersLock = new Object();

	/**
	 * Registers a file opener.
	 * 
	 * @param  extensions  The simple extensions, in lowercase, not including the dot, such as "dia"
	 */
	public static void addFileOpener(ServletContext servletContext, FileOpener fileOpener, String ... extensions) {
		synchronized(fileOpenersLock) {
			@SuppressWarnings("unchecked")
			Map<String,FileOpener> fileOpeners = (Map<String,FileOpener>)servletContext.getAttribute(FILE_OPENERS_REQUEST_ATTRIBUTE_NAME);
			if(fileOpeners == null) {
				fileOpeners = new HashMap<String,FileOpener>();
				servletContext.setAttribute(FILE_OPENERS_REQUEST_ATTRIBUTE_NAME, fileOpeners);
			}
			for(String extension : extensions) {
				if(fileOpeners.containsKey(extension)) throw new IllegalStateException("File opener already registered: " + extension);
				fileOpeners.put(extension, fileOpener);
			}
		}
	}

	/**
	 * Removes file openers.
	 * 
	 * @param  extensions  The simple extensions, in lowercase, not including the dot, such as "dia"
	 */
	public static void removeFileOpener(ServletContext servletContext, String ... extensions) {
		synchronized(fileOpenersLock) {
			@SuppressWarnings("unchecked")
			Map<String,FileOpener> fileOpeners = (Map<String,FileOpener>)servletContext.getAttribute(FILE_OPENERS_REQUEST_ATTRIBUTE_NAME);
			if(fileOpeners != null) {
				for(String extension : extensions) {
					fileOpeners.remove(extension);
				}
				if(fileOpeners.isEmpty()) {
					servletContext.removeAttribute(FILE_OPENERS_REQUEST_ATTRIBUTE_NAME);
				}
			}
		}
	}

	public static void openFile(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		String book,
		final String path
	) throws ServletException, IOException, SkipPageException {
		// Only allow from localhost and when open enabled
		if(!isAllowed(servletContext, request)) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN);
			throw new SkipPageException();
		} else {
			String[] command;
			java.io.File resourceFile = PageRefResolver.getPageRef(servletContext, request, book, path).getResourceFile(true, true);
			if(resourceFile.isDirectory()) {
				command = new String[] {
					// TODO: What is good windows path?
					//isWindows()
					//	? "C:\\Program Files (x86)\\OpenOffice 4\\program\\swriter.exe"
					"/usr/bin/konqueror",
					resourceFile.getCanonicalPath()
				};
			} else {
				// Open the file with the appropriate application based on extension
				String extension = FileUtils.getExtension(resourceFile.getName()).toLowerCase(Locale.ROOT);
				// Check registered file openers first
				FileOpener fileOpener;
				synchronized(fileOpenersLock) {
					@SuppressWarnings("unchecked")
					Map<String,FileOpener> fileOpeners = (Map<String,FileOpener>)servletContext.getAttribute(FILE_OPENERS_REQUEST_ATTRIBUTE_NAME);
					if(fileOpeners != null) {
						fileOpener = fileOpeners.get(extension);
					} else {
						fileOpener = null;
					}
				}
				if(fileOpener != null) {
					command = fileOpener.getCommand(resourceFile);
				} else {
					// Use default behavior
					// Java 1.7: switch(extension)
					if(
						"gif".equals(extension)
						|| "jpg".equals(extension)
						|| "jpeg".equals(extension)
						|| "png".equals(extension)
					) {
						command = new String[] {
							isWindows()
								? "C:\\Program Files (x86)\\OpenOffice 4\\program\\swriter.exe"
								: "/usr/bin/gwenview",
							resourceFile.getCanonicalPath()
						};
					} else if(
						"doc".equals(extension)
						|| "odt".equals(extension)
					) {
						command = new String[] {
							isWindows()
								? "C:\\Program Files (x86)\\OpenOffice 4\\program\\swriter.exe"
								: "/usr/bin/libreoffice",
							"--writer",
							resourceFile.getCanonicalPath()
						};
					} else if(
						"csv".equals(extension)
						|| "ods".equals(extension)
						|| "sxc".equals(extension)
						|| "xls".equals(extension)
					) {
						command = new String[] {
							isWindows()
								? "C:\\Program Files (x86)\\OpenOffice 4\\program\\scalc.exe"
								: "/usr/bin/libreoffice",
							"--calc",
							resourceFile.getCanonicalPath()
						};
					} else if(
						"pdf".equals(extension)
					) {
						command = new String[] {
							isWindows()
								? "C:\\Program Files (x86)\\Adobe\\Reader 11.0\\Reader\\AcroRd32.exe"
								: "/usr/bin/okular",
							resourceFile.getCanonicalPath()
						};
					//} else if(
					//	"sh".equals(extension)
					//) {
						//command = new String[] {
						//	"/usr/bin/kwrite",
						//	resourceFile.getCanonicalPath()
						//};
					} else if(
						"java".equals(extension)
						|| "jsp".equals(extension)
						|| "sh".equals(extension)
						|| "txt".equals(extension)
						|| "xml".equals(extension)
					) {
						if(isWindows()) {
							command = new String[] {
								"C:\\Program Files\\NetBeans 7.4\\bin\\netbeans64.exe",
								"--open",
								resourceFile.getCanonicalPath()
							};
						} else {
							command = new String[] {
								//"/usr/bin/kwrite",
								"/opt/netbeans-8.0.2/bin/netbeans",
								"--jdkhome",
								getJdkPath(),
								"--open",
								resourceFile.getCanonicalPath()
							};
						}
					} else if(
						"zip".equals(extension)
					) {
						if(isWindows()) {
							command = new String[] {
								resourceFile.getCanonicalPath()
							};
						} else {
							command = new String[] {
								"/usr/bin/konqueror",
								resourceFile.getCanonicalPath()
							};
						}
					} else if(
						"mp3".equals(extension)
						|| "wma".equals(extension)
					) {
						command = new String[] {
							isWindows()
								? "C:\\Program Files\\VideoLAN\\VLC.exe"
								: "/usr/bin/vlc",
							resourceFile.getCanonicalPath()
						};
					} else {
						throw new IllegalArgumentException("Unsupprted file type by extension: " + extension);
					}
				}
			}
			// Start the process
			final Process process = Runtime.getRuntime().exec(command);
			// Result is watched in the background only
			// Java 1.8: Lambda
			new Thread(
				new Runnable() {
					@Override
					public void run() {
						try {
							final ProcessResult result = ProcessResult.getProcessResult(process);
							int exitVal = result.getExitVal();
							if(exitVal != 0) {
								logger.log(Level.SEVERE, "Non-zero exit status from \"{0}\": {1}", new Object[]{path, exitVal});
							}
							String stdErr = result.getStderr();
							if(!stdErr.isEmpty()) {
								logger.log(Level.SEVERE, "Standard error from \"{0}\":\n{1}", new Object[]{path, stdErr});
							}
							if(logger.isLoggable(Level.INFO)) {
								String stdOut = result.getStdout();
								if(!stdOut.isEmpty()) {
									logger.log(Level.INFO, "Standard output from \"{0}\":\n{1}", new Object[]{path, stdOut});
								}
							}
						} catch(IOException e) {
							logger.log(Level.SEVERE, null, e);
						}
					}
				}
			).start();
		}
	}

	/**
	 * Make no instances.
	 */
	private OpenFile() {
	}
}
