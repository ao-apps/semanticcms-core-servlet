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

import com.aoindustries.web.page.ElementContext;
import com.aoindustries.web.page.servlet.impl.HeadingImpl;
import java.io.IOException;
import java.io.Writer;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.jsp.SkipPageException;

public class Heading extends Element<com.aoindustries.web.page.Heading> {

	public Heading(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		String label
	) {
		super(
			servletContext,
			request,
			response,
			new com.aoindustries.web.page.Heading()
		);
		element.setLabel(label);
	}

	@Override
	public Heading id(String id) {
		super.id(id);
		return this;
	}

	private PageIndex pageIndex;
	@Override
	protected void doBody(HttpServletRequest request, HttpServletResponse response, CaptureLevel captureLevel, ElementBody<? super com.aoindustries.web.page.Heading> body) throws ServletException, IOException, SkipPageException {
		pageIndex = PageIndex.getCurrentPageIndex(request);
		super.doBody(request, response, captureLevel, body);
		HeadingImpl.doAfterBody(element);
	}

	@Override
	public void writeTo(Writer out, ElementContext context) throws IOException {
		HeadingImpl.writeHeading(out, context, element, pageIndex);
	}
}
