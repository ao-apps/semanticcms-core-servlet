/*
 * ao-web-page-servlet - Java API for modeling web page content and relationships in a Servlet environment.
 * Copyright (C) 2015, 2016  AO Industries, Inc.
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

import com.aoindustries.lang.NotImplementedException;
import com.aoindustries.web.page.ElementContext;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import javax.servlet.jsp.PageContext;

/**
 * An ElementContext that is a PageContext.
 */
public class PageElementContext implements ElementContext {

	private final PageContext pageContext;

	public PageElementContext(PageContext pageContext) {
		this.pageContext = pageContext;
	}

	@Override
	public void include(String resource, Writer out) throws IOException {
		try {
			ServletContext servletContext = pageContext.getServletContext();
			RequestDispatcher dispatcher = servletContext.getRequestDispatcher(resource);
			dispatcher.include(
				pageContext.getRequest(),
				new HttpServletResponseWrapper((HttpServletResponse)pageContext.getResponse()) {
					@Override
					public PrintWriter getWriter() throws IOException {
						if(out instanceof PrintWriter) return (PrintWriter)out;
						return new PrintWriter(out);
					}
					@Override
					public ServletOutputStream getOutputStream() throws IOException {
						throw new NotImplementedException();
					}
				}
			);
		} catch(ServletException e) {
			throw new IOException(e);
		}
	}
}
