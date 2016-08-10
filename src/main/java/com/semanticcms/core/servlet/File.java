/*
 * semanticcms-core-servlet - Java API for modeling web page content and relationships in a Servlet environment.
 * Copyright (C) 2013, 2014, 2015, 2016  AO Industries, Inc.
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

import com.aoindustries.servlet.http.NullHttpServletResponseWrapper;
import com.semanticcms.core.servlet.impl.FileImpl;
import java.io.IOException;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.jsp.SkipPageException;

public class File {

	private final ServletContext servletContext;
	private final HttpServletRequest request;
	private final HttpServletResponse response;
	private final String path;

	private String book;
	private boolean hidden;

	public File(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		String path
	) {
		this.servletContext = servletContext;
		this.request = request;
		this.response = response;
		this.path = path;
	}

	public File(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		String book,
		String path
	) {
		this(servletContext, request, response, path);
		this.book = book;
	}

	/**
	 * Creates a new file in the current page context.
	 *
	 * @see  PageContext
	 */
	public File(String path) {
		this(
			PageContext.getServletContext(),
			PageContext.getRequest(),
			PageContext.getResponse(),
			path
		);
	}

	/**
	 * Creates a new file in the current page context.
	 *
	 * @see  PageContext
	 */
	public File(String book, String path) {
		this(path);
		this.book = book;
	}

	public File book(String book) {
		this.book = book;
		return this;
	}

	public File hidden(boolean hidden) {
		this.hidden = hidden;
		return this;
	}

	public static interface Body {
		void doBody(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException, SkipPageException;
	}

	/**
	 * <p>
	 * Also establishes a new {@link PageContext}.
	 * </p>
	 *
	 * @see  PageContext
	 */
	public void invoke(final Body body) throws ServletException, IOException, SkipPageException {
		FileImpl.writeFileImpl(
			servletContext,
			request,
			response,
			response.getWriter(),
			book,
			path,
			hidden,
			body == null
				? null
				// Lamdba version not working with generic exceptions:
				// discard -> body.doBody(request, discard ? new NullHttpServletResponseWrapper(response) : response)
				: new FileImpl.FileImplBody<ServletException>() {
					@Override
					public void doBody(boolean discard) throws ServletException, IOException, SkipPageException {
						if(discard) {
							final HttpServletResponse newResponse = new NullHttpServletResponseWrapper(response);
							// Set PageContext
							PageContext.newPageContextSkip(
								servletContext,
								request,
								newResponse,
								// Java 1.8: () -> body.doBody(request, newResponse)
								new PageContext.PageContextCallableSkip() {
									@Override
									public void call() throws ServletException, IOException, SkipPageException {
										body.doBody(request, newResponse);
									}
								}
							);
						} else {
							body.doBody(request, response);
						}
					}
				}
		);
	}

	/**
	 * @see  #invoke(com.aoindustries.web.page.servlet.File.Body) 
	 */
	public void invoke() throws ServletException, IOException, SkipPageException {
		invoke((Body)null);
	}

	public static interface PageContextBody {
		void doBody() throws ServletException, IOException, SkipPageException;
	}

	/**
	 * @see  #invoke(com.aoindustries.web.page.servlet.File.Body) 
	 */
	public void invoke(final PageContextBody body) throws ServletException, IOException, SkipPageException {
		invoke(
			body == null
				? null
				// Java 1.8: (req, resp) -> body.doBody()
				: new Body() {
					@Override
					public void doBody(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException, SkipPageException {
						body.doBody();
					}
				}
		);
	}
}
