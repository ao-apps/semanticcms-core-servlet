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
package com.semanticcms.core.servlet.impl;

import com.aoindustries.encoding.MediaWriter;
import static com.aoindustries.encoding.TextInXhtmlAttributeEncoder.textInXhtmlAttributeEncoder;
import static com.aoindustries.encoding.TextInXhtmlEncoder.encodeTextInXhtml;
import com.aoindustries.io.buffer.BufferResult;
import com.semanticcms.core.model.ElementContext;
import com.semanticcms.core.model.NodeBodyWriter;
import com.semanticcms.core.model.Page;
import com.semanticcms.core.servlet.PageIndex;
import java.io.IOException;
import java.io.Writer;
import java.util.List;

final public class HeadingImpl {

	public static void doAfterBody(com.semanticcms.core.model.Heading heading) {
		// Add to any parent heading
		com.semanticcms.core.model.Element parent = heading.getParentElement();
		while(parent != null) {
			if(parent instanceof com.semanticcms.core.model.Heading) {
				com.semanticcms.core.model.Heading parentHeading = (com.semanticcms.core.model.Heading)parent;
				parentHeading.addChildHeading(heading);
				return;
			}
			parent = parent.getParentElement();
		}
		// No parent heading, add as top-level heading to page
		Page page = heading.getPage();
		if(page != null) page.addTopLevelHeading(heading);
	}

	public static void writeHeading(
		Writer out,
		ElementContext context,
		com.semanticcms.core.model.Heading heading,
		PageIndex pageIndex
	) throws IOException {
		// If this is the first heading in the page, write the table of contents
		Page page = heading.getPage();
		if(page != null) {
			List<com.semanticcms.core.model.Heading> topLevelHeadings = page.getTopLevelHeadings();
			if(!topLevelHeadings.isEmpty() && topLevelHeadings.get(0) == heading) {
				context.include("/lib/docs/toc.inc.jsp", out);
			}
		}
		// Count the heading level by finding all headings in the parent elements
		int headingLevel = 2; // <h1> is reserved for page titles
		com.semanticcms.core.model.Element parentElement = heading.getParentElement();
		while(parentElement != null) {
			if(parentElement instanceof com.semanticcms.core.model.Heading) headingLevel++;
			parentElement = parentElement.getParentElement();
		}
		// Highest tag is <h6>
		if(headingLevel > 6) throw new IOException("Headings exceeded depth of h6 (including page as h1): headingLevel = " + headingLevel);

		out.write("<section><h");
		char headingLevelChar = (char)('0' + headingLevel);
		out.write(headingLevelChar);
		String id = heading.getId();
		if(id != null) {
			out.write(" id=\"");
			PageIndex.appendIdInPage(
				pageIndex,
				heading.getPage(),
				id,
				new MediaWriter(textInXhtmlAttributeEncoder, out)
			);
			out.write('"');
		}
		out.write('>');
		encodeTextInXhtml(heading.getLabel(), out);
		out.write("</h");
		out.write(headingLevelChar);
		out.write('>');
		BufferResult body = heading.getBody();
		if(body.getLength() > 0) {
			out.write("<div class=\"h");
			out.write(headingLevelChar);
			out.write("Content\">");
			body.writeTo(new NodeBodyWriter(heading, out, context));
			out.write("</div>");
		}
		out.write("</section>");
	}

	/**
	 * Make no instances.
	 */
	private HeadingImpl() {
	}
}
