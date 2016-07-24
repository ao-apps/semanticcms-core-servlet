/*
 * ao-web-page-servlet - Java API for modeling web page content and relationships in a Servlet environment.
 * Copyright (C) 2014, 2015, 2016  AO Industries, Inc.
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

import com.aoindustries.servlet.http.Dispatcher;
import com.aoindustries.util.AoCollections;
import com.aoindustries.util.PropertiesUtils;
import com.aoindustries.util.StringUtility;
import com.aoindustries.util.WrappedException;
import com.aoindustries.web.page.Book;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.http.HttpServletRequest;

/**
 * Exposes all the configured Books as an application-scope map "books".
 */
public class BooksContextListener implements ServletContextListener {

	private static final String BOOKS_ATTRIBUTE_NAME = "books";
	private static final String MISSING_BOOKS_ATTRIBUTE_NAME = "missingBooks";

	@Override
	public void contextInitialized(ServletContextEvent event) {
		try {
			ServletContext servletContext = event.getServletContext();
			// Load books
			if(servletContext.getAttribute(BOOKS_ATTRIBUTE_NAME)!=null) throw new IllegalStateException("Application-scope attribute already present: " + BOOKS_ATTRIBUTE_NAME);
			Map<String,Book> books = new LinkedHashMap<>();
			for(String name : StringUtility.splitStringCommaSpace(servletContext.getInitParameter("books"))) {
				if(!name.isEmpty()) {
					String cvsworkDirectoryAttribute = "book." + name + ".cvsworkDirectory";
					String cvsworkDirectory = servletContext.getInitParameter(cvsworkDirectoryAttribute);
					if(cvsworkDirectory == null) throw new IllegalStateException("Required context parameter not present: " + cvsworkDirectoryAttribute);
					books.put(
						name,
						new Book(
							name,
							cvsworkDirectory,
							PropertiesUtils.loadFromResource(servletContext, ("/".equals(name) ? "" : name) + "/book.properties")
						)
					);
				}
			}
			servletContext.setAttribute(
				BOOKS_ATTRIBUTE_NAME,
				AoCollections.optimalUnmodifiableMap(books)
			);
			// Load missingBooks
			if(servletContext.getAttribute(MISSING_BOOKS_ATTRIBUTE_NAME)!=null) throw new IllegalStateException("Application-scope attribute already present: " + MISSING_BOOKS_ATTRIBUTE_NAME);
			Set<String> missingBooks = new LinkedHashSet<>();
			for(String name : StringUtility.splitStringCommaSpace(servletContext.getInitParameter("missingBooks"))) {
				if(!name.isEmpty()) {
					missingBooks.add(name);
				}
			}
			servletContext.setAttribute(
				MISSING_BOOKS_ATTRIBUTE_NAME,
				AoCollections.optimalUnmodifiableSet(missingBooks)
			);
		} catch(IOException e) {
			throw new WrappedException(e);
		}
	}

	public static Map<String,Book> getBooks(ServletContext servletContext) {
		@SuppressWarnings("unchecked")
		Map<String,Book> books = (Map)servletContext.getAttribute(BOOKS_ATTRIBUTE_NAME);
		if(books == null) throw new IllegalStateException("Application-scope attribute not found: " + BOOKS_ATTRIBUTE_NAME);
		return books;
	}

	public static Set<String> getMissingBooks(ServletContext servletContext) {
		@SuppressWarnings("unchecked")
		Set<String> missingBooks = (Set)servletContext.getAttribute(MISSING_BOOKS_ATTRIBUTE_NAME);
		if(missingBooks == null) throw new IllegalStateException("Application-scope attribute not found: " + MISSING_BOOKS_ATTRIBUTE_NAME);
		return missingBooks;
	}

	@Override
	public void contextDestroyed(ServletContextEvent event) {
		ServletContext servletContext = event.getServletContext();
		servletContext.removeAttribute(BOOKS_ATTRIBUTE_NAME);
	}

	/**
	 * Gets the book for the provided context-relative servlet path or <code>null</code> if no book configured at that path.
	 * The book with the longest prefix match is used.
	 * The servlet path must begin with a slash (/).
	 */
	public static Book getBook(ServletContext servletContext, String servletPath) {
		if(servletPath.charAt(0) != '/') throw new IllegalArgumentException("Invalid servletPath: " + servletPath);
		Book longestPrefixBook = null;
		int longestPrefixLen = -1;
		for(Book book : getBooks(servletContext).values()) {
			String prefix = book.getPathPrefix();
			int prefixLen = prefix.length();
			if(
				prefixLen > longestPrefixLen
				&& servletPath.startsWith(prefix)
			) {
				longestPrefixBook = book;
				longestPrefixLen = prefixLen;
			}
		}
		return longestPrefixBook;
	}

	/**
	 * Gets the book for the provided request or <code>null</code> if no book configured at the current request path.
	 */
	public static Book getBook(ServletContext servletContext, HttpServletRequest request) {
		return getBook(servletContext, Dispatcher.getCurrentPagePath(request));
	}
}
