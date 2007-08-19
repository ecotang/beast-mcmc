/*
 * CenteredScaleOperator.java
 *
 * Copyright (C) 2002-2007 Alexei Drummond and Andrew Rambaut
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

package dr.inference.operators;

import dr.inference.distribution.MixedDistributionLikelihood;
import dr.inference.model.Parameter;
import dr.inference.model.Statistic;
import dr.math.MathUtils;
import dr.xml.*;

/**
 * Given a values vector (data) and an indicators vector (boolean vector indicating wheather the corrosponding value
 * is used or ignored), this operator explores all possible positions for the used data points while preserving their
 * order.
 * The distribition is uniform on all possible data positions.
 * <p/>
 * For example, if data values A and B are used in a vector of dimension 4, each of the following states is visited 1/6
 * of the time.
 * <p/>
 * ABcd 1100
 * AcBd 1010
 * AcdB 1001
 * cABd 0110
 * cAdB 0101
 * cdAB 0011
 * <p/>
 * The operator works by picking a 1 bit in the indicators and swapping it with a neighbour 0, with the appropriate
 * adjustment to the hastings ratio since a pair of 1,1 and 0,0 are never swapped, and the ends can be swapped in one
 * direction only.
 *
 * @author Joseph Heled
 * @version $Id$
 */
public class BitSwapOperator extends SimpleMCMCOperator {

    public static final String BIT_SWAP_OPERATOR = "bitSwapOperator";
    public static final String RADIUS = "radius";

    private Parameter data;
    private Parameter indicators;
    private final boolean impliedOne;
    private int radius;

    public BitSwapOperator(Parameter data, Parameter indicators, int radius, double weight) {
        this.data = data;
        this.indicators = indicators;
        this.radius = radius;
        setWeight(weight);

        final int iDim = indicators.getDimension();
        final int dDim = data.getDimension();
        if (iDim == dDim - 1) {
            impliedOne = true;
        } else if (iDim == dDim) {
            impliedOne = false;
        } else {
            throw new IllegalArgumentException();
        }
    }


    public String getPerformanceSuggestion() {
        return "";
    }

    public String getOperatorName() {
        return "bitSwap(" + data.getParameterName() + ")";
    }

    public double doOperation() throws OperatorFailedException {
        final int dim = indicators.getDimension();
        if (dim < 2) {
            throw new OperatorFailedException("no swaps possible");
        }
        int nLoc = 0;
        int[] loc = new int[2 * dim];
        double hastingsRatio;
        int pos;
        int direction;

        int nOnes = 0;
        if (radius > 0) {
            for (int i = 0; i < dim; i++) {
                final double value = indicators.getStatisticValue(i);
                if (value > 0) {
                    ++nOnes;
                    loc[nLoc] = i;
                    ++nLoc;
                }
            }

            if (nOnes == 0 || nOnes == dim) {
                throw new OperatorFailedException("no swaps possible");  //??
                //return 0;
            }

            hastingsRatio = 0.0;
            final int rand = MathUtils.nextInt(nLoc);
            pos = loc[rand];
            direction = MathUtils.nextInt(2 * radius);
            direction -= radius - (direction < radius ? 0 : 1);
            for (int i = direction > 0 ? pos + 1 : pos + direction; i < (direction > 0 ? pos + direction + 1 : pos); i++)
            {
                if (i < 0 || i >= dim || indicators.getStatisticValue(i) > 0) {
                    throw new OperatorFailedException("swap faild");
                }
            }
        } else {
            double prev = -1;
            for (int i = 0; i < dim; i++) {
                final double value = indicators.getStatisticValue(i);
                if (value > 0) {
                    ++nOnes;
                    if (i > 0 && prev == 0) {
                        loc[nLoc] = -(i + 1);
                        ++nLoc;
                    }
                    if (i < dim - 1 && indicators.getStatisticValue(i + 1) == 0) {
                        loc[nLoc] = (i + 1);
                        ++nLoc;
                    }
                }
                prev = value;
            }

            if (nOnes == 0 || nOnes == dim) {
                return 0;
            }

            if (!(nLoc > 0)) {
                // System.out.println(indicators);
                assert false : indicators;
            }

            final int rand = MathUtils.nextInt(nLoc);
            pos = loc[rand];
            direction = pos < 0 ? -1 : 1;
            pos = (pos < 0 ? -pos : pos) - 1;
            final int maxOut = 2 * nOnes;

            hastingsRatio = (maxOut == nLoc) ? 0.0 : Math.log((double) nLoc / maxOut);
        }

//            System.out.println("swap " + pos + "<->" + nto + "  " +
//                              indicators.getParameterValue(pos) +  "<->" + indicators.getParameterValue(nto) +
//                 "  " +  data.getParameterValue(pos) +  "<->" + data.getParameterValue(nto));
        final int nto = pos + direction;
        double vto = indicators.getStatisticValue(nto);

        indicators.setParameterValue(nto, indicators.getParameterValue(pos));
        indicators.setParameterValue(pos, vto);

        final int dataOffset = impliedOne ? 1 : 0;
        final int ntodata = nto + dataOffset;
        final int posdata = pos + dataOffset;
        vto = data.getStatisticValue(ntodata);
        data.setParameterValue(ntodata, data.getParameterValue(posdata));
        data.setParameterValue(posdata, vto);

//            System.out.println("after " + pos + "<->" + nto + "  " +
//                              indicators.getParameterValue(pos) +  "<->" + indicators.getParameterValue(nto) +
//                 "  " +  data.getParameterValue(pos) +  "<->" + data.getParameterValue(nto));

        return hastingsRatio;
    }

    private static final String DATA = MixedDistributionLikelihood.DATA;
    private static final String INDICATORS = MixedDistributionLikelihood.INDICATORS;
    public static XMLObjectParser PARSER = new AbstractXMLObjectParser() {

        public String getParserName() {
            return BIT_SWAP_OPERATOR;
        }

        public Object parseXMLObject(XMLObject xo) throws XMLParseException {

            final double weight = xo.getDoubleAttribute(WEIGHT);
            Parameter data = (Parameter) ((XMLObject) xo.getChild(DATA)).getChild(Parameter.class);
            Parameter indicators = (Parameter) ((XMLObject) xo.getChild(INDICATORS)).getChild(Parameter.class);
            int radius = -1;

            if (xo.hasAttribute(RADIUS)) {
                double rd = xo.getDoubleAttribute(RADIUS);

                if (rd > 0) {
                    if (rd < 1) {
                        rd = Math.round(rd * indicators.getDimension());
                    }
                    radius = (int) Math.round(rd);
                    if (!(radius >= 1 && radius < indicators.getDimension() - 1)) {
                        radius = -1;
                    }
                }
                if (radius < 1) {
                    throw new XMLParseException("invalid radius " + rd);
                }
            }

            return new BitSwapOperator(data, indicators, radius, weight);
        }

        //************************************************************************
        // AbstractXMLObjectParser implementation
        //************************************************************************

        public String getParserDescription() {
            return "This element returns a bit-swap operator on a given parameter and data.";
        }

        public Class getReturnType() {
            return MCMCOperator.class;
        }

        public XMLSyntaxRule[] getSyntaxRules() {
            return rules;
        }

        private XMLSyntaxRule[] rules = new XMLSyntaxRule[]{
                AttributeRule.newDoubleRule(WEIGHT),
                new ElementRule(DATA, new XMLSyntaxRule[]{new ElementRule(Statistic.class)}),
                new ElementRule(INDICATORS, new XMLSyntaxRule[]{new ElementRule(Statistic.class)}),
        };

    };
}
