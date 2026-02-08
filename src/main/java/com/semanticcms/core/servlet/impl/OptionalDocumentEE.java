/*
 * semanticcms-core-servlet - Java API for modeling web page content and relationships in a Servlet environment.
 * Copyright (C) 2026  AO Industries, Inc.
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
 * along with semanticcms-core-servlet.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.semanticcms.core.servlet.impl;

import com.aoapps.html.servlet.DocumentEE;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletRequest;

/**
 * The dependency for {@link DocumentEE} is optional.
 */
final class OptionalDocumentEE {

  /** Make no instances. */
  private OptionalDocumentEE() {
    throw new AssertionError();
  }

  /**
   * @see DocumentEE#getAutonli(jakarta.servlet.ServletContext, jakarta.servlet.ServletRequest)
   */
  static boolean getAutonli(ServletContext servletContext, ServletRequest request) {
    return DocumentEE.getAutonli(servletContext, request);
  }

  /**
   * @see DocumentEE#replaceAutonli(jakarta.servlet.ServletRequest, java.lang.Boolean)
   */
  static Boolean replaceAutonli(ServletRequest request, Boolean autonli) {
    return DocumentEE.replaceAutonli(request, autonli);
  }

  /**
   * @see DocumentEE#setAutonli(jakarta.servlet.ServletRequest, java.lang.Boolean)
   */
  static void setAutonli(ServletRequest request, Boolean autonli) {
    DocumentEE.setAutonli(request, autonli);
  }

  /**
   * @see DocumentEE#getIndent(jakarta.servlet.ServletContext, jakarta.servlet.ServletRequest)
   */
  static boolean getIndent(ServletContext servletContext, ServletRequest request) {
    return DocumentEE.getIndent(servletContext, request);
  }

  /**
   * @see DocumentEE#replaceIndent(jakarta.servlet.ServletRequest, java.lang.Boolean)
   */
  static Boolean replaceIndent(ServletRequest request, Boolean indent) {
    return DocumentEE.replaceIndent(request, indent);
  }

  /**
   * @see DocumentEE#setIndent(jakarta.servlet.ServletRequest, java.lang.Boolean)
   */
  static void setIndent(ServletRequest request, Boolean indent) {
    DocumentEE.setAutonli(request, indent);
  }
}
