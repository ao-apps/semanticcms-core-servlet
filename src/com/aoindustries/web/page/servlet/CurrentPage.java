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

import com.aoindustries.web.page.Page;
import javax.servlet.ServletRequest;

/**
 * Tracking of the current page during request processing and capturing.
 */
final public class CurrentPage {

	// Cleared and restored on request in CapturePage
	private static final String CURRENT_PAGE_REQUEST_ATTRIBUTE_NAME = /*PageTag.class.getName()+".*/ "currentPage";

	/**
	 * Gets the currentPage or <code>null</code> if not inside a <code>PageTag</code>.
	 */
	public static Page getCurrentPage(ServletRequest request) {
		return (Page)request.getAttribute(CURRENT_PAGE_REQUEST_ATTRIBUTE_NAME);
	}

	public static void setCurrentPage(ServletRequest request, Page page) {
		request.setAttribute(CURRENT_PAGE_REQUEST_ATTRIBUTE_NAME, page);
	}

	/**
	 * Make no instances.
	 */
	private CurrentPage() {
	}
}
