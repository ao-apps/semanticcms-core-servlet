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

import javax.servlet.ServletContext;
import javax.servlet.ServletRequest;

final public class OpenFile {

	private static final String ENABLE_INIT_PARAM = OpenFile.class.getName() + ".enabled";

	/**
	 * Checks if the given host address is allowed to open files on the server.
	 */
	private static boolean isAllowedAddr(String addr) {
		return "127.0.0.1".equals(addr);
	}

	/**
	 * Checks if the given request is allowed to open files on the server.
	 * The servlet init param must have it enabled, as well as be from an allowed IP.
	 */
	public static boolean isAllowed(ServletContext servletContext, ServletRequest request) {
		return
			Boolean.parseBoolean(servletContext.getInitParameter(ENABLE_INIT_PARAM))
			&& isAllowedAddr(request.getRemoteAddr())
		;
	}

	/**
	 * Make no instances.
	 */
	private OpenFile() {
	}
}
