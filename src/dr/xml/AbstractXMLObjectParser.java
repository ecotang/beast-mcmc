/*
 * AbstractXMLObjectParser.java
 *
 * Copyright (C) 2002-2006 Alexei Drummond and Andrew Rambaut
 *
 * This file is part of BEAST.
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership and licensing.
 *
 * BEAST is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 *  BEAST is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with BEAST; if not, write to the
 * Free Software Foundation, Inc., 51 Franklin St, Fifth Floor,
 * Boston, MA  02110-1301  USA
 */

package dr.xml;

import org.w3c.dom.NamedNodeMap;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

public abstract class AbstractXMLObjectParser implements XMLObjectParser {

    public final Object parseXMLObject(XMLObject xo, String id, ObjectStore store, boolean strictXML)
            throws XMLParseException {

        this.store = store;

        if (hasSyntaxRules()) {
            final XMLSyntaxRule[] rules = getSyntaxRules();
            for (XMLSyntaxRule rule : rules) {
                if (!rule.isSatisfied(xo)) {
                    throw new XMLParseException("The '<" + getParserName() +
                            ">' element with id, '" + id +
                            "', is incorrectly constructed.\nThe following was expected:\n" +
                            rule.ruleString(xo));
                }
            }

            // Look for undeclared attributes and issue a warning
            final NamedNodeMap attributes = xo.getAttributes();
            for (int k = 0; k < attributes.getLength(); ++k) {
                String name = attributes.item(k).getNodeName();
                if (name.equals(XMLObject.ID)) continue;

                for (XMLSyntaxRule rule : rules) {
                    if (rule.containsAttribute(name)) {
                        name = null;
                        break;
                    }
                }
                if (name != null) {
                    final String msg = "unhandled attribute (typo?) " + name + " in " + xo;
                    if (strictXML) {
                        throw new XMLParseException(msg);
                    }
                    System.err.println("WARNING:" + msg);

                }
            }

            // try to catch out of place elements, placed either by mistake or from older incompatible files.
            
            for (int k = 0; k < xo.getChildCount(); ++k) {
                final Object child = xo.getChild(k);
                String unexpectedName;
                if (child instanceof XMLObject) {
                    final XMLObject ch = (XMLObject) child;
                    unexpectedName = !isAllowed(ch.getName()) ? ch.getName() : null;
                    final List<String> unexpected = isUnexpected(ch);
                    if( unexpected != null ) {
                        String n = "";
                        for(int j = 0; j < unexpected.size(); j += 2) {
                           n = n + ", " + unexpected.get(j) + " in " + unexpected.get(j+1);
                        }
                        if( unexpectedName == null ) {
                            unexpectedName = n.substring(1, n.length()) ;
                        } else {
                           unexpectedName = unexpectedName + n;
                        }
                    }
                } else {
                    unexpectedName = child.getClass().getName();
                    for (XMLSyntaxRule rule : rules) {
                        if (rule.isAllowed(child.getClass())) {
                            unexpectedName = null;
                            break;
                        }
                    }
                }
                if( unexpectedName != null ) {

                    String msg = "unexpected element in " + xo + ": " + unexpectedName;
                    if (strictXML) {
                        throw new XMLParseException(msg);
                    }
                    System.err.println("WARNING: " + msg);
                }
            }
        }

        try {
            return parseXMLObject(xo);
        } catch (XMLParseException xpe) {
            throw new XMLParseException("Error parsing '<" + getParserName() +
                    ">' element with id, '" + id + "':\n" +
                    xpe.getMessage());
        }
    }

    public String[] getParserNames() {
        return new String[]{getParserName()};
    }

    public final void throwUnrecognizedElement(XMLObject xo) throws XMLParseException {
        throw new XMLParseException("Unrecognized element '<" + xo.getName() + ">' in element '<" + getParserName() + ">'");
    }

    public abstract Object parseXMLObject(XMLObject xo) throws XMLParseException;

    /**
     * @return an array of syntax rules required by this element.
     *         Order is not important.
     */
    public abstract XMLSyntaxRule[] getSyntaxRules();

    /**
     * Allowed if any of the rules allows that element
     * @param elementName
     * @return
     */
    public final boolean isAllowed(String elementName) {
        final XMLSyntaxRule[] rules = getSyntaxRules();
        if (rules != null && rules.length > 0) {
            for (XMLSyntaxRule rule : rules) {
                if (rule.isAllowed(elementName)) {
                    return true;
                }
            }
        }

        return false;
    }

    public final List<String> isUnexpected(XMLObject element) {
        List<String> un = null;
        final XMLSyntaxRule[] rules = getSyntaxRules();
        if (rules != null && rules.length > 0) {
            for (XMLSyntaxRule rule : rules) {
                if (rule.isAllowed(element.getName())) {
                    for(int nc = 0; nc < element.getChildCount(); ++nc) {
                        final Object child = element.getChild(nc);
                        if( child instanceof XMLObject ) {
                            final String name = ((XMLObject) child).getName();
                            if( ! rule.isAllowed(name) ) {
                                if( un == null ) {
                                    un = new ArrayList<String>();
                                }
                                un.add(name);
                                un.add(element.getName());
                            }
                        }
                    }
                }
            }
        }

        return un;
    }

    public abstract String getParserDescription();

    public abstract Class getReturnType();

    public final boolean hasExample() {
        return getExample() != null;
    }

    public String getExample() {
        return null;
    }

    public final ObjectStore getStore() {
        return store;
    }

    /**
     * @return a description of this parser as a string.
     */
    public final String toHTML(XMLDocumentationHandler handler) {
        StringBuffer buffer = new StringBuffer();
        buffer.append("<div id=\"").append(getParserName()).append("\" class=\"element\">\n");
        buffer.append("  <div class=\"elementheader\">\n");
        buffer.append("    <span class=\"elementname\">").append(getParserName()).append("</span> element\n");
        buffer.append("    <div class=\"description\">\n");
        buffer.append("      ").append(getParserDescription()).append("\n");
        buffer.append("    </div>\n");
        buffer.append("  </div>\n");
        if (hasSyntaxRules()) {
            XMLSyntaxRule[] rules = getSyntaxRules();
            buffer.append("  <div class=\"rules\">\n");
            for (XMLSyntaxRule rule : rules) {
                buffer.append(rule.htmlRuleString(handler));
            }
            buffer.append("  </div>\n");
        }
        buffer.append("<div class=\"example\">");
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        handler.outputExampleXML(pw, this);
        pw.flush();
        pw.close();
        buffer.append(sw.toString());
        buffer.append("</div>\n");
        buffer.append("</div>\n");
        return buffer.toString();
    }

    /**
     * @return a description of this parser as a string.
     */
    public final String toWiki(XMLDocumentationHandler handler) {
        StringBuffer buffer = new StringBuffer();
        buffer.append("===<code>&lt;").append(getParserName()).append("&gt;</code> element===\n\n");
        buffer.append(getParserDescription()).append("\n\n");

        if (hasSyntaxRules()) {
            XMLSyntaxRule[] rules = getSyntaxRules();
            List<XMLSyntaxRule> attributes = new ArrayList<XMLSyntaxRule>();
            List<XMLSyntaxRule> contents = new ArrayList<XMLSyntaxRule>();
            for (XMLSyntaxRule rule : rules) {
                if (rule instanceof AttributeRule) {
                    attributes.add(rule);
                } else {
                    contents.add(rule);
                }
            }

            if (attributes.size() > 0) {
                buffer.append("\nThe element takes following attributes:\n");
                for (XMLSyntaxRule rule : attributes) {
                    buffer.append(rule.wikiRuleString(handler, "*"));
                }
                buffer.append("\n");
            }

            if (contents.size() > 0) {
                buffer.append("\nThe element has the following contents:\n");
                for (XMLSyntaxRule rule : contents) {
                    buffer.append(rule.wikiRuleString(handler, "*"));
                }
                buffer.append("\n");
            }
        }
        buffer.append("\nExample:\n");
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        handler.outputExampleXML(pw, this);
        pw.flush();
        pw.close();
        buffer.append(sw.toString());
        buffer.append("\n");
        return buffer.toString();
    }

    /**
     * @return a description of this parser as a string.
     */
    public String toString() {
        StringBuffer buffer = new StringBuffer();
        buffer.append("\nELEMENT ").append(getParserName()).append("\n");

        if (hasSyntaxRules()) {
            XMLSyntaxRule[] rules = getSyntaxRules();
            for (XMLSyntaxRule rule : rules) {
                buffer.append("  ").append(rule.ruleString()).append("\n");
            }
        }
        return buffer.toString();
    }

    //************************************************************************
    // private methods
    //************************************************************************

    public final boolean hasSyntaxRules() {
        XMLSyntaxRule[] rules = getSyntaxRules();
        return (rules != null && rules.length > 0);
    }

    private ObjectStore store = null;
}
