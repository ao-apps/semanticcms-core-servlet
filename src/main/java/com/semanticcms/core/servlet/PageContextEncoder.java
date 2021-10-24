/*
 * semanticcms-core-servlet - Java API for modeling web page content and relationships in a Servlet environment.
 * Copyright (C) 2016, 2021  AO Industries, Inc.
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

import com.aoapps.encoding.TextInXhtmlAttributeEncoder;
import com.aoapps.encoding.TextInXhtmlEncoder;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * Convenience access to encoding to current PageContext through static import.
 *
 * @see  PageContext
 * @see  PrintWriter
 */
public final class PageContextEncoder {

	public static void encodeTextInXhtmlAttribute(char ch) throws IOException {
		TextInXhtmlAttributeEncoder.encodeTextInXhtmlAttribute(ch, PageContext.getOut());
	}

	public static void encodeTextInXhtmlAttribute(char[] cbuf) throws IOException {
		TextInXhtmlAttributeEncoder.encodeTextInXhtmlAttribute(cbuf, PageContext.getOut());
	}

	public static void encodeTextInXhtmlAttribute(char[] cbuf, int start, int len) throws IOException {
		TextInXhtmlAttributeEncoder.encodeTextInXhtmlAttribute(cbuf, start, len, PageContext.getOut());
	}

	public static void encodeTextInXhtmlAttribute(CharSequence cs) throws IOException {
		TextInXhtmlAttributeEncoder.encodeTextInXhtmlAttribute(cs, PageContext.getOut());
	}

	public static void encodeTextInXhtmlAttribute(CharSequence cs, int start, int end) throws IOException {
		TextInXhtmlAttributeEncoder.encodeTextInXhtmlAttribute(cs, start, end, PageContext.getOut());
	}

	public static void encodeTextInXhtml(char ch) throws IOException {
		TextInXhtmlEncoder.encodeTextInXhtml(ch, PageContext.getOut());
	}

	public static void encodeTextInXhtml(char[] cbuf) throws IOException {
		TextInXhtmlEncoder.encodeTextInXhtml(cbuf, PageContext.getOut());
	}

	public static void encodeTextInXhtml(char[] cbuf, int start, int len) throws IOException {
		TextInXhtmlEncoder.encodeTextInXhtml(cbuf, start, len, PageContext.getOut());
	}

	public static void encodeTextInXhtml(CharSequence cs) throws IOException {
		TextInXhtmlEncoder.encodeTextInXhtml(cs, PageContext.getOut());
	}

	public static void encodeTextInXhtml(CharSequence cs, int start, int end) throws IOException {
		TextInXhtmlEncoder.encodeTextInXhtml(cs, start, end, PageContext.getOut());
	}

	/**
	 * Make no instances.
	 */
	private PageContextEncoder() {
	}
}
