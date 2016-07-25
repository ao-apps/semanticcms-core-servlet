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

import com.aoindustries.io.FileUtils;
import com.aoindustries.web.page.Book;
import com.aoindustries.web.page.DiaExport;
import com.aoindustries.web.page.PageRef;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class DiaExportServlet extends HttpServlet {

	private static final long serialVersionUID = 1L;

	public static final int DEFAULT_WIDTH = 200;

	/** Performing an oversampling for high-resolution devices and zoom */
	public static final int OVERSAMPLING = 1; // Was 2, but clearer on a typical browser at 1

	private DiaExport getThumbnail(HttpServletRequest request) throws IOException, ServletException {
		String bookName = request.getParameter("book");
		Book book = BooksContextListener.getBooks(getServletContext()).get(bookName);
		if(book==null) throw new IOException("Book not found: " + bookName);
		PageRef pageRef = new PageRef(book, request.getParameter("path"));
		Integer width = request.getParameter("width")==null ? null : Integer.valueOf(request.getParameter("width"));
		Integer height = request.getParameter("height")==null ? null : Integer.valueOf(request.getParameter("height"));
		// Use default width when neither provided
		if(width==null && height==null) width = DEFAULT_WIDTH * OVERSAMPLING;
		// Get the thumbnail image
		return DiaExport.exportDiagram(
			pageRef,
			width,
			height,
			(File)getServletContext().getAttribute("javax.servlet.context.tempdir" /*ServletContext.TEMPDIR*/)
		);
	}

	@Override
	protected long getLastModified(HttpServletRequest request) {
		try {
			DiaExport thumbnail = getThumbnail(request);
			long lastModified = thumbnail.getTmpFile().lastModified();
			return lastModified==0 ? -1 : lastModified;
		} catch(IOException|ServletException e) {
			getServletContext().log(null, e);
			return -1;
		}
	}

	@Override
	protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		DiaExport thumbnail = getThumbnail(request);
		// Write output
		response.reset();
		response.setContentType("image/png");
		long length = thumbnail.getTmpFile().length();
		if(length>0 && length<=Integer.MAX_VALUE) response.setContentLength((int)length);
		try (OutputStream out = response.getOutputStream()) {
			FileUtils.copy(thumbnail.getTmpFile(), out);
		}
	}
}
