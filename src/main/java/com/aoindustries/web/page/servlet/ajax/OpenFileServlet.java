/*
 * ao-web-page-servlet - Java API for modeling web page content and relationships in a Servlet environment.
 * Copyright (C) 2013, 2014, 2016  AO Industries, Inc.
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
package com.aoindustries.web.page.servlet.ajax;

import com.aoindustries.web.page.servlet.OpenFile;
import java.io.IOException;
import java.io.PrintWriter;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.jsp.SkipPageException;

/**
 * Opens the file provided in the book and path parameters.  This file
 * must reside within this application and be of a supported type.
 * This is to be called by the JavaScript function openFile.
 *
 * Request parameters:
 *   book  The name of the book of the file to open
 *   path  The book-relative path of the file to open
 */
@WebServlet(OpenFileServlet.SERVLET_PATH)
public class OpenFileServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;

	public static final String SERVLET_PATH = "/ao-web-page-servlet/ajax/open-file";

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		try {
			OpenFile.openFile(
				getServletContext(),
				request,
				response,
				request.getParameter("book"),
				request.getParameter("path")
			);
			// Write output
			response.reset();
			response.setContentType("application/xml");
			PrintWriter out = response.getWriter();
			out.println("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\" ?>");
			out.println("<success>true</success>");
		} catch(SkipPageException e) {
			// Nothing to do
		}
	}
}
