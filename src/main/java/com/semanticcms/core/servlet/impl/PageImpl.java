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
package com.semanticcms.core.servlet.impl;

import com.aoindustries.io.buffer.BufferResult;
import com.aoindustries.net.Path;
import com.aoindustries.servlet.LocalizedServletException;
import com.aoindustries.validation.ValidationException;
import com.semanticcms.core.model.BookRef;
import com.semanticcms.core.model.ChildRef;
import com.semanticcms.core.model.Node;
import com.semanticcms.core.model.Page;
import com.semanticcms.core.model.PageRef;
import com.semanticcms.core.model.ParentRef;
import com.semanticcms.core.pages.CaptureLevel;
import com.semanticcms.core.pages.local.CurrentCaptureLevel;
import com.semanticcms.core.pages.local.CurrentNode;
import com.semanticcms.core.pages.local.CurrentPage;
import com.semanticcms.core.servlet.ApplicationResources;
import com.semanticcms.core.servlet.Book;
import com.semanticcms.core.servlet.CapturePage;
import com.semanticcms.core.servlet.PageUtils;
import com.semanticcms.core.servlet.SemanticCMS;
import com.semanticcms.core.servlet.Theme;
import com.semanticcms.core.servlet.View;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Map;
import java.util.Set;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.jsp.SkipPageException;
import org.joda.time.ReadableDateTime;

final public class PageImpl {

	public static interface PageImplBody<E extends Throwable> {
		BufferResult doBody(boolean discard, Page page) throws E, IOException, SkipPageException;
	}

	/**
	 * Verified one child-parent relationship.
	 *
	 * @throws  ServletException  if verification failed
	 */
	public static void verifyChildToParent(ChildRef childRef, PageRef parentPageRef, Set<ChildRef> childRefs) throws ServletException {
		if(!childRefs.contains(childRef)) {
			throw new ServletException(
				"The parent page does not have this as a child.  this="
					+ childRef
					+ ", parent="
					+ parentPageRef
					+ ", parent.children="
					+ childRefs
			);
		}
	}

	/**
	 * Verified one child-parent relationship.
	 *
	 * @throws  ServletException  if verification failed
	 */
	public static void verifyChildToParent(PageRef childPageRef, PageRef parentPageRef, Set<ChildRef> childRefs) throws ServletException {
		verifyChildToParent(new ChildRef(childPageRef), parentPageRef, childRefs);
	}

	/**
	 * Verified one parent-child relationship.
	 *
	 * @throws  ServletException  if verification failed
	 */
	public static void verifyParentToChild(ParentRef parentRef, PageRef childPageRef, Set<ParentRef> parentRefs) throws ServletException {
		if(!parentRefs.contains(parentRef)) {
			throw new ServletException(
				"The child page does not have this as a parent.  this="
					+ parentRef
					+ ", child="
					+ childPageRef
					+ ", child.parents="
					+ parentRefs
			);
		}
	}

	/**
	 * Verified one parent-child relationship.
	 *
	 * @throws  ServletException  if verification failed
	 */
	public static void verifyParentToChild(PageRef parentPageRef, PageRef childPageRef, Set<ParentRef> parentRefs) throws ServletException {
		verifyParentToChild(new ParentRef(parentPageRef, null), childPageRef, parentRefs);
	}

	/**
	 * Performs full parent/child verifications of the provided page.  This is normally
	 * not needed for pages that have been added to the cache (PAGE/META level), as verification
	 * is done within the cache.  This is used for BODY level captures which are not put in the
	 * cache and desire full verification.
	 *
	 * @throws  ServletException  if verification failed
	 */
	public static void fullVerifyParentChild(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		Page page
	) throws ServletException, IOException {
		// Verify parents
		if(!page.getAllowParentMismatch()) {
			Map<PageRef,Page> notMissingParents = CapturePage.capturePages(
				servletContext,
				request,
				response,
				PageUtils.filterNotMissingBook(servletContext, page.getParentRefs()),
				CaptureLevel.PAGE
			);
			PageRef pageRef = page.getPageRef();
			for(Map.Entry<PageRef,Page> entry : notMissingParents.entrySet()) {
				verifyChildToParent(pageRef, entry.getKey(), entry.getValue().getChildRefs());
			}
		}
		// Verify children
		if(!page.getAllowChildMismatch()) {
			Map<PageRef,Page> notMissingChildren = CapturePage.capturePages(
				servletContext,
				request,
				response,
				PageUtils.filterNotMissingBook(servletContext, page.getChildRefs()),
				CaptureLevel.PAGE
			);
			PageRef pageRef = page.getPageRef();
			for(Map.Entry<PageRef,Page> entry : notMissingChildren.entrySet()) {
				verifyParentToChild(pageRef, entry.getKey(), entry.getValue().getParentRefs());
			}
		}
	}

	/**
	 * @param pageRef  the default path to this page, this might be changed during page processing
	 */
	public static <E extends Throwable> void doPageImpl(
		final ServletContext servletContext,
		final HttpServletRequest request,
		final HttpServletResponse response,
		PageRef pageRef,
		ReadableDateTime dateCreated,
		ReadableDateTime datePublished,
		ReadableDateTime dateModified,
		ReadableDateTime dateReviewed,
		String title,
		String shortTitle,
		String description,
		String keywords,
		Boolean allowRobots,
		Boolean toc,
		int tocLevels,
		boolean allowParentMismatch,
		boolean allowChildMismatch,
		Map<String,Object> properties,
		PageImplBody<E> body
	) throws E, ServletException, IOException, SkipPageException {
		final Page page = new Page();
		page.setPageRef(pageRef);
		{
			// Pages may not be nested within any kind of node
			Node parentNode = CurrentNode.getCurrentNode(request);
			if(parentNode != null) throw new ServletException("Pages may not be nested within other nodes: " + page.getPageRef() + " not allowed inside of " + parentNode);
			assert CurrentPage.getCurrentPage(request) == null : "When no parent node, cannot have a parent page";
		}

		page.setDateCreated(dateCreated);
		page.setDatePublished(datePublished);
		page.setDateModified(dateModified);
		page.setDateReviewed(dateReviewed);
		page.setTitle(title);
		page.setShortTitle(shortTitle);
		page.setDescription(description);
		page.setKeywords(keywords);
		page.setAllowRobots(allowRobots);
		page.setToc(toc);
		page.setTocLevels(tocLevels);
		page.setAllowParentMismatch(allowParentMismatch);
		page.setAllowChildMismatch(allowChildMismatch);
		if(properties != null) {
			for(Map.Entry<String,Object> entry : properties.entrySet()) {
				String name = entry.getKey();
				if(!page.setProperty(name, entry.getValue())) {
					throw new LocalizedServletException(
						ApplicationResources.accessor,
						"error.duplicatePageProperty",
						name
					);
				}
			}
		}
		// Freeze page once body done
		try {
			// Unlike elements, the page body is still invoked on captureLevel=PAGE, this
			// is done to catch parents and childen.
			if(body != null) {
				// Set currentNode
				CurrentNode.setCurrentNode(request, page);
				try {
					// Set currentPage
					CurrentPage.setCurrentPage(request, page);
					try {
						final CaptureLevel captureLevel = CurrentCaptureLevel.getCaptureLevel(request);
						if(captureLevel == CaptureLevel.BODY) {
							// Invoke page body, capturing output
							page.setBody(body.doBody(false, page).trim());
						} else {
							// Invoke page body, discarding output
							body.doBody(true, page);
						}
						// Page may not move itself to a different book
						PageRef newPageRef = page.getPageRef();
						if(!newPageRef.getBookRef().equals(pageRef.getBookRef())) {
							throw new ServletException(
								"Page may not move itself into a different book.  pageRef="
									+ pageRef
									+ ", newPageRef="
									+ newPageRef
							);
						}
					} finally {
						// Restore previous currentPage
						CurrentPage.setCurrentPage(request, null);
					}
				} finally {
					// Restore previous currentNode
					CurrentNode.setCurrentNode(request, null);
				}
			}
			doAutoParents(servletContext, page);
		} finally {
			page.freeze();
		}
		CapturePage capture = CapturePage.getCaptureContext(request);
		if(capture != null) {
			// Capturing, add to capture
			capture.setCapturedPage(page);
		} else {
			// Perform full verification now since not interacting with the page cache
			fullVerifyParentChild(servletContext, request, response, page);

			// Resolve the view
			SemanticCMS semanticCMS = SemanticCMS.getInstance(servletContext);
			View view;
			{
				String viewName = request.getParameter(SemanticCMS.VIEW_PARAM);
				Map<String,View> viewsByName = semanticCMS.getViewsByName();
				if(viewName == null) {
					view = null;
				} else {
					if(SemanticCMS.DEFAULT_VIEW_NAME.equals(viewName)) throw new ServletException(SemanticCMS.VIEW_PARAM + " paramater may not be sent for default view: " + viewName);
					view = viewsByName.get(viewName);
				}
				if(view == null) {
					// Find default
					view = viewsByName.get(SemanticCMS.DEFAULT_VIEW_NAME);
					if(view == null) throw new ServletException("Default view not found: " + SemanticCMS.DEFAULT_VIEW_NAME);
				}
			}

			// Find the theme
			Theme theme = null;
			{
				// Currently just picks the first non-default theme registered, the uses default
				Theme defaultTheme = null;
				for(Theme t : semanticCMS.getThemes().values()) {
					if(t.isDefault()) {
						assert defaultTheme == null : "More than one default theme registered";
						defaultTheme = t;
					} else {
						// Use first non-default
						theme = t;
						break;
					}
				}
				if(theme == null) {
					// Use default
					if(defaultTheme == null) throw new ServletException("No themes registered");
					theme = defaultTheme;
				}
				assert theme != null;
			}

			// Forward to theme
			theme.doTheme(servletContext, request, response, view, page);
			throw new SkipPageException();
		}
	}

	// TODO: Profile this since have many books now.  Maybe create method on SemanticCMS for this lookup
	private static Book findBookByContentRoot(ServletContext servletContext, PageRef pageRef) {
		for(Book book : SemanticCMS.getInstance(servletContext).getBooks().values()) {
			if(pageRef.equals(book.getContentRoot())) {
				return book;
			}
		}
		return null;
	}

	private static void doAutoParents(ServletContext servletContext, Page page) throws ServletException, MalformedURLException {
		if(page.getParentRefs().isEmpty()) {
			// Auto parents

			// PageRef might have been changed during page capture if the default value was incorrect, such as when using pathInfo, get the new value
			PageRef pageRef = page.getPageRef();

			// If this page is the "content.root" of a book, include all parents configured when book imported.
			Book book = findBookByContentRoot(servletContext, pageRef);
			if(book != null) {
				for(ParentRef bookParentRef : book.getParentRefs()) {
					page.addParentRef(bookParentRef);
				}
			} else {
				// Otherwise, try auto parents
				BookRef pageBookRef = pageRef.getBookRef();
				SemanticCMS semanticCMS = SemanticCMS.getInstance(servletContext);
				Book pageBook = semanticCMS.getBook(pageBookRef);
				String pagePath = pageRef.getPath().toString();
				if(pagePath.endsWith("/")) {
					// If this page URL ends in "/", look for page at "../", error if outside book.
					int lastSlash = pagePath.lastIndexOf('/');
					if(lastSlash == -1) throw new AssertionError();
					int nextLastSlash = pagePath.lastIndexOf('/', lastSlash-1);
					if(nextLastSlash == -1) {
						throw new ServletException("Auto parent of page would be outside book: " + pageRef);
					}
					Path endSlashPath;
					try {
						endSlashPath = Path.valueOf(pagePath.substring(0, nextLastSlash + 1));
					} catch(ValidationException e) {
						AssertionError ae = new AssertionError("Sub paths of a valid path are also valid");
						ae.initCause(e);
						throw ae;
					}
					page.addParentRef(
						new ParentRef(
							new PageRef(
								pageBookRef,
								endSlashPath
							),
							null
						)
					);
				} else {
					// Assume "./".
					int lastSlash = pagePath.lastIndexOf('/');
					if(lastSlash == -1) throw new AssertionError();
					Path endSlashPath;
					try {
						endSlashPath = Path.valueOf(pagePath.substring(0, lastSlash + 1));
					} catch(ValidationException e) {
						AssertionError ae = new AssertionError("Sub paths of a valid path are also valid");
						ae.initCause(e);
						throw ae;
					}
					page.addParentRef(
						new ParentRef(
							new PageRef(
								pageBookRef,
								endSlashPath
							),
							null
						)
					);
				}
			}
		}
	}

	/**
	 * Make no instances.
	 */
	private PageImpl() {
	}
}
