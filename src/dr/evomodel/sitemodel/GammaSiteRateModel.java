/*
 * GammaSiteModel.java
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

package dr.evomodel.sitemodel;

import dr.inference.model.AbstractModel;
import dr.inference.model.Model;
import dr.inference.model.Parameter;
import dr.math.distributions.GammaDistribution;
import dr.xml.*;

import java.util.logging.Logger;

/**
 * GammaSiteModel - A SiteModel that has a gamma distributed rates across sites.
 *
 * @author Andrew Rambaut
 * @version $Id: GammaSiteModel.java,v 1.31 2005/09/26 14:27:38 rambaut Exp $
 */

public class GammaSiteRateModel extends AbstractModel
        implements SiteRateModel {

    public static final String SITE_RATE_MODEL = "siteRateModel";
    public static final String SUBSTITUTION_RATE = "substitutionRate";
    public static final String RELATIVE_RATE = "relativeRate";
    public static final String GAMMA_SHAPE = "gammaShape";
    public static final String GAMMA_CATEGORIES = "gammaCategories";
    public static final String PROPORTION_INVARIANT = "proportionInvariant";

    public GammaSiteRateModel() {
        this(
                null,
                null,
                0,
                null);
    }

    public GammaSiteRateModel(double alpha, int categoryCount) {
        this(
                null,
                new Parameter.Default(alpha),
                categoryCount,
                null);
    }

    public GammaSiteRateModel(double pInvar) {
        this(
                null,
                null,
                0,
                new Parameter.Default(pInvar));
    }

    public GammaSiteRateModel(double alpha, int categoryCount, double pInvar) {
        this(
                null,
                new Parameter.Default(alpha),
                categoryCount,
                new Parameter.Default(pInvar));
    }

    /**
     * Constructor for gamma+invar distributed sites. Either shapeParameter or
     * invarParameter (or both) can be null to turn off that feature.
     */
    public GammaSiteRateModel(
            Parameter muParameter,
            Parameter shapeParameter, int gammaCategoryCount,
            Parameter invarParameter) {

        super(SITE_RATE_MODEL);

        this.muParameter = muParameter;
        if (muParameter != null) {
            addParameter(muParameter);
            muParameter.addBounds(new Parameter.DefaultBounds(Double.POSITIVE_INFINITY, 0.0, 1));
        }

        this.shapeParameter = shapeParameter;
        if (shapeParameter != null) {
            this.categoryCount = gammaCategoryCount;

            addParameter(shapeParameter);
            shapeParameter.addBounds(new Parameter.DefaultBounds(1.0E3, 0.0, 1));
        } else {
            this.categoryCount = 1;
        }

        this.invarParameter = invarParameter;
        if (invarParameter != null) {
            this.categoryCount += 1;

            addParameter(invarParameter);
            invarParameter.addBounds(new Parameter.DefaultBounds(1.0, 0.0, 1));
        }

        categoryRates = new double[this.categoryCount];
        categoryProportions = new double[this.categoryCount];

        ratesKnown = false;
    }

    /**
     * set mu
     */
    public void setMu(double mu) {
        muParameter.setParameterValue(0, mu);
    }

    /**
     * @return mu
     */
    public final double getMu() {
        return muParameter.getParameterValue(0);
    }

    /**
     * set alpha
     */
    public void setAlpha(double alpha) {
        shapeParameter.setParameterValue(0, alpha);
        ratesKnown = false;
    }

    /**
     * @return alpha
     */
    public final double getAlpha() {
        return shapeParameter.getParameterValue(0);
    }

    public Parameter getMutationRateParameter() {
        return muParameter;
    }

    public Parameter getAlphaParameter() {
        return shapeParameter;
    }

    public Parameter getPInvParameter() {
        return invarParameter;
    }

    public void setMutationRateParameter(Parameter parameter) {
        if (muParameter != null) removeParameter(muParameter);
        muParameter = parameter;
        if (muParameter != null) addParameter(muParameter);
    }

    public void setAlphaParameter(Parameter parameter) {
        if (shapeParameter != null) removeParameter(shapeParameter);
        shapeParameter = parameter;
        if (shapeParameter != null) addParameter(shapeParameter);
    }

    public void setPInvParameter(Parameter parameter) {
        if (invarParameter != null) removeParameter(invarParameter);
        invarParameter = parameter;
        if (invarParameter != null) addParameter(invarParameter);
    }

    // *****************************************************************
    // Interface SiteRateModel
    // *****************************************************************

    public int getCategoryCount() {
        return categoryCount;
    }

    public double[] getCategoryRates() {
        synchronized (this) {
            if (!ratesKnown) {
                calculateCategoryRates();
            }
        }

        return categoryRates;
    }

    public double[] getCategoryProportions() {
        synchronized (this) {
            if (!ratesKnown) {
                calculateCategoryRates();
            }
        }

        return categoryProportions;
    }

    public double getRateForCategory(int category) {
        synchronized (this) {
            if (!ratesKnown) {
                calculateCategoryRates();
            }
        }
        double mu = 1.0;
        if (muParameter != null) {
            mu = muParameter.getParameterValue(0);
        }

        return categoryRates[category] * mu;
    }

    public double getProportionForCategory(int category) {
        synchronized (this) {
            if (!ratesKnown) {
                calculateCategoryRates();
            }
        }

        return categoryProportions[category];
    }

    /**
     * discretization of gamma distribution with equal proportions in each
     * category
     */
    private void calculateCategoryRates() {

        double propVariable = 1.0;
        int cat = 0;

        if (invarParameter != null) {
            categoryRates[0] = 0.0;
            categoryProportions[0] = invarParameter.getParameterValue(0);

            propVariable = 1.0 - categoryProportions[0];
            cat = 1;
        }

        if (shapeParameter != null) {

            final double a = shapeParameter.getParameterValue(0);
            double mean = 0.0;
            final int gammaCatCount = categoryCount - cat;

            for (int i = 0; i < gammaCatCount; i++) {

                categoryRates[i + cat] = GammaDistribution.quantile((2.0 * i + 1.0) / (2.0 * gammaCatCount), a, 1.0 / a);
                mean += categoryRates[i + cat];

                categoryProportions[i + cat] = propVariable / gammaCatCount;
            }

            mean = (propVariable * mean) / gammaCatCount;

            for (int i = 0; i < gammaCatCount; i++) {

                categoryRates[i + cat] /= mean;
            }
        } else {
            categoryRates[cat] = 1.0 / propVariable;
            categoryProportions[cat] = propVariable;
        }


        ratesKnown = true;
    }

    // *****************************************************************
    // Interface ModelComponent
    // *****************************************************************

    protected void handleModelChangedEvent(Model model, Object object, int index) {
        // Substitution model has changed so fire model changed event
        listenerHelper.fireModelChanged(this, object, index);
    }

    protected final void handleParameterChangedEvent(Parameter parameter, int index, Parameter.ChangeType type) {
        if (parameter == shapeParameter) {
            ratesKnown = false;
        } else if (parameter == invarParameter) {
            ratesKnown = false;
        } else {
            // is the muParameter and nothing needs to be done
        }
        listenerHelper.fireModelChanged(this, parameter, index);
    }

    protected void storeState() {
    } // no additional state needs storing

    protected void restoreState() {
        ratesKnown = false;
    }

    protected void acceptState() {
    } // no additional state needs accepting

    public static XMLObjectParser PARSER = new AbstractXMLObjectParser() {

        public String getParserName() {
            return SITE_RATE_MODEL;
        }

        public Object parseXMLObject(XMLObject xo) throws XMLParseException {

            String msg = "";

            Parameter muParam = null;
            if (xo.hasChildNamed(SUBSTITUTION_RATE)) {
                muParam = (Parameter) xo.getElementFirstChild(SUBSTITUTION_RATE);

                msg += "\n  with initial substitution rate = " + muParam.getParameterValue(0);
            } else if (xo.hasChildNamed(RELATIVE_RATE)) {
                muParam = (Parameter) xo.getElementFirstChild(RELATIVE_RATE);

                msg += "\n  with initial relative rate = " + muParam.getParameterValue(0);
            }

            Parameter shapeParam = null;
            int catCount = 4;
            if (xo.hasChildNamed(GAMMA_SHAPE)) {
                XMLObject cxo = (XMLObject) xo.getChild(GAMMA_SHAPE);
                catCount = cxo.getIntegerAttribute(GAMMA_CATEGORIES);
                shapeParam = (Parameter) cxo.getChild(Parameter.class);

                msg += "\n  " + catCount + " category discrete gamma with initial shape = " + shapeParam.getParameterValue(0);
            }

            Parameter invarParam = null;
            if (xo.hasChildNamed(PROPORTION_INVARIANT)) {
                invarParam = (Parameter) xo.getElementFirstChild(PROPORTION_INVARIANT);
                msg += "\n  initial proportion of invariant sites = " + invarParam.getParameterValue(0);
            }

            if (msg.length() > 0) {
                Logger.getLogger("dr.evomodel").info("Creating site model: " + msg);
            } else {
                Logger.getLogger("dr.evomodel").info("Creating site model.");
            }

            return new GammaSiteRateModel(muParam, shapeParam, catCount, invarParam);

        }

        //************************************************************************
        // AbstractXMLObjectParser implementation
        //************************************************************************

        public String getParserDescription() {
            return "A SiteModel that has a gamma distributed rates across sites";
        }

        public Class getReturnType() {
            return GammaSiteRateModel.class;
        }

        public XMLSyntaxRule[] getSyntaxRules() {
            return rules;
        }

        private XMLSyntaxRule[] rules = new XMLSyntaxRule[]{
                new XORRule(
                        new ElementRule(SUBSTITUTION_RATE, new XMLSyntaxRule[]{
                                new ElementRule(Parameter.class)
                        }),
                        new ElementRule(RELATIVE_RATE, new XMLSyntaxRule[]{
                                new ElementRule(Parameter.class)
                        }), true
                ),
                new ElementRule(GAMMA_SHAPE, new XMLSyntaxRule[]{
                        AttributeRule.newIntegerRule(GAMMA_CATEGORIES, true),
                        new ElementRule(Parameter.class)
                }, true),
                new ElementRule(PROPORTION_INVARIANT, new XMLSyntaxRule[]{
                        new ElementRule(Parameter.class)
                }, true)
        };
    };

    /**
     * mutation rate parameter
     */
    private Parameter muParameter;

    /**
     * shape parameter
     */
    private Parameter shapeParameter;

    /**
     * invariant sites parameter
     */
    private Parameter invarParameter;

    private boolean ratesKnown;

    private int categoryCount;

    private double[] categoryRates;

    private double[] categoryProportions;
}