/*
 * semanticcms-core-servlet - Java API for modeling web page content and relationships in a Servlet environment.
 * Copyright (C) 2013, 2014, 2015, 2016, 2017, 2018  AO Industries, Inc.
 *     support@aoindustries.com
 *     7262 Bull Pen Cir
 *     Mobile, AL 36695
 *
 * This file is part of semanticcms-core-servlet.
 *
 * semanticcms-core-servlet is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * semanticcms-core-servlet is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with semanticcms-core-servlet.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.semanticcms.core.servlet;

import com.aoindustries.io.buffer.BufferResult;
import com.aoindustries.io.buffer.BufferWriter;
import com.aoindustries.io.buffer.EmptyResult;
import com.aoindustries.lang.LocalizedIllegalStateException;
import com.aoindustries.lang.NotImplementedException;
import com.aoindustries.servlet.http.NullHttpServletResponseWrapper;
import com.aoindustries.taglib.AutoEncodingBufferedTag;
import com.semanticcms.core.model.PageRef;
import com.semanticcms.core.servlet.impl.PageImpl;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import javax.servlet.jsp.SkipPageException;
import org.joda.time.ReadableDateTime;

public class Page {

	private final ServletContext servletContext;
	private final HttpServletRequest request;
	private final HttpServletResponse response;

	private PageRef pageRef;

	private ReadableDateTime dateCreated;
	private ReadableDateTime datePublished;
	private ReadableDateTime dateModified;
	private ReadableDateTime dateReviewed;

	private final String title;

	private String shortTitle;
	private String description;
	private String keywords;
	private Boolean allowRobots;
	private Boolean toc;
	private int tocLevels = com.semanticcms.core.model.Page.DEFAULT_TOC_LEVELS;
	private boolean allowParentMismatch;
	private boolean allowChildMismatch;
	private Map<String,Object> properties;

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

	public Page pageRef(PageRef pageRef) {
		this.pageRef = pageRef;
		return this;
	}

	public Page dateCreated(ReadableDateTime dateCreated) {
		this.dateCreated = dateCreated;
		return this;
	}

	public Page datePublished(ReadableDateTime datePublished) {
		this.datePublished = datePublished;
		return this;
	}

	public Page dateModified(ReadableDateTime dateModified) {
		this.dateModified = dateModified;
		return this;
	}

	public Page dateReviewed(ReadableDateTime dateReviewed) {
		this.dateReviewed = dateReviewed;
		return this;
	}

	public Page shortTitle(String shortTitle) {
		this.shortTitle = shortTitle;
		return this;
	}

	public Page description(String description) {
		this.description = description;
		return this;
	}

	public Page keywords(String keywords) {
		this.keywords = keywords;
		return this;
	}

	public Page allowRobots(Boolean allowRobots) {
		this.allowRobots = allowRobots;
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

	/**
	 * Adds a property to the page.
	 *
	 * @throws  IllegalStateException  if the property with the given name has already been set
	 */
	public Page property(String name, Object value) throws IllegalStateException {
		if(properties == null) {
			properties = new LinkedHashMap<String,Object>();
		} else if(properties.containsKey(name)) {
			throw new LocalizedIllegalStateException(
				ApplicationResources.accessor,
				"error.duplicatePageProperty",
				name
			);
		}
		properties.put(name, value);
		return this;
	}

	public static interface Body {
		void doBody(HttpServletRequest req, HttpServletResponse resp, com.semanticcms.core.model.Page page) throws ServletException, IOException, SkipPageException;
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
	public void invoke(final Body body) throws ServletException, IOException, SkipPageException {
		PageRef pr = pageRef;
		if(pr == null) pr = PageRefResolver.getCurrentPageRef(servletContext, request);
		PageImpl.doPageImpl(
			servletContext,
			request,
			response,
			pr,
			dateCreated,
			datePublished,
			dateModified,
			dateReviewed,
			title,
			shortTitle,
			description,
			keywords,
			allowRobots,
			toc,
			tocLevels,
			allowParentMismatch,
			allowChildMismatch,
			properties,
			body == null
				? null
				// Lamdba version not working with generic exceptions:
				// discard -> body.doBody(request, discard ? new NullHttpServletResponseWrapper(response) : response)
				: new PageImpl.PageImplBody<ServletException>() {
					@Override
					public BufferResult doBody(boolean discard, final com.semanticcms.core.model.Page page) throws ServletException, IOException, SkipPageException {
						if(discard) {
							final HttpServletResponse newResponse = new NullHttpServletResponseWrapper(response);
							// Set PageContext
							PageContext.newPageContextSkip(
								servletContext,
								request,
								newResponse,
								// Java 1.8: () -> body.doBody(request, newResponse, page)
								new PageContext.PageContextRunnableSkip() {
									@Override
									public void run() throws ServletException, IOException, SkipPageException {
										body.doBody(request, newResponse, page);
									}
								}
							);
							return EmptyResult.getInstance();
						} else {
							BufferWriter capturedOut = AutoEncodingBufferedTag.newBufferWriter(request);
							try {
								final PrintWriter capturedPW = new PrintWriter(capturedOut);
								try {
									final HttpServletResponse newResponse = new HttpServletResponseWrapper(response) {
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
										// Java 1.8: () -> body.doBody(request, newResponse, page)
										new PageContext.PageContextRunnableSkip() {
											@Override
											public void run() throws ServletException, IOException, SkipPageException {
												body.doBody(request, newResponse, page);
											}
										}
									);
									if(capturedPW.checkError()) throw new IOException("Error on capturing PrintWriter");
								} finally {
									capturedPW.close();
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
	 * @see  #invoke(com.semanticcms.core.servlet.Page.Body) 
	 */
	public void invoke() throws ServletException, IOException, SkipPageException {
		invoke((Body)null);
	}

	public static interface PageContextBody {
		void doBody(com.semanticcms.core.model.Page page) throws ServletException, IOException, SkipPageException;
	}

	/**
	 * @see  #invoke(com.semanticcms.core.servlet.Page.Body) 
	 */
	public void invoke(final PageContextBody body) throws ServletException, IOException, SkipPageException {
		invoke(
			body == null
				? null
				// Java 1.8: (req, resp, page) -> body.doBody(page)
				: new Body() {
					@Override
					public void doBody(HttpServletRequest req, HttpServletResponse resp, com.semanticcms.core.model.Page page) throws ServletException, IOException, SkipPageException {
						body.doBody(page);
					}
				}
		);
	}

	public static interface PageContextNoPageBody {
		void doBody() throws ServletException, IOException, SkipPageException;
	}

	/**
	 * @see  #invoke(com.semanticcms.core.servlet.Page.Body) 
	 */
	public void invoke(final PageContextNoPageBody body) throws ServletException, IOException, SkipPageException {
		invoke(
			body == null
				? null
				// Java 1.8: (req, resp, page) -> body.doBody()
				: new Body() {
					@Override
					public void doBody(HttpServletRequest req, HttpServletResponse resp, com.semanticcms.core.model.Page page) throws ServletException, IOException, SkipPageException {
						body.doBody();
					}
				}
		);
	}
}
