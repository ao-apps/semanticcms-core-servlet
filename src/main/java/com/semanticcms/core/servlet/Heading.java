/*
 * semanticcms-core-servlet - Java API for modeling web page content and relationships in a Servlet environment.
 * Copyright (C) 2013, 2014, 2015, 2016  AO Industries, Inc.
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
package com.semanticcms.core.servlet;

import com.semanticcms.core.model.ElementContext;
import com.semanticcms.core.servlet.impl.HeadingImpl;
import java.io.IOException;
import java.io.Writer;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.jsp.SkipPageException;

public class Heading extends Element<com.semanticcms.core.model.Heading> {

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
			new com.semanticcms.core.model.Heading()
		);
		element.setLabel(label);
	}

	/**
	 * Creates a new heading in the current page context.
	 *
	 * @see  PageContext
	 */
	public Heading(String label) {
		this(
			PageContext.getServletContext(),
			PageContext.getRequest(),
			PageContext.getResponse(),
			label
		);
	}

	@Override
	public Heading id(String id) {
		super.id(id);
		return this;
	}

	private PageIndex pageIndex;
	@Override
	protected void doBody(HttpServletRequest request, HttpServletResponse response, CaptureLevel captureLevel, Body<? super com.semanticcms.core.model.Heading> body) throws ServletException, IOException, SkipPageException {
		pageIndex = PageIndex.getCurrentPageIndex(request);
		super.doBody(request, response, captureLevel, body);
		HeadingImpl.doAfterBody(element);
	}

	@Override
	public void writeTo(Writer out, ElementContext context) throws IOException {
		HeadingImpl.writeHeading(out, context, element, pageIndex);
	}
}
