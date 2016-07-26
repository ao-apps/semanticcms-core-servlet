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

import com.aoindustries.web.page.Node;
import com.aoindustries.web.page.PageRef;
import java.io.IOException;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

final public class File {

	public static <E extends Throwable> void writeFile(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		Appendable out,
		String book,
		String path,
		boolean hidden,
		FileImpl.FileBody<E> body
	) throws E, ServletException, IOException {
		// Get the current capture state
		final CaptureLevel captureLevel = CaptureLevel.getCaptureLevel(request);
		if(captureLevel.compareTo(CaptureLevel.META) >= 0) {
			PageRef file = PageRefResolver.getPageRef(servletContext, request, book, path);
			// If we have a parent node, associate this file with the node
			final Node currentNode = CurrentNode.getCurrentNode(request);
			if(currentNode != null && !hidden) currentNode.addFile(file);

			if(captureLevel == CaptureLevel.BODY) {
				// Write a link to the file
				FileImpl.writeFileLink(
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

	/**
	 * Make no instances.
	 */
	private File() {
	}
}
