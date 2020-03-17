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

import com.aoindustries.util.AoCollections;
import com.semanticcms.core.model.Book;
import com.semanticcms.core.model.ChildRef;
import com.semanticcms.core.model.Element;
import com.semanticcms.core.model.Page;
import com.semanticcms.core.model.PageRef;
import com.semanticcms.core.model.PageReferrer;
import com.semanticcms.core.model.ParentRef;
import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Utilities for working with pages.
 */
final public class PageUtils {

	public static boolean hasChild(Page page) {
		for(ChildRef childRef : page.getChildRefs()) {
			if(childRef.getPageRef().getBook() != null) return true;
		}
		return false;
	}

	// TODO: Cache result per class per page?
	public static boolean hasElement(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		Page page,
		final Class<? extends Element> elementType,
		final boolean recursive
	) throws ServletException, IOException {
		if(recursive) {
			return CapturePage.traversePagesAnyOrder(
				servletContext,
				request,
				response,
				page,
				CaptureLevel.META,
				new CapturePage.PageHandler<Boolean>() {
					@Override
					public Boolean handlePage(Page page) {
						for(Element element : page.getElements()) {
							if(elementType.isAssignableFrom(element.getClass())) {
								return true;
							}
						}
						return null;
					}
				},
				new CapturePage.TraversalEdges() {
					@Override
					public Collection<ChildRef> getEdges(Page page) {
						return page.getChildRefs();
					}
				},
				new CapturePage.EdgeFilter() {
					@Override
					public boolean applyEdge(PageRef childPage) {
						// Child not in missing book
						return childPage.getBook() != null;
					}
				}
			) != null;
		} else {
			for(Element element : page.getElements()) {
				if(elementType.isAssignableFrom(element.getClass())) {
					return true;
				}
			}
			return false;
		}
	}

	/**
	 * <p>
	 * Finds the allowRobots setting for the given page.
	 * </p>
	 * <p>
	 * When no allowRobots provided, will use the allowRobots(s) of any parent
	 * page that is within the same book.  If there are no parent pages
	 * in this same book, uses the book's allowRobots.
	 * </p>
	 * <p>
	 * When inheriting allowRobots from multiple parent pages, the allowRobots must
	 * be in exact agreement.  This means exactly the same order and all
	 * values matching precisely.  Any mismatch in allowRobots will result in
	 * an exception.
	 * </p>
	 */
	public static boolean findAllowRobots(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		com.semanticcms.core.model.Page page
	) throws ServletException, IOException {
		// TODO: Traversal
		return findAllowRobotsRecursive(
			servletContext,
			request,
			response,
			page,
			new HashMap<PageRef,Boolean>()
		);
	}

	private static boolean findAllowRobotsRecursive(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		com.semanticcms.core.model.Page page,
		Map<PageRef,Boolean> finished
	) throws ServletException, IOException {
		PageRef pageRef = page.getPageRef();
		assert !finished.containsKey(pageRef);
		// Use directly set allowRobots first
		Boolean pageAllowRobots = page.getAllowRobots();
		if(pageAllowRobots == null) {
			// Use the allowRobots of all parents in the same book
			Book book = pageRef.getBook();
			for(ParentRef parentRef : page.getParentRefs()) {
				PageRef parentPageRef = parentRef.getPageRef();
				if(book.equals(parentPageRef.getBook())) {
					// Check finished already
					Boolean parentAllowRobots = finished.get(parentPageRef);
					if(parentAllowRobots == null) {
						// Capture parent and find its allowRobots
						parentAllowRobots = findAllowRobotsRecursive(
							servletContext,
							request,
							response,
							CapturePage.capturePage(servletContext, request, response, parentPageRef, CaptureLevel.PAGE),
							finished
						);
					}
					if(pageAllowRobots == null) {
						pageAllowRobots = parentAllowRobots;
					} else {
						// Must precisely match when have multiple parents
						if(!pageAllowRobots.equals(parentAllowRobots)) throw new ServletException("Mismatched allowRobots inherited from different parents: " + pageAllowRobots + " does not match " + parentAllowRobots);
					}
				}
			}
			// No parents in the same book, use book allowRobots
			if(pageAllowRobots == null) pageAllowRobots = book.getAllowRobots();
		}
		// Store in finished
		finished.put(pageRef, pageAllowRobots);
		return pageAllowRobots;
	}

	/**
	 * Filters for all pageRefs that are present (not missing books).
	 */
	public static <PR extends PageReferrer> Set<PR> filterNotMissingBook(Set<PR> pageReferrers) {
		int size = pageReferrers.size();
		if(size == 0) {
			return Collections.emptySet();
		} else if(size == 1) {
			PR pageReferrer = pageReferrers.iterator().next();
			if(pageReferrer.getPageRef().getBook() != null) {
				return Collections.singleton(pageReferrer);
			} else {
				return Collections.emptySet();
			}
		} else {
			Set<PR> notMissingBooks = new LinkedHashSet<PR>(size *4/3+1);
			for(PR pageReferrer : pageReferrers) {
				if(pageReferrer.getPageRef().getBook() != null) {
					if(!notMissingBooks.add(pageReferrer)) throw new AssertionError();
				}
			}
			return Collections.unmodifiableSet(notMissingBooks);
		}
	}

	/**
	 * Determines the short title for a page and one of its parents.
	 */
	public static String getShortTitle(PageRef parentPageRef, Page page) {
		// Check for per-parent shortTitle first for the given parent
		if(parentPageRef != null) {
			for(ParentRef parentRef : page.getParentRefs()) {
				if(parentRef.getPageRef().equals(parentPageRef)) {
					String shortTitle = parentRef.getShortTitle();
					if(shortTitle != null) return shortTitle;
					break;
				}
			}
		}
		// Use the overall page shortTitle
		return page.getShortTitle();
	}

	/**
	 * Gets all the parents of the given page that are not in missing books
	 * and are applicable to the given view.
	 *
	 * @return  The filtered set of parents, in the order declared by the page.
	 */
	public static Set<Page> getApplicableParents(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		View view,
		Page page
	) throws ServletException, IOException {
		Collection<Page> parents = CapturePage.capturePages(
			servletContext,
			request,
			response,
			filterNotMissingBook(page.getParentRefs()),
			CaptureLevel.META // TODO: View provide capture level required for isApplicable check, might be PAGE or (null for none) for some views.
		).values();
		Set<Page> applicableParents = new LinkedHashSet<Page>(parents.size() *4/3+1);
		for(Page parent : parents) {
			if(view.isApplicable(servletContext, request, response, parent)) {
				applicableParents.add(parent);
			}
		}
		return AoCollections.optimalUnmodifiableSet(applicableParents);
	}

	/**
	 * Make no instances.
	 */
	private PageUtils() {
	}
}
