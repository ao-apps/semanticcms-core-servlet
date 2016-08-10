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
package com.semanticcms.core.servlet.impl;

import com.aoindustries.io.buffer.BufferResult;
import com.aoindustries.servlet.http.Dispatcher;
import com.semanticcms.core.model.Book;
import com.semanticcms.core.model.Node;
import com.semanticcms.core.model.Page;
import com.semanticcms.core.model.PageRef;
import com.semanticcms.core.servlet.BooksContextListener;
import com.semanticcms.core.servlet.CaptureLevel;
import com.semanticcms.core.servlet.CapturePage;
import com.semanticcms.core.servlet.CurrentNode;
import com.semanticcms.core.servlet.CurrentPage;
import com.semanticcms.core.servlet.PageContext;
import com.semanticcms.core.servlet.PageRefResolver;
import java.io.IOException;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.jsp.SkipPageException;

final public class PageImpl {

	private static final String PAGE_TEMPLATE_JSP_PATH = "/lib/docs/page.inc.jsp";
	private static final String PAGE_REQUEST_ATTRIBUTE = "page";

	public static interface PageImplBody<E extends Throwable> {
		BufferResult doBody(boolean discard, Page page) throws E, IOException, SkipPageException;
	}

	public static <E extends Throwable> void doPageImpl(
		final ServletContext servletContext,
		final HttpServletRequest request,
		final HttpServletResponse response,
		String title,
		String shortTitle,
		String description,
		String keywords,
		Boolean toc,
		int tocLevels,
		boolean allowParentMismatch,
		boolean allowChildMismatch,
		PageImplBody<E> body
	) throws E, ServletException, IOException, SkipPageException {
		final Page page = new Page();
		// Find the default path to this page, this might be changed during page processing
		final PageRef defaultPageRef = PageRefResolver.getCurrentPageRef(servletContext, request);
		page.setPageRef(defaultPageRef);

		{
			// Pages may not be nested within any kind of node
			Node parentNode = CurrentNode.getCurrentNode(request);
			if(parentNode != null) throw new ServletException("Pages may not be nested within other nodes: " + page.getPageRef() + " not allowed inside of " + parentNode);
			assert CurrentPage.getCurrentPage(request) == null : "When no parent node, cannot have a parent page";
		}

		page.setTitle(title);
		page.setShortTitle(shortTitle);
		page.setDescription(description);
		page.setKeywords(keywords);
		page.setToc(toc);
		page.setTocLevels(tocLevels);
		page.setAllowParentMismatch(allowParentMismatch);
		page.setAllowChildMismatch(allowChildMismatch);

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
						final CaptureLevel captureLevel = CaptureLevel.getCaptureLevel(request);
						if(captureLevel == CaptureLevel.BODY) {
							// Invoke page body, capturing output
							page.setBody(body.doBody(false, page).trim());
						} else {
							// Invoke page body, discarding output
							body.doBody(true, page);
						}
						// Page may not move itself to a different book
						PageRef newPageRef = page.getPageRef();
						if(!newPageRef.getBook().equals(defaultPageRef.getBook())) {
							throw new ServletException(
								"Page may not move itself into a different book.  defaultPageRef="
									+ defaultPageRef
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
			if(page.getParentPages().isEmpty()) {
				// Auto parents

				// PageRef might have been changed during page capture if the default value was incorrect, such as when using pathInfo, get the new value
				PageRef pageRef = page.getPageRef();

				// If this page is the "content.root" of a book, include all parents configured when book imported.
				boolean addedBookParents = false;
				for(Book book : BooksContextListener.getBooks(servletContext).values()) {
					if(book.getContentRoot().equals(pageRef)) {
						for(PageRef bookParentPage : book.getParentPages()) {
							page.addParentPage(bookParentPage);
						}
						addedBookParents = true;
						break;
					}
				}
				if(!addedBookParents) {
					Book pageBook = pageRef.getBook();
					assert pageBook != null;
					String pagePath = pageRef.getPath();
					if(pagePath.endsWith("/") || pagePath.endsWith("/index.jsp")) {
						// If this page URL ends in "/" or "/index.jsp", look for JSP page at "../index.jsp" then asssume "../", error if outside book.
						int lastSlash = pagePath.lastIndexOf('/');
						if(lastSlash == -1) throw new AssertionError();
						int nextLastSlash = pagePath.lastIndexOf('/', lastSlash-1);
						if(nextLastSlash == -1) {
							throw new ServletException("Auto parent of page would be outside book: " + pageRef);
						}
						String endSlashPath = pagePath.substring(0, nextLastSlash + 1);
						PageRef indexJspPageRef = new PageRef(pageBook, endSlashPath + "index.jsp");
						if(servletContext.getResource(indexJspPageRef.getServletPath()) != null) {
							page.addParentPage(indexJspPageRef);
						} else {
							page.addParentPage(new PageRef(pageBook, endSlashPath));
						}
					} else {
						// Look for page at "./index.jsp" then assume "./".
						int lastSlash = pagePath.lastIndexOf('/');
						if(lastSlash == -1) throw new AssertionError();
						String endSlashPath = pagePath.substring(0, lastSlash + 1);
						PageRef indexJspPageRef = new PageRef(pageBook, endSlashPath + "index.jsp");
						if(servletContext.getResource(indexJspPageRef.getServletPath()) != null) {
							page.addParentPage(indexJspPageRef);
						} else {
							page.addParentPage(new PageRef(pageBook, endSlashPath));
						}
					}
				}
			}
		} finally {
			page.freeze();
		}
		CapturePage capture = CapturePage.getCaptureContext(request);
		if(capture != null) {
			// Capturing, add to capture
			capture.setCapturedPage(page);
		} else {
			// Verify parents
			if(!page.getAllowParentMismatch()) {
				for(PageRef parentRef : page.getParentPages()) {
					// Can't verify parent reference to missing book
					if(parentRef.getBook() != null) {
						Page parentPage = CapturePage.capturePage(servletContext, request, response, parentRef, CaptureLevel.PAGE);
						// PageRef might have been changed during page capture if the default value was incorrect, such as when using pathInfo, get the new value
						PageRef pageRef = page.getPageRef();
						if(!parentPage.getChildPages().contains(pageRef)) {
							throw new ServletException(
								"The parent page does not have this as a child.  this="
									+ pageRef
									+ ", parent="
									+ parentRef
							);
						}
					}
				}
			}
			// Verify children
			if(!page.getAllowChildMismatch()) {
				for(PageRef childRef : page.getChildPages()) {
					// Can't verify child reference to missing book
					if(childRef.getBook() != null) {
						Page childPage = CapturePage.capturePage(servletContext, request, response, childRef, CaptureLevel.PAGE);
						// PageRef might have been changed during page capture if the default value was incorrect, such as when using pathInfo, get the new value
						PageRef pageRef = page.getPageRef();
						if(!childPage.getParentPages().contains(pageRef)) {
							throw new ServletException(
								"The child page does not have this as a parent.  this="
									+ pageRef
									+ ", child="
									+ childRef
							);
						}
					}
				}
			}

			// Display page directly
			// Forward to PAGE_TEMPLATE_JSP_PATH, passing PAGE_REQUEST_ATTRIBUTE request attribute
			Object oldValue = request.getAttribute(PAGE_REQUEST_ATTRIBUTE);
			try {
				// Pass PAGE_REQUEST_ATTRIBUTE attribute
				request.setAttribute(PAGE_REQUEST_ATTRIBUTE, page);
				// Clear PageContext on include
				PageContext.newPageContext(
					null,
					null,
					null,
					// Java 1.8: Lambda
					new PageContext.PageContextCallable() {
						@Override
						public void call() throws ServletException, IOException {
							Dispatcher.forward(
								servletContext,
								PAGE_TEMPLATE_JSP_PATH,
								request,
								response
							);
						}
					}
				);
			} finally {
				// Restore old value of PAGE_REQUEST_ATTRIBUTE attribute
				request.setAttribute(PAGE_REQUEST_ATTRIBUTE, oldValue);
			}
			throw new SkipPageException();
		}
	}

	/**
	 * Make no instances.
	 */
	private PageImpl() {
	}
}
