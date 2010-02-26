package dr.app.beagle.evomodel.substmodel;

import dr.evolution.datatype.DataType;
import dr.evolution.datatype.GeneralDataType;
import dr.math.KroneckerOperation;
import dr.util.Citable;
import dr.util.Citation;
import dr.util.CommonCitations;
//import dr.math.matrixAlgebra.Vector;

import java.util.List;
import java.util.ArrayList;
import java.util.logging.Logger;

/**
 * @author Marc A. Suchard
 * @author Vladimir Minin
 *         <p/>
 *         A class for implementing a kronecker sum of CTMC models in BEAST using BEAGLE
 *         This work is supported by NSF grant 0856099
 *         <p/>
 *         O'Brien JD, Minin VN and Suchard MA (2009) Learning to count: robust estimates for labeled distances between
 *         molecular sequences. Molecular Biology and Evolution, 26, 801-814
 */
public class ProductChainSubstitutionModel extends BaseSubstitutionModel implements Citable {

    public ProductChainSubstitutionModel(String name, List<SubstitutionModel> baseModels) {

        super(name);

        this.baseModels = baseModels;
        numBaseModel = baseModels.size();

        if (numBaseModel == 0) {
            throw new RuntimeException("May not construct ProductChainSubstitutionModel with 0 base models");
        }

        stateSizes = new int[numBaseModel];
        stateCount = 1;
        for (int i = 0; i < numBaseModel; i++) {
            DataType dataType = baseModels.get(i).getDataType();
            stateSizes[i] = dataType.getStateCount();
            stateCount *= dataType.getStateCount();
        }

        String[] codeStrings = getCharacterStrings();

        dataType = new GeneralDataType(codeStrings);

        updateMatrix = true;

        Logger.getLogger("dr.app.beagle").info("\tConstructing a product chain substition model,  please cite:\n"
                + Citable.Utils.getCitationString(this));
    }

    public List<Citation> getCitations() {
        List<Citation> citations = new ArrayList<Citation>();
        citations.add(
                CommonCitations.OBRIEN_2009
        );
        return citations;
    }

    public EigenDecomposition getEigenDecomposition() {
        synchronized (this) {
            if (updateMatrix) {
                computeKroneckerSumsAndProducts();
            }
        }
        return eigenDecomposition;
    }

    private String[] getCharacterStrings() {
        String[] strings = null;
        for (int i = numBaseModel - 1; i >= 0; i--) {
            strings = recursivelyAppendCharacterStates(baseModels.get(i).getDataType(), strings);
        }

        return strings;
    }

    private String[] recursivelyAppendCharacterStates(DataType dataType, String[] inSubStates) {

        String[] subStates = inSubStates;
        if (subStates == null) {
            subStates = new String[]{""};
        }

        final int previousStateCount = subStates.length;
        final int inStateCount = dataType.getStateCount();
        String[] states = new String[previousStateCount * inStateCount];

        for (int i = 0; i < inStateCount; i++) {
            String code = dataType.getCode(i);
            for (int j = 0; j < previousStateCount; j++) {
                states[i * previousStateCount + j] = code + subStates[j];
            }
        }
        return states;
    }

// Function 'ind.codon.eigen' from MarkovJumps-R
//  rate.mat = kronecker.sum(kronecker.sum(codon1.eigen$rate.matrix, codon2.eigen$rate.matrix),codon3.eigen$rate.matrix)
//
//  stat = codon1.eigen$stationary%x%codon2.eigen$stationary%x%codon3.eigen$stationary
//
//  ident.vec = rep(1,length(codon1.eigen$stationary))
//
//  eigen.val = (codon1.eigen$values%x%ident.vec + ident.vec%x%codon2.eigen$values)%x%
//    ident.vec + ident.vec%x%ident.vec%x%codon3.eigen$values
//
//  right.eigen.vec = (codon1.eigen$vectors%x%codon2.eigen$vectors)%x%codon3.eigen$vectors
//
//  left.eigen.vec = t((t(codon1.eigen$invvectors)%x%t(codon2.eigen$invvectors))%x%
//    t(codon3.eigen$invvectors))

    public void getInfinitesimalMatrix(double[] out) {
        getEigenDecomposition(); // Updates rate matrix if necessary
        System.arraycopy(rateMatrix, 0, out, 0, stateCount * stateCount);
    }

    private void computeKroneckerSumsAndProducts() {

        int currentStateSize = stateSizes[0];
        double[] currentRate = new double[currentStateSize * currentStateSize];
        baseModels.get(0).getInfinitesimalMatrix(currentRate);
        EigenDecomposition currentED = baseModels.get(0).getEigenDecomposition();
        double[] currentEval = currentED.getEigenValues();
        double[] currentEvec = currentED.getEigenVectors();
//        currentEvec = new double[] {1, 2.0 / 3.0, 1, -1.0 / 3.0};
//        currentIevc = new double[] {1.0 / 3.0, 2.0 / 3.0, 1, -1};
        double[] currentIevcT = transpose(currentED.getInverseEigenVectors(), currentStateSize);

//        System.err.println("In kS&P");
//        double[] out = new double[currentStateSize * currentStateSize];
//        baseModels.get(0).getTransitionProbabilities(0.0, out);
//        printSquareMatrix(out, currentStateSize);
//        System.exit(-1);


        for (int i = 1; i < numBaseModel; i++) {
            SubstitutionModel nextModel = baseModels.get(i);
            int nextStateSize = stateSizes[i];
            double[] nextRate = new double[nextStateSize * nextStateSize];
            nextModel.getInfinitesimalMatrix(nextRate);
            currentRate = KroneckerOperation.sum(currentRate, currentStateSize, nextRate, nextStateSize);

            EigenDecomposition nextED = nextModel.getEigenDecomposition();
            double[] nextEval = nextED.getEigenValues();
            double[] nextEvec = nextED.getEigenVectors();
//            nextEvec = new double[] {1, 3.0 / 4.0, 1, -1.0 / 4.0};
            double[] nextIevcT = transpose(nextED.getInverseEigenVectors(), nextStateSize);
//            nextIevc = new double[] {1.0 / 4.0, 3.0 / 4.0, 1, -1};

//            System.err.println("evec0 = ");
//            printSquareMatrix(currentEvec, currentStateSize);
//            System.err.println("evec1 = ");
//            printSquareMatrix(nextEvec, nextStateSize);
//            System.exit(-1);


            currentEval = KroneckerOperation.sum(currentEval, nextEval);

            currentEvec = KroneckerOperation.product(
                    currentEvec, currentStateSize, currentStateSize,
                    nextEvec, nextStateSize, nextStateSize);

//            transpose(nextIevc, nextStateSize);
            currentIevcT = KroneckerOperation.product(
                    currentIevcT, currentStateSize, currentStateSize,
                    nextIevcT, nextStateSize, nextStateSize);
            currentStateSize *= nextStateSize;

        }
//        transpose(currentIevc, currentStateSize);

        rateMatrix = currentRate;

        eigenDecomposition = new EigenDecomposition(
                currentEvec,
                transpose(currentIevcT, currentStateSize),
                currentEval);
        updateMatrix = false;
    }

//   private static void printSquareMatrix(double[] A, int dim) {
//        double[] row = new double[dim];
//        for (int i = 0; i < dim; i++) {
//            System.arraycopy(A, i * dim, row, 0, dim);
//            System.err.println(new Vector(row));
//        }
//    }

    // transposes a square matrix

    private static double[] transpose(double[] mat, int dim) {
        double[] out = new double[dim * dim];
        for (int i = 0; i < dim; i++) {
            for (int j = 0; j < dim; j++) {
                out[j * dim + i] = mat[i * dim + j];
            }
        }
        return out;
    }

    public FrequencyModel getFrequencyModel() {
        throw new RuntimeException("KroneckerSumSubstitionModel does have a FrequencyModel");
    }

    protected void frequenciesChanged() {
        // Do nothing
    }

    protected void ratesChanged() {
        // Do nothing
    }

    protected void setupRelativeRates(double[] rates) {
        // Do nothing
    }

    private final int numBaseModel;
    private final List<SubstitutionModel> baseModels;
    private final int[] stateSizes;
    //    private final List<DataType> dataTypes;
    private double[] rateMatrix = null;
}
