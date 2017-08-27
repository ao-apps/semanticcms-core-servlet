/*
 * semanticcms-core-servlet - Java API for modeling web page content and relationships in a Servlet environment.
 * Copyright (C) 2013, 2014, 2015, 2016, 2017  AO Industries, Inc.
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

import com.aoindustries.net.DomainName;
import com.aoindustries.net.HttpParameters;
import com.aoindustries.net.Path;
import com.semanticcms.core.model.ElementContext;
import com.semanticcms.core.pages.local.PageContext;
import java.io.IOException;
import java.io.Writer;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.jsp.SkipPageException;

public class Link extends Element<com.semanticcms.core.model.Link> {

	public Link(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		com.semanticcms.core.model.Link element
	) {
		super(
			servletContext,
			request,
			response,
			element
		);
	}

	public Link(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response
	) {
		this(
			servletContext,
			request,
			response,
			new com.semanticcms.core.model.Link()
		);
	}

	public Link(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		com.semanticcms.core.model.Link element,
		String page
	) {
		this(servletContext, request, response, element);
		element.setPagePath(page);
	}

	public Link(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		String page
	) {
		this(servletContext, request, response);
		element.setPagePath(page);
	}

	public Link(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		com.semanticcms.core.model.Link element,
		Path book,
		String page
	) {
		this(servletContext, request, response, element, page);
		element.setBook(book);
	}

	public Link(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		Path book,
		String page
	) {
		this(servletContext, request, response, page);
		element.setBook(book);
	}

	public Link(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		com.semanticcms.core.model.Link element,
		DomainName domain,
		Path book,
		String page
	) {
		this(servletContext, request, response, element, book, page);
		element.setDomain(domain);
	}

	public Link(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		DomainName domain,
		Path book,
		String page
	) {
		this(servletContext, request, response, book, page);
		element.setDomain(domain);
	}

	/**
	 * Creates a new link in the current page context.
	 *
	 * @see  PageContext
	 */
	public Link(com.semanticcms.core.model.Link element) {
		this(
			PageContext.getServletContext(),
			PageContext.getRequest(),
			PageContext.getResponse(),
			element
		);
	}

	/**
	 * Creates a new link in the current page context.
	 *
	 * @see  PageContext
	 */
	public Link() {
		this(
			PageContext.getServletContext(),
			PageContext.getRequest(),
			PageContext.getResponse()
		);
	}

	/**
	 * Creates a new link in the current page context.
	 *
	 * @see  PageContext
	 */
	public Link(
		com.semanticcms.core.model.Link element,
		String page
	) {
		this(element);
		element.setPagePath(page);
	}

	/**
	 * Creates a new link in the current page context.
	 *
	 * @see  PageContext
	 */
	public Link(String page) {
		this();
		element.setPagePath(page);
	}

	/**
	 * Creates a new link in the current page context.
	 *
	 * @see  PageContext
	 */
	public Link(
		com.semanticcms.core.model.Link element,
		Path book,
		String page
	) {
		this(element, page);
		element.setBook(book);
	}

	/**
	 * Creates a new link in the current page context.
	 *
	 * @see  PageContext
	 */
	public Link(Path book, String page) {
		this(page);
		element.setBook(book);
	}

	/**
	 * Creates a new link in the current page context.
	 *
	 * @see  PageContext
	 */
	public Link(
		com.semanticcms.core.model.Link element,
		DomainName domain,
		Path book,
		String page
	) {
		this(element, book, page);
		element.setDomain(domain);
	}

	/**
	 * Creates a new link in the current page context.
	 *
	 * @see  PageContext
	 */
	public Link(DomainName domain, Path book, String page) {
		this(book, page);
		element.setDomain(domain);
	}

	@Override
	public Link id(String id) {
		super.id(id);
		return this;
	}

	public Link domain(DomainName domain) {
		element.setDomain(domain);
		return this;
	}

	public Link book(Path book) {
		element.setBook(book);
		return this;
	}

	public Link page(String page) {
		element.setPagePath(page);
		return this;
	}

	public Link element(String element) {
		this.element.setElement(element);
		return this;
	}

	public Link allowGeneratedElement(boolean allowGeneratedElement) {
		element.setAllowGeneratedElement(allowGeneratedElement);
		return this;
	}

	public Link anchor(String anchor) {
		element.setAnchor(anchor);
		return this;
	}

	public Link view(String view) {
		element.setView(view);
		return this;
	}

	public Link small(boolean small) {
		element.setSmall(small);
		return this;
	}

	public Link params(HttpParameters params) {
		element.setParams(params);
		return this;
	}

	public Link clazz(String clazz) {
		element.setClazz(clazz);
		return this;
	}

	@Override
	public void writeTo(Writer out, ElementContext context) throws IOException, ServletException, SkipPageException {
		/* TODO: Move to renderer class
		LinkRenderer.writeLinkImpl(
			pageIndex,
			out,
			context,
			element
		);
		 */
	}
}
