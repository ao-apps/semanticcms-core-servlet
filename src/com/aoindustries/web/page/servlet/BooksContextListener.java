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
import com.aoindustries.web.page.PageRef;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;
import javax.servlet.http.HttpServletRequest;

@WebListener(
	"Exposes all the configured books as an application-scope Map<String,Book> \"" + BooksContextListener.BOOKS_ATTRIBUTE_NAME + "\".\n"
	+ "Exposes all the configured missing book names as an application-scope Set<String> \"" + BooksContextListener.MISSING_BOOKS_ATTRIBUTE_NAME+ "\".\n"
	+ "Exposes the configured root book as an application-scope Book \"" + BooksContextListener.ROOT_BOOK_ATTRIBUTE_NAME+ "\"."
)
public class BooksContextListener implements ServletContextListener {

	static final String BOOKS_ATTRIBUTE_NAME = "books";
	static final String MISSING_BOOKS_ATTRIBUTE_NAME = "missingBooks";
	static final String ROOT_BOOK_ATTRIBUTE_NAME = "rootBook";

	@Override
	public void contextInitialized(ServletContextEvent event) {
		try {
			ServletContext servletContext = event.getServletContext();
			// Load missingBooks
			Set<String> missingBooks = new LinkedHashSet<>();
			for(String name : StringUtility.splitStringCommaSpace(servletContext.getInitParameter(MISSING_BOOKS_ATTRIBUTE_NAME))) {
				if(!name.isEmpty()) {
					missingBooks.add(name);
				}
			}
			// Load books
			String rootBookName = servletContext.getInitParameter(ROOT_BOOK_ATTRIBUTE_NAME);
			if(rootBookName == null || rootBookName.isEmpty()) throw new IllegalStateException('"' + ROOT_BOOK_ATTRIBUTE_NAME + "\" not provided");
			Map<String,Book> books = new LinkedHashMap<>();
			for(String name : StringUtility.splitStringCommaSpace(servletContext.getInitParameter(BOOKS_ATTRIBUTE_NAME))) {
				if(!name.isEmpty()) {
					if(missingBooks.contains(name)) throw new IllegalStateException("Book also listed in \"" + MISSING_BOOKS_ATTRIBUTE_NAME + "\": " + name);
					String cvsworkDirectoryAttribute = "book." + name + ".cvsworkDirectory";
					String cvsworkDirectory = servletContext.getInitParameter(cvsworkDirectoryAttribute);
					if(cvsworkDirectory == null) throw new IllegalStateException("Required context parameter not present: " + cvsworkDirectoryAttribute);
					List<PageRef> parentPages = new ArrayList<>();
					for(int i=1; i<Integer.MAX_VALUE; i++) {
						String parentBookNameAttribute = "book." + name + ".parent." + i + ".book";
						String parentBookName = servletContext.getInitParameter(parentBookNameAttribute);
						String parentPageAttribute = "book." + name + ".parent." + i + ".page";
						String parentPage = servletContext.getInitParameter(parentPageAttribute);
						// Stop on the first not found
						if(parentBookName == null && parentPage == null) break;
						if(parentBookName == null) throw new IllegalArgumentException("parent book required when parent page provided: " + parentPageAttribute + " = " + parentPage);
						if(parentPage == null) throw new IllegalArgumentException("parent page required when parent book provided: " + parentBookNameAttribute + " = " + parentBookName);
						if(missingBooks.contains(parentBookName)) throw new IllegalStateException("parent book may not be a \"" + MISSING_BOOKS_ATTRIBUTE_NAME + "\": " + parentBookNameAttribute + " = " + parentBookName);
						Book parentBook = books.get(parentBookName);
						if(parentBook == null) throw new IllegalStateException("parent book not found (loading order currently matters): " + parentBookNameAttribute + " = " + parentBookName);
						parentPages.add(new PageRef(parentBook, parentPage));
					}
					if(name.equals(rootBookName)) {
						if(!parentPages.isEmpty()) {
							throw new IllegalStateException('"' + ROOT_BOOK_ATTRIBUTE_NAME + "\" may not have any parents: " + rootBookName);
						}
					} else {
						if(parentPages.isEmpty()) {
							throw new IllegalStateException("Non-root books must have at least one parent: " + name);
						}
					}
					books.put(
						name,
						new Book(
							name,
							cvsworkDirectory,
							parentPages,
							PropertiesUtils.loadFromResource(servletContext, ("/".equals(name) ? "" : name) + "/book.properties")
						)
					);
				}
			}
			// Load rootBook
			if(missingBooks.contains(rootBookName)) throw new IllegalStateException('"' + ROOT_BOOK_ATTRIBUTE_NAME + "\" may not be a \"" + MISSING_BOOKS_ATTRIBUTE_NAME + "\": " + rootBookName);
			Book rootBook = books.get(rootBookName);
			if(rootBook == null) throw new IllegalStateException('"' + ROOT_BOOK_ATTRIBUTE_NAME + "\" is not found in \"" + BOOKS_ATTRIBUTE_NAME + "\": " + rootBookName);
			// Successful startup
			if(servletContext.getAttribute(BOOKS_ATTRIBUTE_NAME)!=null) throw new IllegalStateException("Application-scope attribute already present: " + BOOKS_ATTRIBUTE_NAME);
			servletContext.setAttribute(BOOKS_ATTRIBUTE_NAME, AoCollections.optimalUnmodifiableMap(books));
			if(servletContext.getAttribute(MISSING_BOOKS_ATTRIBUTE_NAME)!=null) throw new IllegalStateException("Application-scope attribute already present: " + MISSING_BOOKS_ATTRIBUTE_NAME);
			servletContext.setAttribute(MISSING_BOOKS_ATTRIBUTE_NAME, AoCollections.optimalUnmodifiableSet(missingBooks));
			if(servletContext.getAttribute(ROOT_BOOK_ATTRIBUTE_NAME)!=null) throw new IllegalStateException("Application-scope attribute already present: " + ROOT_BOOK_ATTRIBUTE_NAME);
			servletContext.setAttribute(ROOT_BOOK_ATTRIBUTE_NAME, rootBook);
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

	/**
	 * Gets the root book as configured in web.xml
	 */
	public static Book getRootBook(ServletContext servletContext) throws IllegalStateException {
		Book rootBook = (Book)servletContext.getAttribute(ROOT_BOOK_ATTRIBUTE_NAME);
		if(rootBook == null) throw new IllegalStateException("Application-scope attribute not found: " + ROOT_BOOK_ATTRIBUTE_NAME);
		return rootBook;
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

	@Override
	public void contextDestroyed(ServletContextEvent event) {
		ServletContext servletContext = event.getServletContext();
		servletContext.removeAttribute(BOOKS_ATTRIBUTE_NAME);
	}
}
