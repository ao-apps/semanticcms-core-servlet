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

import com.aoindustries.encoding.MediaWriter;
import static com.aoindustries.encoding.TextInXhtmlAttributeEncoder.textInXhtmlAttributeEncoder;
import static com.aoindustries.encoding.TextInXhtmlEncoder.encodeTextInXhtml;
import com.aoindustries.io.buffer.BufferResult;
import com.aoindustries.web.page.ElementContext;
import com.aoindustries.web.page.NodeBodyWriter;
import com.aoindustries.web.page.Page;
import java.io.IOException;
import java.io.Writer;
import java.util.List;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.jsp.SkipPageException;

public class Heading extends Element<com.aoindustries.web.page.Heading> {

	public Heading(String label, String id) {
		super(new com.aoindustries.web.page.Heading(), id);
		element.setLabel(label);
	}

	public Heading(String label) {
		this(label, null);
	}

	private PageIndex pageIndex;
	@Override
	protected void doBody(HttpServletRequest request, HttpServletResponse response, CaptureLevel captureLevel, ElementBody<? super com.aoindustries.web.page.Heading> body) throws ServletException, IOException, SkipPageException {
		pageIndex = PageIndex.getCurrentPageIndex(request);
		super.doBody(request, response, captureLevel, body);
		// Add to any parent heading
		com.aoindustries.web.page.Element parent = element.getParentElement();
		while(parent != null) {
			if(parent instanceof com.aoindustries.web.page.Heading) {
				com.aoindustries.web.page.Heading parentHeading = (com.aoindustries.web.page.Heading)parent;
				parentHeading.addChildHeading(element);
				return;
			}
			parent = parent.getParentElement();
		}
		// No parent heading, add as top-level heading to page
		Page page = element.getPage();
		if(page != null) page.addTopLevelHeading(element);
	}

	@Override
	public void writeTo(Writer out, ElementContext context) throws IOException {
		// If this is the first heading in the page, write the table of contents
		Page page = element.getPage();
		if(page != null) {
			List<com.aoindustries.web.page.Heading> topLevelHeadings = page.getTopLevelHeadings();
			if(!topLevelHeadings.isEmpty() && topLevelHeadings.get(0) == element) {
				context.include("/lib/docs/toc.inc.jsp", out);
			}
		}
		// Count the heading level by finding all headings in the parent elements
		int headingLevel = 2; // <h1> is reserved for page titles
		com.aoindustries.web.page.Element parentElement = element.getParentElement();
		while(parentElement != null) {
			if(parentElement instanceof com.aoindustries.web.page.Heading) headingLevel++;
			parentElement = parentElement.getParentElement();
		}
		// Highest tag is <h6>
		if(headingLevel > 6) throw new IOException("Headings exceeded depth of h6 (including page as h1): headingLevel = " + headingLevel);

		out.write("<section><h");
		char headingLevelChar = (char)('0' + headingLevel);
		out.write(headingLevelChar);
		String id = element.getId();
		if(id != null) {
			out.write(" id=\"");
			PageIndex.appendIdInPage(
				pageIndex,
				element.getPage(),
				id,
				new MediaWriter(textInXhtmlAttributeEncoder, out)
			);
			out.write('"');
		}
		out.write('>');
		encodeTextInXhtml(element.getLabel(), out);
		out.write("</h");
		out.write(headingLevelChar);
		out.write('>');
		BufferResult body = element.getBody();
		if(body.getLength() > 0) {
			out.write("<div class=\"h");
			out.write(headingLevelChar);
			out.write("Content\">");
			body.writeTo(new NodeBodyWriter(element, out, context));
			out.write("</div>");
		}
		out.write("</section>");
	}
}
