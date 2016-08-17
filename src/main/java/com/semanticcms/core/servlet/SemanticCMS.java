/*
 * semanticcms-core-servlet - Java API for modeling web page content and relationships in a Servlet environment.
 * Copyright (C) 2014, 2015, 2016  AO Industries, Inc.
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

import com.aoindustries.servlet.http.Dispatcher;
import com.aoindustries.util.PropertiesUtils;
import com.aoindustries.util.WrappedException;
import com.semanticcms.core.model.Book;
import com.semanticcms.core.model.PageRef;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;

/**
 * The SemanticCMS application context.
 * 
 * TODO: Consider custom EL resolver for this variable: http://stackoverflow.com/questions/5016965/how-to-add-a-custom-variableresolver-in-pure-jsp
 */
public class SemanticCMS {

	// <editor-fold defaultstate="collapsed" desc="Singleton Instance (per application)">
	static final String ATTRIBUTE_NAME = "semanticCMS";

	private static final Object instanceLock = new Object();

	/**
	 * Gets the SemanticCMS instance, creating it if necessary.
	 */
	public static SemanticCMS getInstance(ServletContext servletContext) {
		try {
			synchronized(instanceLock) {
				SemanticCMS semanticCMS = (SemanticCMS)servletContext.getAttribute(SemanticCMS.ATTRIBUTE_NAME);
				if(semanticCMS == null) {
					semanticCMS = new SemanticCMS(servletContext);
					servletContext.setAttribute(SemanticCMS.ATTRIBUTE_NAME, semanticCMS);
				}
				return semanticCMS;
			}
		} catch(IOException e) {
			throw new WrappedException(e);
		}
	}

	private SemanticCMS(ServletContext servletContext) throws IOException {
		this.rootBook = initBooks(servletContext);
	}
	// </editor-fold>

	// <editor-fold defaultstate="collapsed" desc="Books">
	private static final String BOOKS_PROPERTIES_RESOURCE = "/WEB-INF/books.properties";

	private static final String BOOKS_ATTRIBUTE_NAME = "books";
	private static final String MISSING_BOOKS_ATTRIBUTE_NAME = "missingBooks";
	private static final String ROOT_BOOK_ATTRIBUTE_NAME = "rootBook";

	private static String getProperty(Properties booksProps, Set<Object> usedKeys, String key) {
		usedKeys.add(key);
		return booksProps.getProperty(key);
	}

	private final Map<String,Book> books = new LinkedHashMap<String,Book>();
	private final Set<String> missingBooks = new LinkedHashSet<String>();
	private final Book rootBook;

	private Book initBooks(ServletContext servletContext) throws IOException {
		Properties booksProps = PropertiesUtils.loadFromResource(servletContext, BOOKS_PROPERTIES_RESOURCE);
		Set<Object> booksPropsKeys = booksProps.keySet();

		// Tracks each properties key used, will throw exception if any key exists in the properties file that is not used
		Set<Object> usedKeys = new HashSet<Object>(booksPropsKeys.size() * 4/3 + 1);

		// Load missingBooks
		for(int missingBookNum=1; missingBookNum<Integer.MAX_VALUE; missingBookNum++) {
			String key =  MISSING_BOOKS_ATTRIBUTE_NAME + "." + missingBookNum;
			String name = getProperty(booksProps, usedKeys, key);
			if(name == null) break;
			if(!missingBooks.add(name)) throw new IllegalStateException(BOOKS_PROPERTIES_RESOURCE + ": Duplicate value for \"" + MISSING_BOOKS_ATTRIBUTE_NAME + "\": " + key + "=" + name);
		}

		// Load books
		String rootBookName = getProperty(booksProps, usedKeys, ROOT_BOOK_ATTRIBUTE_NAME);
		if(rootBookName == null || rootBookName.isEmpty()) throw new IllegalStateException(BOOKS_PROPERTIES_RESOURCE + ": \"" + ROOT_BOOK_ATTRIBUTE_NAME + "\" not found");
		for(int bookNum=1; bookNum<Integer.MAX_VALUE; bookNum++) {
			String name = getProperty(booksProps, usedKeys, "books." + bookNum + ".name");
			if(name == null) break;
			if(missingBooks.contains(name)) throw new IllegalStateException(BOOKS_PROPERTIES_RESOURCE + ": Book also listed in \"" + MISSING_BOOKS_ATTRIBUTE_NAME + "\": " + name);
			String cvsworkDirectoryAttribute = "books." + bookNum + ".cvsworkDirectory";
			String cvsworkDirectory = getProperty(booksProps, usedKeys, cvsworkDirectoryAttribute);
			if(cvsworkDirectory == null) throw new IllegalStateException(BOOKS_PROPERTIES_RESOURCE + ": Required parameter not present: " + cvsworkDirectoryAttribute);
			Set<PageRef> parentPages = new LinkedHashSet<PageRef>();
			for(int parentNum=1; parentNum<Integer.MAX_VALUE; parentNum++) {
				String parentBookNameAttribute = "books." + bookNum + ".parents." + parentNum + ".book";
				String parentBookName = getProperty(booksProps, usedKeys, parentBookNameAttribute);
				String parentPageAttribute = "books." + bookNum + ".parents." + parentNum + ".page";
				String parentPage = getProperty(booksProps, usedKeys, parentPageAttribute);
				// Stop on the first not found
				if(parentBookName == null && parentPage == null) break;
				if(parentBookName == null) throw new IllegalArgumentException(BOOKS_PROPERTIES_RESOURCE + ": parent book required when parent page provided: " + parentPageAttribute + "=" + parentPage);
				if(parentPage == null) throw new IllegalArgumentException(BOOKS_PROPERTIES_RESOURCE + ": parent page required when parent book provided: " + parentBookNameAttribute + "=" + parentBookName);
				if(missingBooks.contains(parentBookName)) throw new IllegalStateException(BOOKS_PROPERTIES_RESOURCE + ": parent book may not be a \"" + MISSING_BOOKS_ATTRIBUTE_NAME + "\": " + parentBookNameAttribute + "=" + parentBookName);
				Book parentBook = books.get(parentBookName);
				if(parentBook == null) {
					throw new IllegalStateException(BOOKS_PROPERTIES_RESOURCE + ": parent book not found (loading order currently matters): " + parentBookNameAttribute + "=" + parentBookName);
				}
				if(!parentPages.add(new PageRef(parentBook, parentPage))) {
					throw new IllegalStateException(BOOKS_PROPERTIES_RESOURCE + ": Duplicate parent: " + parentPageAttribute + "=" + parentPage);
				}
			}
			if(name.equals(rootBookName)) {
				if(!parentPages.isEmpty()) {
					throw new IllegalStateException(BOOKS_PROPERTIES_RESOURCE + ": \"" + ROOT_BOOK_ATTRIBUTE_NAME + "\" may not have any parents: " + rootBookName);
				}
			} else {
				if(parentPages.isEmpty()) {
					throw new IllegalStateException(BOOKS_PROPERTIES_RESOURCE + ": Non-root books must have at least one parent: " + name);
				}
			}
			if(
				books.put(
					name,
					new Book(
						name,
						cvsworkDirectory,
						parentPages,
						PropertiesUtils.loadFromResource(servletContext, ("/".equals(name) ? "" : name) + "/book.properties")
					)
				) != null
			) {
				throw new IllegalStateException(BOOKS_PROPERTIES_RESOURCE + ": Duplicate book: " + name);
			}
		}

		// Load rootBook
		if(missingBooks.contains(rootBookName)) throw new IllegalStateException(BOOKS_PROPERTIES_RESOURCE + ": \"" + ROOT_BOOK_ATTRIBUTE_NAME + "\" may not be a \"" + MISSING_BOOKS_ATTRIBUTE_NAME + "\": " + rootBookName);
		Book newRootBook = books.get(rootBookName);
		if(newRootBook == null) throw new IllegalStateException(BOOKS_PROPERTIES_RESOURCE + ": \"" + ROOT_BOOK_ATTRIBUTE_NAME + "\" is not found in \"" + BOOKS_ATTRIBUTE_NAME + "\": " + rootBookName);

		// Make sure all keys used
		Set<Object> unusedKeys = new HashSet<Object>();
		for(Object key : booksPropsKeys) {
			if(!usedKeys.contains(key)) unusedKeys.add(key);
		}
		if(!unusedKeys.isEmpty()) throw new IllegalStateException(BOOKS_PROPERTIES_RESOURCE + ": Unused keys: " + unusedKeys);

		// Successful book load
		return newRootBook;
	}

	public Map<String,Book> getBooks() {
		return Collections.unmodifiableMap(books);
	}

	public Set<String> getMissingBooks() {
		return Collections.unmodifiableSet(missingBooks);
	}

	/**
	 * Gets the root book as configured in /WEB-INF/books.properties
	 */
	public Book getRootBook() {
		return rootBook;
	}

	/**
	 * Gets the book for the provided context-relative servlet path or <code>null</code> if no book configured at that path.
	 * The book with the longest prefix match is used.
	 * The servlet path must begin with a slash (/).
	 */
	public Book getBook(String servletPath) {
		if(servletPath.charAt(0) != '/') throw new IllegalArgumentException("Invalid servletPath: " + servletPath);
		Book longestPrefixBook = null;
		int longestPrefixLen = -1;
		for(Book book : getBooks().values()) {
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
	public Book getBook(HttpServletRequest request) {
		return getBook(Dispatcher.getCurrentPagePath(request));
	}
	// </editor-fold>

	// <editor-fold defaultstate="collapsed" desc="Views">
	/**
	 * The parameter name used for views.
	 */
	public static final String VIEW_PARAM = "view";

	/**
	 * The default view is the content view and will have the empty view name.
	 */
	public static final String DEFAULT_VIEW_NAME = "content";

	private final Object viewsLock = new Object();

	/**
	 * The views by name in order added.
	 */
	private final Map<String,View> viewsByName = new LinkedHashMap<String,View>();

	/**
	 * Gets the views in order added.
	 */
	public Map<String,View> getViewsByName() {
		return Collections.unmodifiableMap(viewsByName);
	}

	/**
	 * The views in order.
	 */
	private final SortedSet<View> views = new TreeSet<View>();

	/**
	 * Gets the views, ordered by view group then display.
	 *
	 * @see  View#compareTo(com.semanticcms.core.servlet.View)
	 */
	public SortedSet<View> getViews() {
		return Collections.unmodifiableSortedSet(views);
	}

	/**
	 * Registers a new view.
	 *
	 * @throws  IllegalStateException  if a view is already registered with the name.
	 */
	public void addView(View view) throws IllegalStateException {
		String name = view.getName();
		synchronized(viewsLock) {
			if(viewsByName.containsKey(name)) throw new IllegalStateException("View already registered: " + name);
			if(viewsByName.put(name, view) != null) throw new AssertionError();
			if(!views.add(view)) throw new AssertionError();
		}
	}
	// </editor-fold>

	// <editor-fold defaultstate="collapsed" desc="Themes">
	private final Object themesLock = new Object();

	/**
	 * The themes in order added.
	 */
	private final Map<String,Theme> themes = new LinkedHashMap<String,Theme>();

	/**
	 * Gets the themes, in the order added.
	 */
	public Map<String,Theme> getThemes() {
		return Collections.unmodifiableMap(themes);
	}

	/**
	 * Registers a new theme.
	 *
	 * @throws  IllegalStateException  if a theme is already registered with the name.
	 */
	public void addTheme(Theme theme) throws IllegalStateException {
		String name = theme.getName();
		synchronized(themesLock) {
			if(themes.containsKey(name)) throw new IllegalStateException("Theme already registered: " + name);
			if(themes.put(name, theme) != null) throw new AssertionError();
		}
	}
	// </editor-fold>
}