/*
 * semanticcms-core-servlet - Java API for modeling web page content and relationships in a Servlet environment.
 * Copyright (C) 2013, 2014, 2015, 2016, 2020  AO Industries, Inc.
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

import javax.servlet.ServletRequest;

/**
 * The capture modes.
 */
public enum CaptureLevel {

	/**
	 * Captures page meta data only, such as title, copyright, authors, parents, and children.
	 */
	PAGE,

	/**
	 * Captures both page and content meta data.
	 */
	META,

	/**
	 * Captures everything: page meta data, content meta data, and all body HTML.
	 */
	BODY;

	private static final String CAPTURE_LEVEL_REQUEST_ATTRIBUTE = CaptureLevel.class.getName()+".captureLevel";

	/**
	 * Gets the capture level or <code>BODY</code> if none occurring.
	 */
	public static CaptureLevel getCaptureLevel(ServletRequest request) {
		CaptureLevel captureLevel = (CaptureLevel)request.getAttribute(CAPTURE_LEVEL_REQUEST_ATTRIBUTE);
		return captureLevel == null ? BODY : captureLevel;
	}

	static void setCaptureLevel(ServletRequest request, CaptureLevel level) {
		request.setAttribute(CAPTURE_LEVEL_REQUEST_ATTRIBUTE, level);
	}
}
