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
import com.aoindustries.io.buffer.BufferResult;
import com.aoindustries.io.buffer.BufferWriter;
import com.aoindustries.io.buffer.EmptyResult;
import com.aoindustries.io.buffer.SegmentedWriter;
import com.aoindustries.lang.NotImplementedException;
import com.aoindustries.servlet.filter.TempFileContext;
import com.aoindustries.servlet.http.NullHttpServletResponseWrapper;
import com.aoindustries.web.page.servlet.impl.PageImpl;
import java.io.IOException;
import java.io.PrintWriter;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import javax.servlet.jsp.SkipPageException;

public class Page {

	private final ServletContext servletContext;
	private final HttpServletRequest request;
	private final HttpServletResponse response;
	private final String title;

	private String description;
	private String keywords;
	private Boolean toc;
	private int tocLevels = com.aoindustries.web.page.Page.DEFAULT_TOC_LEVELS;
	private boolean allowParentMismatch;
	private boolean allowChildMismatch;

	public Page(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		String title
	) {
		this.servletContext = servletContext;
		this.request = request;
		this.response = response;
		this.title = title;
	}

	/**
	 * Creates a new page in the current page context.
	 *
	 * @see  PageContext
	 */
	public Page(String title) {
		this(
			PageContext.getServletContext(),
			PageContext.getRequest(),
			PageContext.getResponse(),
			title
		);
	}

	public Page description(String description) {
		this.description = description;
		return this;
	}

	public Page keywords(String keywords) {
		this.keywords = keywords;
		return this;
	}

	public Page toc(Boolean toc) {
		this.toc = toc;
		return this;
	}

	public Page tocLevels(int tocLevels) {
		this.tocLevels = tocLevels;
		return this;
	}

	public Page allowParentMismatch(boolean allowParentMismatch) {
		this.allowParentMismatch = allowParentMismatch;
		return this;
	}

	public Page allowChildMismatch(boolean allowChildMismatch) {
		this.allowChildMismatch = allowChildMismatch;
		return this;
	}

	public static interface Body {
		void doBody(HttpServletRequest req, HttpServletResponse resp, com.aoindustries.web.page.Page page) throws ServletException, IOException, SkipPageException;
	}

	/**
	 * <p>
	 * Sets request attribute "page" to the current page, and restores the previous "page"
	 * attribute once completed.
	 * </p>
	 * <p>
	 * Also establishes a new {@link PageContext}.
	 * </p>
	 *
	 * @see  PageContext
	 * @see  PageImpl#PAGE_REQUEST_ATTRIBUTE
	 */
	public void invoke(Body body) throws ServletException, IOException, SkipPageException {
		PageImpl.doPageImpl(
			servletContext,
			request,
			response,
			title,
			description,
			keywords,
			toc,
			tocLevels,
			allowParentMismatch,
			allowChildMismatch,
			body == null
				? null
				// Lamdba version not working with generic exceptions:
				// discard -> body.doBody(request, discard ? new NullHttpServletResponseWrapper(response) : response)
				: new PageImpl.PageImplBody<ServletException>() {
					@Override
					public BufferResult doBody(boolean discard, com.aoindustries.web.page.Page page) throws ServletException, IOException, SkipPageException {
						if(discard) {
							HttpServletResponse newResponse = new NullHttpServletResponseWrapper(response);
							// Set PageContext
							PageContext.newPageContextSkip(
								servletContext,
								request,
								newResponse,
								() -> body.doBody(request, newResponse, page)
							);
							return EmptyResult.getInstance();
						} else {
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
										() -> body.doBody(request, newResponse, page)
									);
									if(capturedPW.checkError()) throw new IOException("Error on capturing PrintWriter");
								}
							} finally {
								capturedOut.close();
							}
							return capturedOut.getResult();
						}
					}
				}
		);
	}

	/**
	 * @see  #invoke(com.aoindustries.web.page.servlet.Page.Body) 
	 */
	public void invoke() throws ServletException, IOException, SkipPageException {
		invoke((Body)null);
	}

	public static interface PageContextBody {
		void doBody(com.aoindustries.web.page.Page page) throws ServletException, IOException, SkipPageException;
	}

	/**
	 * @see  #invoke(com.aoindustries.web.page.servlet.Page.Body) 
	 */
	public void invoke(PageContextBody body) throws ServletException, IOException, SkipPageException {
		invoke(
			body == null
				? null
				: (req, resp, page) -> body.doBody(page)
		);
	}

	public static interface PageContextNoPageBody {
		void doBody() throws ServletException, IOException, SkipPageException;
	}

	/**
	 * @see  #invoke(com.aoindustries.web.page.servlet.Page.Body) 
	 */
	public void invoke(PageContextNoPageBody body) throws ServletException, IOException, SkipPageException {
		invoke(
			body == null
				? null
				: (req, resp, page) -> body.doBody()
		);
	}
}
