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

import com.aoindustries.net.HttpParameters;
import com.aoindustries.servlet.http.NullHttpServletResponseWrapper;
import com.aoindustries.web.page.servlet.impl.LinkImpl;
import java.io.IOException;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.jsp.SkipPageException;

public class Link {

	public static interface LinkBody {
		void doBody(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException, SkipPageException;
	}

	private final ServletContext servletContext;
	private final HttpServletRequest request;
	private final HttpServletResponse response;

	private String book;
	private String page;
	private String element;
	private String view;
	private HttpParameters params;
	private String clazz;

	public Link(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response
	) {
		this.servletContext = servletContext;
		this.request = request;
		this.response = response;
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

	public Link view(String view) {
		this.view = view;
		return this;
	}

	public Link params(HttpParameters params) {
		this.params = params;
		return this;
	}

	public Link clazz(String clazz) {
		this.clazz = clazz;
		return this;
	}

	public void writeLink(
		LinkBody body
	) throws ServletException, IOException, SkipPageException {
		LinkImpl.writeLinkImpl(
			servletContext,
			request,
			response,
			response.getWriter(),
			book,
			page,
			element,
			view,
			params,
			clazz,
			body == null
				? null
				// Lamdba version not working with generic exceptions:
				// discard -> body.doBody(request, discard ? new NullHttpServletResponseWrapper(response) : response)
				: new LinkImpl.LinkImplBody<ServletException>() {
					@Override
					public void doBody(boolean discard) throws ServletException, IOException, SkipPageException {
						body.doBody(
							request,
							discard ? new NullHttpServletResponseWrapper(response) : response
						);
					}
				}
		);
	}

	public void writeLink() throws ServletException, IOException, SkipPageException {
		writeLink(null);
	}
}
