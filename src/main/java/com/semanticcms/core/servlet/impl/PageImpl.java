/*
 * semanticcms-core-servlet - Java API for modeling web page content and relationships in a Servlet environment.
 * Copyright (C) 2013, 2014, 2015, 2016, 2017, 2018, 2019, 2020, 2021  AO Industries, Inc.
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

import com.aoindustries.encoding.Doctype;
import com.aoindustries.encoding.Serialization;
import com.aoindustries.encoding.servlet.DoctypeEE;
import com.aoindustries.encoding.servlet.SerializationEE;
import com.aoindustries.html.servlet.DocumentEE;
import com.aoindustries.io.buffer.BufferResult;
import com.aoindustries.net.Path;
import com.aoindustries.servlet.LocalizedServletException;
import com.aoindustries.web.resources.registry.Registry;
import com.aoindustries.web.resources.servlet.RegistryEE;
import com.semanticcms.core.controller.Book;
import com.semanticcms.core.controller.SemanticCMS;
import com.semanticcms.core.model.BookRef;
import com.semanticcms.core.model.Node;
import com.semanticcms.core.model.Page;
import com.semanticcms.core.model.PageRef;
import com.semanticcms.core.model.ParentRef;
import com.semanticcms.core.pages.CaptureLevel;
import com.semanticcms.core.pages.local.CaptureContext;
import com.semanticcms.core.pages.local.CurrentCaptureLevel;
import com.semanticcms.core.pages.local.CurrentNode;
import com.semanticcms.core.pages.local.CurrentPage;
import static com.semanticcms.core.servlet.Resources.PACKAGE_RESOURCES;
import java.io.IOException;
import java.net.MalformedURLException;
import java.util.Map;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.jsp.SkipPageException;
import org.joda.time.ReadableDateTime;

final public class PageImpl {

	/**
	 * @param  <Ex>  An arbitrary exception type that may be thrown
	 */
	@FunctionalInterface
	public static interface PageImplBody<Ex extends Throwable> {
		BufferResult doBody(boolean discard, Page page) throws Ex, IOException, SkipPageException;
	}

	/**
	 * @param  <Ex>  An arbitrary exception type that may be thrown
	 * @param pageRef  the default path to this page, this might be changed during page processing
	 */
	// TODO: Doctype and Serialization set before this - document here like in WebPageLayout.startHtml
	// TODO: doctype and serialization on Page and PageTag like in ao:html tag
	// TODO: themes adhere to current doctype and serialization, don't reset in ao:html tag itself
	// TODO: page captures reset to default null on request
	// TODO: All theme/layout/skin support both HTML 4 and 5?
	// TODO: Fall-back to div without semantic tags?
	public static <Ex extends Throwable> void doPageImpl(
		final ServletContext servletContext,
		final HttpServletRequest request,
		final HttpServletResponse response,
		CaptureContext capture,
		PageRef pageRef,
		ReadableDateTime dateCreated,
		ReadableDateTime datePublished,
		ReadableDateTime dateModified,
		ReadableDateTime dateReviewed,
		Serialization serialization,
		Doctype doctype,
		Boolean autonli,
		Boolean indent,
		String title,
		String shortTitle,
		String description,
		String keywords,
		Boolean allowRobots,
		Boolean toc,
		int tocLevels,
		boolean allowParentMismatch,
		boolean allowChildMismatch,
		Map<String, Object> properties,
		PageImplBody<Ex> body
	) throws Ex, ServletException, IOException, SkipPageException {
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
			for(Map.Entry<String, Object> entry : properties.entrySet()) {
				String name = entry.getKey();
				if(!page.setProperty(name, entry.getValue())) {
					throw new LocalizedServletException(
						PACKAGE_RESOURCES,
						"error.duplicatePageProperty",
						name
					);
				}
			}
		}
		Serialization oldSerialization;
		boolean setSerialization;
		if(serialization == null) {
			serialization = SerializationEE.get(servletContext, request);
			oldSerialization = null;
			setSerialization = false;
		} else {
			oldSerialization = SerializationEE.replace(request, serialization);
			setSerialization = true;
		}
		assert serialization != null;
		try {
			Doctype oldDoctype;
			boolean setDoctype;
			if(doctype == null) {
				doctype = DoctypeEE.get(servletContext, request);
				oldDoctype = null;
				setDoctype = false;
			} else {
				oldDoctype = DoctypeEE.replace(request, doctype);
				setDoctype = true;
			}
			assert doctype != null;
			try {
				Boolean oldAutonli;
				boolean setAutonli;
				if(autonli == null) {
					autonli = DocumentEE.getAutonli(servletContext, request);
					oldAutonli = null;
					setAutonli = false;
				} else {
					oldAutonli = DocumentEE.replaceAutonli(request, autonli);
					setAutonli = true;
				}
				assert autonli != null;
				try {
					Boolean oldIndent;
					boolean setIndent;
					if(indent == null) {
						indent = DocumentEE.getIndent(servletContext, request);
						oldIndent = null;
						setIndent = false;
					} else {
						oldIndent = DocumentEE.replaceIndent(request, indent);
						setIndent = true;
					}
					assert indent != null;
					try {
						// Freeze page once body done
						try {
							Registry oldPageRegistry = RegistryEE.Page.get(request);
							try {
								Registry pageRegistry = new Registry();
								page.setRegistry(pageRegistry);
								RegistryEE.Page.set(request, pageRegistry);

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
							} finally {
								RegistryEE.Page.set(request, oldPageRegistry);
							}
							doAutoParents(servletContext, page);
						} finally {
							page.freeze();
						}
						// Capturing, add to capture
						capture.setCapturedPage(page);
					} finally {
						if(setIndent) DocumentEE.setIndent(request, oldIndent);
					}
				} finally {
					if(setAutonli) DocumentEE.setAutonli(request, oldAutonli);
				}
			} finally {
				if(setDoctype) DoctypeEE.set(request, oldDoctype);
			}
		} finally {
			if(setSerialization) SerializationEE.set(request, oldSerialization);
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
				final String pagePath = pageRef.getPath().toString();
				if(pagePath.endsWith("/")) {
					// If this page URL ends in "/", look for page at "../", error if outside book.
					int lastSlash = pagePath.lastIndexOf('/');
					if(lastSlash == -1) throw new AssertionError();
					int nextLastSlash = pagePath.lastIndexOf('/', lastSlash-1);
					if(nextLastSlash == -1) {
						throw new ServletException("Auto parent of page would be outside book: " + pageRef);
					}
					Path endSlashPath = pageRef.getPath().prefix(nextLastSlash + 1);
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
					Path endSlashPath = pageRef.getPath().prefix(lastSlash + 1);
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
