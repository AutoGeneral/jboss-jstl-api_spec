/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.taglibs.standard.tag.common.xml;

import javax.servlet.jsp.JspException;
import javax.servlet.jsp.PageContext;
import javax.servlet.jsp.tagext.Tag;
import javax.servlet.jsp.tagext.TagSupport;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.TransformerException;

import org.apache.xpath.VariableStack;
import org.apache.xpath.XPathContext;
import org.apache.xpath.objects.XBoolean;
import org.apache.xpath.objects.XNodeSet;
import org.apache.xpath.objects.XNumber;
import org.apache.xpath.objects.XObject;
import org.apache.xpath.objects.XString;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import java.lang.reflect.Field;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 */
public class XalanUtil {
    private static final DocumentBuilderFactory dbf;

    static {
        // from Java5 on DocumentBuilderFactory is thread safe and hence can be cached
        dbf = ModularUtil.createDocumentBuilderFactory();
        dbf.setNamespaceAware(true);
        dbf.setValidating(false);
    }



    /**
     * Return the XPathContext to be used for evaluating expressions.
     *
     * A new context is created based on an empty Document.
     *
     * @param child the tag whose context should be returned
     * @param pageContext the current page context
     * @return the XPath evaluation context
     */
    public static XPathContext getContext(Tag child, PageContext pageContext) {
		// if within a forEach tag, use its context
		ForEachTag forEachTag = (ForEachTag) TagSupport.findAncestorWithClass(child, ForEachTag.class);
		if (forEachTag != null) {
			/*
				Don't jump page contexts. This is a dirty hack, but solves the issue
				where a x:forEach in a tag would otherwise link up with the page
				context in the parent page rather than its own.
			 */
			final XPathContext context = forEachTag.getContext();
			final VariableStack variableStack = context.getVarStack();

			try {
				if (variableStack instanceof JSTLVariableStack) {
					final Field f = variableStack.getClass().getDeclaredField("pageContext");
					f.setAccessible(true);
					final Object pageContexObject = f.get(variableStack);
					if (pageContexObject instanceof PageContext) {
						final PageContext parentPageContext = (PageContext)pageContexObject;

						if (parentPageContext == pageContext) {
							return forEachTag.getContext();
						}
					}
				}
			} catch (final Exception ex) {
				throw new RuntimeException("There was an exception thrown inspecting the page context of the parent JSP element", ex);
			}
		}

		XPathContext context = new XPathContext(false);
        VariableStack variableStack = new JSTLVariableStack(pageContext);
        context.setVarStack(variableStack);
        int dtm = context.getDTMHandleFromNode(newEmptyDocument());
        context.pushCurrentNodeAndExpression(dtm, dtm);
        return context;
    }

    /**
     * Create a new empty document.
     *
     * This method always allocates a new document as its root node might be
     * exposed to other tags and potentially be mutated.
     *
     * @return a new empty document
     */
    private static Document newEmptyDocument() {
        try {
            DocumentBuilder db = dbf.newDocumentBuilder();
            return db.newDocument();
        } catch (ParserConfigurationException e) {
            throw new AssertionError();
        }
    }

    /**
     * Return the Java value corresponding to an XPath result.
     *
     * @param xo the XPath type
     * @return the corresponding Java value per the JSTL mapping rules
     * @throws TransformerException if there was a problem converting the type
     */
    static Object coerceToJava(XObject xo) throws TransformerException {
        if (xo instanceof XBoolean) {
            return xo.bool();
        } else if (xo instanceof XNumber) {
            return xo.num();
        } else if (xo instanceof XString) {
            return xo.str();
        } else if (xo instanceof XNodeSet) {
            NodeList nodes = xo.nodelist();
            // if there is only one node in the nodeset return it rather than the list
            if (nodes.getLength() == 1) {
                return nodes.item(0);
            } else {
                return nodes;
            }
        } else {
            // unexpected result type
            throw new AssertionError();
        }
    }
}
