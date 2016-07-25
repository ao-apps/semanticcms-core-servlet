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

import com.aoindustries.io.NullPrintWriter;
import com.aoindustries.io.buffer.BufferWriter;
import com.aoindustries.io.buffer.SegmentedWriter;
import com.aoindustries.lang.NotImplementedException;
import com.aoindustries.servlet.http.Dispatcher;
import com.aoindustries.servlet.http.Includer;
import com.aoindustries.servlet.http.ServletUtil;
import com.aoindustries.web.page.Node;
import com.aoindustries.web.page.Page;
import java.io.IOException;
import java.io.PrintWriter;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import javax.servlet.jsp.SkipPageException;

abstract public class PageServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;

	public static final String PAGE_TEMPLATE_JSP_PATH="/lib/docs/page.inc.jsp";
	public static final String PAGE_REQUEST_ATTRIBUTE = "page";

	/**
	 * @see  Page#getTitle()
	 */
	abstract public String getTitle();

	/**
	 * Defaults to null for "auto".
	 *
	 * @see  Page#getToc()
	 */
	public Boolean getToc() {
		return null;
	}

	/**
	 * @see  Page#getTocLevels()
	 * @see  Page#DEFAULT_TOC_LEVELS
	 */
	public int getTocLevels() {
		return Page.DEFAULT_TOC_LEVELS;
	}

	private static interface DoMethodCallable {
		void doMethodPage(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException, SkipPageException;
	}

	private void doMethod(DoMethodCallable method, HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		final ServletContext servletContext = getServletContext();
		try {
			final Page page = new Page();
			// Find the path to this page
			page.setPageRef(PageRefResolver.getCurrentPageRef(servletContext, req));

			{
				// Pages may not be nested within any kind of node
				Node parentNode = CurrentNode.getCurrentNode(req);
				if(parentNode != null) throw new ServletException("Pages may not be nested within other nodes: " + page.getPageRef() + " not allowed inside of " + parentNode);
				assert CurrentPage.getCurrentPage(req) == null : "When no parent node, cannot have a parent page";
			}

			page.setTitle(getTitle());
			page.setToc(getToc());
			page.setTocLevels(getTocLevels());

			// Set currentNode
			CurrentNode.setCurrentNode(req, page);
			try {
				// Set currentPage
				CurrentPage.setCurrentPage(req, page);
				try {
					// Freeze page once body done
					try {
						// Unlike elements, the page body is still invoked on captureLevel=PAGE, this
						// is done to catch childen.
						final CaptureLevel captureLevel = CaptureLevel.getCaptureLevel(req);
						if(captureLevel == CaptureLevel.BODY) {
							// Invoke page body, capturing output
							BufferWriter capturedOut = new SegmentedWriter();
							try {
								try (PrintWriter capturedPW = new PrintWriter(capturedOut)) {
									method.doMethodPage(
										req,
										new HttpServletResponseWrapper(resp) {
											@Override
											public PrintWriter getWriter() throws IOException {
												return capturedPW;
											}
											@Override
											public ServletOutputStream getOutputStream() {
												throw new NotImplementedException();
											}
										}
									);
								}
							} finally {
								capturedOut.close();
							}
							page.setBody(capturedOut.getResult().trim());
						} else {
							// Invoke page body, discarding output
							method.doMethodPage(
								req,
								new HttpServletResponseWrapper(resp) {
									@Override
									public PrintWriter getWriter() throws IOException {
										return NullPrintWriter.getInstance();
									}
									@Override
									public ServletOutputStream getOutputStream() {
										throw new NotImplementedException();
									}
								}
							);
						}
					} finally {
						page.freeze();
					}
				} finally {
					// Restore previous currentPage
					CurrentPage.setCurrentPage(req, null);
				}
			} finally {
				// Restore previous currentNode
				CurrentNode.setCurrentNode(req, null);
			}
			CapturePage capture = CapturePage.getCaptureContext(req);
			if(capture != null) {
				// Capturing, add to capture
				capture.setCapturedPage(page);
			} else {
				// Display page directly
				// Forward to PAGE_TEMPLATE_JSP_PATH, passing PAGE_REQUEST_ATTRIBUTE request attribute
				Object oldValue = req.getAttribute(PAGE_REQUEST_ATTRIBUTE);
				try {
					// Pass PAGE_REQUEST_ATTRIBUTE attribute
					req.setAttribute(PAGE_REQUEST_ATTRIBUTE, page);
					Dispatcher.forward(
						servletContext,
						PAGE_TEMPLATE_JSP_PATH,
						req,
						resp
					);
				} finally {
					// Restore old value of PAGE_REQUEST_ATTRIBUTE attribute
					req.setAttribute(PAGE_REQUEST_ATTRIBUTE, oldValue);
				}
				Includer.setPageSkipped(req);
			}
		} catch(SkipPageException e) {
			Includer.setPageSkipped(req);
		}
	}

	@Override
	final protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		doMethod(
			(req1, resp1) -> doGetPage(req1, resp1),
			req,
			resp
		);
	}

	protected void doGetPage(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException, SkipPageException {
		Includer.sendError(req, resp, HttpServletResponse.SC_METHOD_NOT_ALLOWED);
		throw new SkipPageException();
	}

	@Override
	final protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		doMethod(
			(req1, resp1) -> doPostPage(req1, resp1),
			req,
			resp
		);
	}

	protected void doPostPage(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException, SkipPageException {
		Includer.sendError(req, resp, HttpServletResponse.SC_METHOD_NOT_ALLOWED);
		throw new SkipPageException();
	}

	@Override
	final protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		doMethod(
			(req1, resp1) -> doPutPage(req1, resp1),
			req,
			resp
		);
	}

	protected void doPutPage(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException, SkipPageException {
		Includer.sendError(req, resp, HttpServletResponse.SC_METHOD_NOT_ALLOWED);
		throw new SkipPageException();
	}

	@Override
	final protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		doMethod(
			(req1, resp1) -> doDeletePage(req1, resp1),
			req,
			resp
		);
	}

	protected void doDeletePage(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException, SkipPageException {
		Includer.sendError(req, resp, HttpServletResponse.SC_METHOD_NOT_ALLOWED);
		throw new SkipPageException();
	}

	@Override
	protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		ServletUtil.doOptions(
			resp,
			PageServlet.class,
			this.getClass(),
			"doGetPage",
			"doPostPage",
			"doPutPage",
			"doDeletePage",
			new Class<?>[] {
				HttpServletRequest.class,
				HttpServletResponse.class
			}
		);
	}
}
