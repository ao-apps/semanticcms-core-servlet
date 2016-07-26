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
package com.aoindustries.web.page.servlet.impl;

import static com.aoindustries.encoding.JavaScriptInXhtmlAttributeEncoder.encodeJavaScriptInXhtmlAttribute;
import com.aoindustries.encoding.NewEncodingUtils;
import static com.aoindustries.encoding.TextInXhtmlAttributeEncoder.encodeTextInXhtmlAttribute;
import static com.aoindustries.encoding.TextInXhtmlEncoder.encodeTextInXhtml;
import com.aoindustries.net.UrlUtils;
import com.aoindustries.servlet.http.LastModifiedServlet;
import com.aoindustries.util.StringUtility;
import com.aoindustries.web.page.Node;
import com.aoindustries.web.page.PageRef;
import com.aoindustries.web.page.servlet.CaptureLevel;
import com.aoindustries.web.page.servlet.CurrentNode;
import com.aoindustries.web.page.servlet.Headers;
import com.aoindustries.web.page.servlet.OpenFile;
import com.aoindustries.web.page.servlet.PageRefResolver;
import java.io.File;
import java.io.IOException;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.jsp.SkipPageException;

final public class FileImpl {

	public static interface FileImplBody<E extends Throwable> {
		void doBody(boolean discard) throws E, IOException, SkipPageException;
	}

	public static <E extends Throwable> void writeFileImpl(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		Appendable out,
		String book,
		String path,
		boolean hidden,
		FileImplBody<E> body
	) throws E, ServletException, IOException, SkipPageException {
		// Get the current capture state
		final CaptureLevel captureLevel = CaptureLevel.getCaptureLevel(request);
		if(captureLevel.compareTo(CaptureLevel.META) >= 0) {
			PageRef file = PageRefResolver.getPageRef(servletContext, request, book, path);
			// If we have a parent node, associate this file with the node
			final Node currentNode = CurrentNode.getCurrentNode(request);
			if(currentNode != null && !hidden) currentNode.addFile(file);

			if(captureLevel == CaptureLevel.BODY) {
				// Write a link to the file
				writeFileLinkImpl(
					servletContext,
					request,
					response,
					out,
					body,
					file
				);
			} else {
				// Invoke body for any meta data, but discard any output
				if(body != null) body.doBody(true);
			}
		}
	}

	public static <E extends Throwable> void writeFileLinkImpl(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		Appendable out,
		FileImplBody<E> body,
		PageRef file
	) throws E, IOException, SkipPageException {
		// Determine if local file opening is allowed
		final boolean isAllowed = OpenFile.isAllowed(servletContext, request);
		final boolean isExporting = Headers.EXPORTING_HEADER_VALUE.equalsIgnoreCase(request.getHeader(Headers.EXPORTING_HEADER));

		// Find the local file, assuming relative to CVSWORK directory
		File resourceFile = file.getResourceFile(false, true);
		boolean isDirectory;
		if(resourceFile == null) {
			// In other book and not available, assume directory when ends in path separator
			isDirectory = file.getPath().endsWith("/");
		} else {
			// In accessible book, use attributes
			isDirectory = resourceFile.isDirectory();
		}
		out.append("<a");
		if(body == null) {
			out.append(" class=\"");
			out.append(isDirectory ? "ao-web-page-directory-link" : "ao-web-page-file-link");
			out.append('"');
		}
		out.append(" href=\"");
		if(
			isAllowed
			&& resourceFile != null
			&& !isExporting
		) {
			encodeTextInXhtmlAttribute(resourceFile.toURI().toString(), out);
		} else {
			final String urlPath;
			if(
				resourceFile != null
				&& !isDirectory
				// Check for header disabling auto last modified
				&& !"false".equalsIgnoreCase(request.getHeader(LastModifiedServlet.LAST_MODIFIED_HEADER_NAME))
			) {
				// Include last modified on file
				urlPath = request.getContextPath()
					+ file.getBookPrefix()
					+ file.getPath()
					+ "?" + LastModifiedServlet.LAST_MODIFIED_PARAMETER_NAME
					+ "=" + LastModifiedServlet.encodeLastModified(resourceFile.lastModified())
				;
			} else {
				urlPath = request.getContextPath()
					+ file.getBookPrefix()
					+ file.getPath()
				;
			}
			encodeTextInXhtmlAttribute(
				response.encodeURL(
					UrlUtils.encodeUrlPath(
						urlPath,
						response.getCharacterEncoding()
					)
				),
				out
			);
		}
		out.append('"');
		if(
			isAllowed
			&& resourceFile != null
			&& !isExporting
		) {
			out.append(" onclick=\"");
			encodeJavaScriptInXhtmlAttribute("ao_web_page_servlet.openFile(\"", out);
			NewEncodingUtils.encodeTextInJavaScriptInXhtmlAttribute(file.getBook().getName(), out);
			encodeJavaScriptInXhtmlAttribute("\", \"", out);
			NewEncodingUtils.encodeTextInJavaScriptInXhtmlAttribute(file.getPath(), out);
			encodeJavaScriptInXhtmlAttribute("\"); return false;", out);
			out.append('"');
		}
		out.append('>');
		if(body == null) {
			if(resourceFile == null) {
				LinkImpl.writeBrokenPathInXhtml(file, out);
			} else {
				encodeTextInXhtml(resourceFile.getName(), out);
				if(isDirectory) encodeTextInXhtml('/', out);
			}
		} else {
			body.doBody(false);
		}
		out.append("</a>");
		if(body == null && resourceFile != null && !isDirectory) {
			out.append(" (");
			encodeTextInXhtml(StringUtility.getApproximateSize(resourceFile.length()), out);
			out.append(')');
		}
	}

	/**
	 * Make no instances.
	 */
	private FileImpl() {
	}
}
