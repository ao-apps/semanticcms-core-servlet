/*
 * semanticcms-core-servlet - Java API for modeling web page content and relationships in a Servlet environment.
 * Copyright (C) 2013, 2014, 2015, 2016, 2017  AO Industries, Inc.
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

import com.aoindustries.net.HttpParameters;
import com.aoindustries.servlet.http.NullHttpServletResponseWrapper;
import com.semanticcms.core.servlet.impl.LinkImpl;
import java.io.IOException;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.jsp.SkipPageException;

public class Link {

	private final ServletContext servletContext;
	private final HttpServletRequest request;
	private final HttpServletResponse response;

	private String domain;
	private String book;
	private String page;
	private String element;
	private boolean allowGeneratedElement;
	private String anchor;
	private String view = SemanticCMS.DEFAULT_VIEW_NAME;
	private boolean small;
	private HttpParameters params;
	private Object clazz;

	public Link(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response
	) {
		this.servletContext = servletContext;
		this.request = request;
		this.response = response;
	}

	public Link(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		String page
	) {
		this(servletContext, request, response);
		this.page = page;
	}

	public Link(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		String book,
		String page
	) {
		this(servletContext, request, response, page);
		this.book = book;
	}

	public Link(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		String domain,
		String book,
		String page
	) {
		this(servletContext, request, response, book, page);
		this.domain = domain;
	}

	/**
	 * Creates a new link in the current page context.
	 *
	 * @see  PageContext
	 */
	public Link() {
		this(
			PageContext.getServletContext(),
			PageContext.getRequest(),
			PageContext.getResponse()
		);
	}

	/**
	 * Creates a new link in the current page context.
	 *
	 * @see  PageContext
	 */
	public Link(String page) {
		this();
		this.page = page;
	}

	/**
	 * Creates a new link in the current page context.
	 *
	 * @see  PageContext
	 */
	public Link(String book, String page) {
		this(page);
		this.book = book;
	}

	/**
	 * Creates a new link in the current page context.
	 *
	 * @see  PageContext
	 */
	public Link(String domain, String book, String page) {
		this(book, page);
		this.domain = domain;
	}

	public Link domain(String domain) {
		this.domain = domain;
		return this;
	}

	public Link book(String book) {
		this.book = book;
		return this;
	}

	public Link page(String page) {
		this.page = page;
		return this;
	}

	public Link element(String element) {
		this.element = element;
		return this;
	}

	public Link allowGeneratedElement(boolean allowGeneratedElement) {
		this.allowGeneratedElement = allowGeneratedElement;
		return this;
	}

	public Link anchor(String anchor) {
		this.anchor = anchor;
		return this;
	}

	public Link view(String view) {
		this.view = view;
		return this;
	}

	/**
	 * <p>
	 * When false, the default, will generate a &lt;a&gt; tag around the entire body.
	 * Otherwise, will generate a &lt;span&gt; instead, with a small link added to
	 * the end of the body.
	 * </p>
	 * <p>
	 * Use of a small link can be helpful for usability, such as when the body is
	 * a piece of information intended for quick copy/paste by the user.
	 * </p>
	 */
	public Link small(boolean small) {
		this.small = small;
		return this;
	}

	public Link params(HttpParameters params) {
		this.params = params;
		return this;
	}

	public Link clazz(Object clazz) {
		this.clazz = clazz;
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
		LinkImpl.writeLinkImpl(
			servletContext,
			request,
			response,
			response.getWriter(),
			domain,
			book,
			page,
			element,
			allowGeneratedElement,
			anchor,
			view,
			small,
			params,
			clazz,
			body == null
				? null
				// Lamdba version not working with generic exceptions:
				// discard -> body.doBody(request, discard ? new NullHttpServletResponseWrapper(response) : response)
				: new LinkImpl.LinkImplBody<ServletException>() {
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
	 * @see  #invoke(com.semanticcms.core.servlet.Link.Body)
	 */
	public void invoke() throws ServletException, IOException, SkipPageException {
		invoke((Body)null);
	}

	public static interface PageContextBody {
		void doBody() throws ServletException, IOException, SkipPageException;
	}

	/**
	 * @see  #invoke(com.semanticcms.core.servlet.Link.Body)
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
