/*
 * DistributionLikelihoodParser.java
 *
 * Copyright (C) 2002-2009 Alexei Drummond and Andrew Rambaut
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
 * BEAST is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with BEAST; if not, write to the
 * Free Software Foundation, Inc., 51 Franklin St, Fifth Floor,
 * Boston, MA  02110-1301  USA
 */

package dr.inferencexml;

import dr.inference.distribution.EmpiricalDistributionLikelihood;
import dr.inference.distribution.SplineInterpolatedLikelihood;
import dr.inference.model.Likelihood;
import dr.inference.model.Statistic;
import dr.xml.*;

/**
 *
 */
public class EmpiricalDistributionLikelihoodParser extends AbstractXMLObjectParser {

    public static final String FILE_NAME = "fileName";
    public static final String DATA = "data";
    public static final String FROM = "from";
    public static final String TO = "to";
    public static final String SPLINE_INTERPOLATION = "splineInterpolation";
    public static final String DEGREE = "degree";


    public String getParserName() {
        return EmpiricalDistributionLikelihood.EMPIRICAL_DISTRIBUTION_LIKELIHOOD;
    }

    public Object parseXMLObject(XMLObject xo) throws XMLParseException {

        String fileName = xo.getStringAttribute(FILE_NAME);

        boolean splineInterpolation = xo.getAttribute(SPLINE_INTERPOLATION,false);
        int degree = xo.getAttribute(DEGREE,3); // Default is cubic-spline

        EmpiricalDistributionLikelihood likelihood;

        if (splineInterpolation) {
            if( degree < 1 )
                throw new XMLParseException("Spline degree must be greater than zero!");
            likelihood = new SplineInterpolatedLikelihood(fileName);
        } else
            likelihood = new EmpiricalDistributionLikelihood(fileName);

        XMLObject cxo1 = xo.getChild(DATA);
        final int from = cxo1.getAttribute(FROM, -1);
        int to = cxo1.getAttribute(TO, -1);
        if (from >= 0 || to >= 0) {
            if (to < 0) {
                to = Integer.MAX_VALUE;
            }
            if (!(from >= 0 && to >= 0 && from < to)) {
                throw new XMLParseException("ill formed from-to");
            }
            likelihood.setRange(from, to);
        }

        for (int j = 0; j < cxo1.getChildCount(); j++) {
            if (cxo1.getChild(j) instanceof Statistic) {

                likelihood.addData((Statistic) cxo1.getChild(j));
            } else {
                throw new XMLParseException("illegal element in " + cxo1.getName() + " element");
            }
        }

        return likelihood;
    }

    //************************************************************************
    // AbstractXMLObjectParser implementation
    //************************************************************************

    public XMLSyntaxRule[] getSyntaxRules() {
        return rules;
    }

    private final XMLSyntaxRule[] rules = {
            AttributeRule.newStringRule(FILE_NAME),
            AttributeRule.newBooleanRule(SPLINE_INTERPOLATION,true),
            AttributeRule.newIntegerRule(DEGREE,true),
            new ElementRule(DATA, new XMLSyntaxRule[]{
                    AttributeRule.newIntegerRule(FROM, true),
                    AttributeRule.newIntegerRule(TO, true),
                    new ElementRule(Statistic.class, 1, Integer.MAX_VALUE)
            })
    };

    public String getParserDescription() {
        return "Calculates the likelihood of some data given some empirically-generated distribution.";
    }

    public Class getReturnType() {
        return Likelihood.class;
    }
}