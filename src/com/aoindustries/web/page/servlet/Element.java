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

import com.aoindustries.io.buffer.AutoTempFileWriter;
import com.aoindustries.io.buffer.BufferWriter;
import com.aoindustries.io.buffer.SegmentedWriter;
import com.aoindustries.lang.NotImplementedException;
import com.aoindustries.servlet.filter.TempFileContext;
import com.aoindustries.servlet.http.NullHttpServletResponseWrapper;
import com.aoindustries.web.page.ElementWriter;
import com.aoindustries.web.page.Node;
import com.aoindustries.web.page.NodeBodyWriter;
import com.aoindustries.web.page.Page;
import java.io.IOException;
import java.io.PrintWriter;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import javax.servlet.jsp.SkipPageException;

/**
 * The base for capturing elements.
 */
abstract public class Element<E extends com.aoindustries.web.page.Element> implements ElementWriter {

	private final ServletContext servletContext;
	private final HttpServletRequest request;
	private final HttpServletResponse response;
	protected final E element;

	protected Element(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		E element
	) {
		this.servletContext = servletContext;
		this.request = request;
		this.response = response;
		this.element = element;
	}

	public Element<E> id(String id) {
		this.element.setId(id);
		return this;
	}

	public static interface Body<E extends com.aoindustries.web.page.Element> {
		void doBody(HttpServletRequest req, HttpServletResponse resp, E element) throws ServletException, IOException, SkipPageException;
	}

	/**
	 * <p>
	 * Adds this element to the current page, if part of a page.
	 * Sets this element as the current element.
	 * Then, if not capturing or capturing META or higher, calls {@link #doBody}
	 * </p>
	 * <p>
	 * Also establishes a new {@link PageContext}.
	 * </p>
	 *
	 * @see  PageContext
	 */
	public void invoke(Body<? super E> body) throws ServletException, IOException, SkipPageException {
		// Get the current capture state
		CaptureLevel captureLevel = CaptureLevel.getCaptureLevel(request);
		if(captureLevel.compareTo(CaptureLevel.META) >= 0) {
			// Set currentNode
			Node parentNode = CurrentNode.getCurrentNode(request);
			CurrentNode.setCurrentNode(request, element);
			try {
				// Find the optional parent page
				Page currentPage = CurrentPage.getCurrentPage(request);
				if(currentPage != null) currentPage.addElement(element);

				Long elementKey;
				if(parentNode != null) elementKey = parentNode.addChildElement(element, this);
				else elementKey = null;
				// Freeze element once body done
				try {
					doBody(request, response, captureLevel, body);
				} finally {
					// Note: Page freezes all of its elements after setting missing ids
					if(currentPage == null || element.getId() != null) {
						element.freeze();
					}
				}
				PrintWriter out = response.getWriter();
				if(elementKey == null) {
					// Write now
					writeTo(out, new ServletElementContext(servletContext, request, response));
				} else {
					// Write an element marker instead
					// TODO: Do not write element marker for empty elements, such as passwordTable at http://localhost:8080/docs/ao/infrastructure/ao/regions/mobile-al/workstations/francis.aoindustries.com/
					NodeBodyWriter.writeElementMarker(elementKey, out);
				}
			} finally {
				// Restore previous currentNode
				CurrentNode.setCurrentNode(request, parentNode);
			}
		}
	}

	/**
	 * @see  #invoke(com.aoindustries.web.page.servlet.Element.Body) 
	 */
	public void invoke() throws ServletException, IOException, SkipPageException {
		invoke((Body)null);
	}

	public static interface PageContextBody<E extends com.aoindustries.web.page.Element> {
		void doBody(E element) throws ServletException, IOException, SkipPageException;
	}

	/**
	 * @see  #invoke(com.aoindustries.web.page.servlet.Element.Body) 
	 */
	public void invoke(PageContextBody<? super E> body) throws ServletException, IOException, SkipPageException {
		invoke(
			body == null
				? null
				: (req, resp, e) -> body.doBody(e)
		);
	}

	public static interface PageContextNoElementBody {
		void doBody() throws ServletException, IOException, SkipPageException;
	}

	/**
	 * @see  #invoke(com.aoindustries.web.page.servlet.Element.Body) 
	 */
	public void invoke(PageContextNoElementBody body) throws ServletException, IOException, SkipPageException {
		invoke(
			body == null
				? null
				: (req, resp, e) -> body.doBody()
		);
	}

	/**
	 * Only called at capture level of META and higher.
	 */
	protected void doBody(
		HttpServletRequest request,
		HttpServletResponse response,
		CaptureLevel captureLevel,
		Body<? super E> body
	) throws ServletException, IOException, SkipPageException {
		if(body != null) {
			if(captureLevel == CaptureLevel.BODY) {
				// Invoke tag body, capturing output
				BufferWriter capturedOut = new SegmentedWriter();
				try {
					// Enable temp files if temp file context active
					capturedOut = TempFileContext.wrapTempFileList(capturedOut, request, AutoTempFileWriter::new);
					try (PrintWriter capturedPW = new PrintWriter(capturedOut)) {
						HttpServletResponse newResponse = new HttpServletResponseWrapper(response) {
							@Override
							public PrintWriter getWriter() throws IOException {
								return capturedPW;
							}
							@Override
							public ServletOutputStream getOutputStream() {
								throw new NotImplementedException();
							}
						};
						// Set PageContext
						PageContext.newPageContextSkip(
							servletContext,
							request,
							newResponse,
							() -> body.doBody(request, newResponse, element)
						);
						if(capturedPW.checkError()) throw new IOException("Error on capturing PrintWriter");
					}
				} finally {
					capturedOut.close();
				}
				element.setBody(capturedOut.getResult().trim());
			} else if(captureLevel == CaptureLevel.META) {
				// Invoke body for any meta data, but discard any output
				HttpServletResponse newResponse = new NullHttpServletResponseWrapper(response);
				// Set PageContext
				PageContext.newPageContextSkip(
					servletContext,
					request,
					newResponse,
					() -> body.doBody(request, newResponse, element)
				);
			} else {
				throw new AssertionError();
			}
		}
	}
}
