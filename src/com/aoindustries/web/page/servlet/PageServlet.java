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

import com.aoindustries.servlet.http.Includer;
import com.aoindustries.servlet.http.ServletUtil;
import com.aoindustries.web.page.Page;
import java.io.IOException;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.jsp.SkipPageException;

abstract public class PageServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;

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
		void doMethod(HttpServletRequest req, HttpServletResponse resp, Page page) throws ServletException, IOException, SkipPageException;
	}

	private void callInPage(DoMethodCallable method, HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		final ServletContext servletContext = getServletContext();
		try {
			new com.aoindustries.web.page.servlet.Page(servletContext, req, resp, getTitle())
				.toc(getToc())
				.tocLevels(getTocLevels())
				.invoke(
					(req1, resp1, page) -> method.doMethod(req1, resp1, page)
				)
			;
		} catch(SkipPageException e) {
			Includer.setPageSkipped(req);
		}
	}

	@Override
	final protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		callInPage(
			(req1, resp1, page) -> doGet(req1, resp1, page),
			req,
			resp
		);
	}

	protected void doGet(HttpServletRequest req, HttpServletResponse resp, Page page) throws ServletException, IOException, SkipPageException {
		Includer.sendError(req, resp, HttpServletResponse.SC_METHOD_NOT_ALLOWED);
		throw new SkipPageException();
	}

	@Override
	final protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		callInPage(
			(req1, resp1, page) -> doPost(req1, resp1, page),
			req,
			resp
		);
	}

	protected void doPost(HttpServletRequest req, HttpServletResponse resp, Page page) throws ServletException, IOException, SkipPageException {
		Includer.sendError(req, resp, HttpServletResponse.SC_METHOD_NOT_ALLOWED);
		throw new SkipPageException();
	}

	@Override
	final protected void doPut(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		callInPage(
			(req1, resp1, page) -> doPut(req1, resp1, page),
			req,
			resp
		);
	}

	protected void doPut(HttpServletRequest req, HttpServletResponse resp, Page page) throws ServletException, IOException, SkipPageException {
		Includer.sendError(req, resp, HttpServletResponse.SC_METHOD_NOT_ALLOWED);
		throw new SkipPageException();
	}

	@Override
	final protected void doDelete(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		callInPage(
			(req1, resp1, page) -> doDelete(req1, resp1, page),
			req,
			resp
		);
	}

	protected void doDelete(HttpServletRequest req, HttpServletResponse resp, Page page) throws ServletException, IOException, SkipPageException {
		Includer.sendError(req, resp, HttpServletResponse.SC_METHOD_NOT_ALLOWED);
		throw new SkipPageException();
	}

	@Override
	final protected void doOptions(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
		callInPage(
			(req1, resp1, page) -> doOptions(req1, resp1, page),
			req,
			resp
		);
	}

	protected void doOptions(HttpServletRequest req, HttpServletResponse resp, Page page) throws ServletException, IOException, SkipPageException {
		ServletUtil.doOptions(
			resp,
			PageServlet.class,
			this.getClass(),
			"doGet",
			"doPost",
			"doPut",
			"doDelete",
			new Class<?>[] {
				HttpServletRequest.class,
				HttpServletResponse.class,
				Page.class
			}
		);
	}
}
