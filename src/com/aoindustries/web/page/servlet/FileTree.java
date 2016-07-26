/*
 * ao-web-page-servlet - Java API for modeling web page content and relationships in a Servlet environment.
 * Copyright (C) 2014, 2015, 2016  AO Industries, Inc.
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

import com.aoindustries.web.page.Node;
import com.aoindustries.web.page.servlet.impl.FileTreeImpl;
import java.io.IOException;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.jsp.SkipPageException;

public class FileTree {

	private final ServletContext servletContext;
	private final HttpServletRequest request;
	private final HttpServletResponse response;
	private final Node root;

	private boolean includeElements;

	public FileTree(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		Node root
	) {
		this.servletContext = servletContext;
		this.request = request;
		this.response = response;
		this.root = root;
	}

	public FileTree includeElements(boolean includeElements) {
		this.includeElements = includeElements;
		return this;
	}

	public void invoke() throws ServletException, IOException, SkipPageException {
		FileTreeImpl.writeFileTreeImpl(
			servletContext,
			request,
			response,
			response.getWriter(),
			root,
			includeElements
		);
	}
}
