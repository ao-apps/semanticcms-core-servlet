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

import com.aoindustries.web.page.Book;
import com.aoindustries.web.page.PageRef;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;

/**
 * The context-relative path to the content root page is configured in the
 * "content.root" context parameter in web.xml.
 * This must begin with a slash (/).
 */
final public class ContentRoot {

	private static final String INIT_PARAM = "content.root";

	/**
	 * Gets the context-relative path to the content root.
	 */
	public static PageRef getContentRoot(ServletContext servletContext) throws ServletException {
		String contentRoot = servletContext.getInitParameter(INIT_PARAM);
		Book book = BooksContextListener.getBook(servletContext, contentRoot);
		if(book == null) throw new ServletException("Book not found: " + contentRoot);
		return new PageRef(
			book,
			contentRoot.substring(book.getPathPrefix().length())
		);
	}

	/**
	 * Make no instances.
	 */
	private ContentRoot() {
	}
}
