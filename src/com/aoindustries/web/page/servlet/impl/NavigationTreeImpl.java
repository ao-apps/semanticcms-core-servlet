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
package com.aoindustries.web.page.servlet.impl;

import com.aoindustries.web.page.Element;
import com.aoindustries.web.page.Node;
import com.aoindustries.web.page.Page;
import com.aoindustries.web.page.PageRef;
import com.aoindustries.web.page.servlet.CaptureLevel;
import com.aoindustries.web.page.servlet.CapturePage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

final public class NavigationTreeImpl {

	public static <T> List<T> filterChildren(List<T> children, Set<T> pagesToInclude) {
		int size = children.size();
		if(size == 0) return children;
		List<T> filtered = new ArrayList<>(size);
		for(T child : children) {
			if(pagesToInclude.contains(child)) {
				filtered.add(child);
			}
		}
		return filtered;
	}

	public static List<Node> getChildNodes(
		ServletContext servletContext,
		HttpServletRequest request,
		HttpServletResponse response,
		boolean includeElements,
		boolean metaCapture,
		Node node
	) throws ServletException, IOException {
		// Both elements and pages are child nodes
		List<Element> childElements = includeElements ? node.getChildElements() : null;
		List<PageRef> childPages = (node instanceof Page) ? ((Page)node).getChildPages() : null;
		List<Node> childNodes = new ArrayList<>(
			(childElements==null ? 0 : childElements.size())
			+ (childPages==null ? 0 : childPages.size())
		);
		if(includeElements) {
			childNodes.addAll(childElements);
		}
		if(childPages != null) {
			for(PageRef childRef : childPages) {
				Page childPage = CapturePage.capturePage(servletContext, request, response, childRef, includeElements || metaCapture ? CaptureLevel.META : CaptureLevel.PAGE);
				childNodes.add(childPage);
			}
		}
		return childNodes;
	}

	/**
	 * Make no instances.
	 */
	private NavigationTreeImpl() {
	}
}
