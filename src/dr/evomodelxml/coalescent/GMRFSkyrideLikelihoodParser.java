package dr.evomodelxml.coalescent;

import dr.evomodel.coalescent.GMRFSkyrideLikelihood;
import dr.evomodel.tree.TreeModel;
import dr.inference.model.MatrixParameter;
import dr.inference.model.Parameter;
import dr.xml.*;

import java.util.logging.Logger;

/**
 *
 */
public class GMRFSkyrideLikelihoodParser extends AbstractXMLObjectParser {

	public static final String SKYLINE_LIKELIHOOD = "gmrfSkyrideLikelihood";
    public static final String SKYRIDE_LIKELIHOOD = "skyrideLikelihood";

	public static final String POPULATION_PARAMETER = "populationSizes";
	public static final String GROUP_SIZES = "groupSizes";
	public static final String PRECISION_PARAMETER = "precisionParameter";
	public static final String POPULATION_TREE = "populationTree";
	public static final String LAMBDA_PARAMETER = "lambdaParameter";
	public static final String BETA_PARAMETER = "betaParameter";
	public static final String COVARIATE_MATRIX = "covariateMatrix";
	public static final String RANDOMIZE_TREE = "randomizeTree";
	public static final String TIME_AWARE_SMOOTHING = "timeAwareSmoothing";

    public String getParserName() {
        return SKYLINE_LIKELIHOOD;
    }

    public String[] getParserNames(){
        return new String[]{getParserName(), SKYRIDE_LIKELIHOOD}; // cannot duplicate 
    }

    public Object parseXMLObject(XMLObject xo) throws XMLParseException {

        XMLObject cxo = xo.getChild(POPULATION_PARAMETER);
        Parameter popParameter = (Parameter) cxo.getChild(Parameter.class);

        cxo = xo.getChild(PRECISION_PARAMETER);
        Parameter precParameter = (Parameter) cxo.getChild(Parameter.class);

        cxo = xo.getChild(POPULATION_TREE);
        TreeModel treeModel = (TreeModel) cxo.getChild(TreeModel.class);

        cxo = xo.getChild(GROUP_SIZES);
        Parameter groupParameter = (Parameter) cxo.getChild(Parameter.class);

        if (popParameter.getDimension() != groupParameter.getDimension())
            throw new XMLParseException("Population and group size parameters must have the same length");

        Parameter lambda;
        if (xo.getChild(LAMBDA_PARAMETER) != null) {
            cxo = xo.getChild(LAMBDA_PARAMETER);
            lambda = (Parameter) cxo.getChild(Parameter.class);
        } else {
            lambda = new Parameter.Default(1.0);
        }

        Parameter beta = null;
        if (xo.getChild(BETA_PARAMETER) != null) {
            cxo = xo.getChild(BETA_PARAMETER);
            beta = (Parameter) cxo.getChild(Parameter.class);
        }

        MatrixParameter dMatrix = null;
        if (xo.getChild(COVARIATE_MATRIX) != null) {
            cxo = xo.getChild(COVARIATE_MATRIX);
            dMatrix = (MatrixParameter) cxo.getChild(MatrixParameter.class);
        }

        boolean timeAwareSmoothing = GMRFSkyrideLikelihood.TIME_AWARE_IS_ON_BY_DEFAULT;
        if (xo.hasAttribute(TIME_AWARE_SMOOTHING)) {
            timeAwareSmoothing = xo.getBooleanAttribute(TIME_AWARE_SMOOTHING);
        }

        if ((dMatrix != null && beta == null) || (dMatrix == null && beta != null))
            throw new XMLParseException("Must specify both a set of regression coefficients and a design matrix.");

        if (dMatrix != null) {
            if (dMatrix.getRowDimension() != popParameter.getDimension())
                throw new XMLParseException("Design matrix row dimension must equal the population parameter length.");
            if (dMatrix.getColumnDimension() != beta.getDimension())
                throw new XMLParseException("Design matrix column dimension must equal the regression coefficient length.");
        }

        if (xo.hasAttribute(RANDOMIZE_TREE) && xo.getBooleanAttribute(RANDOMIZE_TREE))
            GMRFSkyrideLikelihood.checkTree(treeModel);

        Logger.getLogger("dr.evomodel").info("The " + SKYLINE_LIKELIHOOD + " has " +
                (timeAwareSmoothing ? "time aware smoothing" : "uniform smoothing"));

        return new GMRFSkyrideLikelihood(treeModel, popParameter, groupParameter, precParameter,
                lambda, beta, dMatrix, timeAwareSmoothing);
    }

    //************************************************************************
    // AbstractXMLObjectParser implementation
    //************************************************************************

    public String getParserDescription() {
        return "This element represents the likelihood of the tree given the population size vector.";
    }

    public Class getReturnType() {
        return GMRFSkyrideLikelihood.class;
    }

    public XMLSyntaxRule[] getSyntaxRules() {
        return rules;
    }

    private final XMLSyntaxRule[] rules = {
            new ElementRule(POPULATION_PARAMETER, new XMLSyntaxRule[]{
                    new ElementRule(Parameter.class)
            }),
            new ElementRule(PRECISION_PARAMETER, new XMLSyntaxRule[]{
                    new ElementRule(Parameter.class)
            }),
            new ElementRule(POPULATION_TREE, new XMLSyntaxRule[]{
                    new ElementRule(TreeModel.class)
            }),
            new ElementRule(GROUP_SIZES, new XMLSyntaxRule[]{
                    new ElementRule(Parameter.class)
            }),
            AttributeRule.newBooleanRule(RANDOMIZE_TREE, true),
            AttributeRule.newBooleanRule(TIME_AWARE_SMOOTHING, true),
    };

}
