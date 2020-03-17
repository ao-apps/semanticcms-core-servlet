/*
 * ao-web-page-servlet - Java API for modeling web page content and relationships in a Servlet environment.
 * Copyright (C) 2016  AO Industries, Inc.
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

import com.semanticcms.core.model.Author;
import com.semanticcms.core.model.Book;
import com.semanticcms.core.model.PageRef;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Author processing utilities.
 */
public class AuthorUtils {

	/**
	 * <p>
	 * Finds all the authors for the given page.
	 * </p>
	 * <p>
	 * When no authors provided, will use the author(s) of any parent
	 * page that is within the same book.  If there are no parent pages
	 * in this same book, uses the book's authors.
	 * </p>
	 * <p>
	 * When inheriting authorship from multiple parent pages, the authors must
	 * be in exact agreement.  This means exactly the same order and all
	 * values matching precisely.  Any mismatch in authors will result in
	 * an exception.
	 * </p>
	 */
	public static Set<Author> findAuthors(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		com.semanticcms.core.model.Page page
	) throws ServletException, IOException {
		return findAuthorsRecursive(
			servletContext,
			request,
			response,
			page,
			new HashMap<PageRef,Set<Author>>()
		);
	}

	/**
	 * Checks that both the iterables have equal objects in iteration order.
	 */
	private static boolean exactMatch(Iterable<?> iterable1, Iterable<?> iterable2) {
		if(iterable1 == iterable2) return true;
		Iterator<?> iter1 = iterable1.iterator();
		Iterator<?> iter2 = iterable2.iterator();
		while(iter1.hasNext() && iter2.hasNext()) {
			Object o1 = iter1.next();
			Object o2 = iter2.next();
			if(!o1.equals(o2)) return false;
		}
		return !iter1.hasNext() && !iter2.hasNext();
	}

	private static Set<Author> findAuthorsRecursive(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		com.semanticcms.core.model.Page page,
		Map<PageRef,Set<Author>> finished
	) throws ServletException, IOException {
		PageRef pageRef = page.getPageRef();
		assert !finished.containsKey(pageRef);
		// Use directly set authors first
		Set<Author> pageAuthors = page.getAuthors();
		if(pageAuthors.isEmpty()) {
			// Use the authors of all parents in the same book
			pageAuthors = null;
			Book book = pageRef.getBook();
			for(PageRef parentRef : page.getParentPages()) {
				if(book.equals(parentRef.getBook())) {
					// Check finished already
					Set<Author> parentAuthors = finished.get(parentRef);
					if(parentAuthors == null) {
						// Capture parent and find its authors
						parentAuthors = findAuthorsRecursive(
							servletContext,
							request,
							response,
							CapturePage.capturePage(servletContext, request, response, parentRef, CaptureLevel.PAGE),
							finished
						);
					}
					if(pageAuthors == null) {
						pageAuthors = parentAuthors;
					} else {
						// Must precisely match when have multiple parents
						if(!exactMatch(pageAuthors, parentAuthors)) throw new ServletException("Mismatched authors inherited from different parents: " + pageAuthors + " does not match " + parentAuthors);
					}
				}
			}
			// No parents in the same book, use book authors
			if(pageAuthors == null) pageAuthors = book.getAuthors();
		}
		// Store in finished
		finished.put(pageRef, pageAuthors);
		return pageAuthors;
	}

	/**
	 * Make no instances.
	 */
	private AuthorUtils() {
	}
}
