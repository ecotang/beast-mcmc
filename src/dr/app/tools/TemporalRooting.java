/*
 * RootToTip.java
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

package dr.app.tools;

import dr.evolution.tree.*;
import dr.evolution.util.*;
import dr.evolution.util.Date;
import dr.stats.Regression;
import dr.stats.DiscreteStatistics;
import dr.math.UnivariateMinimum;
import dr.math.UnivariateFunction;

import java.util.*;

/*
 * @author Andrew Rambaut
 */

public class TemporalRooting {

    private boolean contemporaneous = false;
    private final TaxonList taxa;
    private final Map<String, Double> dates;
    private boolean useTargetRate = false;
    private double targetRate = 0.0;

    public TemporalRooting(TaxonList taxa) {
        this.taxa = taxa;

        double dateMin = Double.MAX_VALUE;
        double dateMax = -Double.MAX_VALUE;

        dates = new HashMap<String, Double>();

        for (int i = 0; i < taxa.getTaxonCount(); i++) {
            Taxon taxon = taxa.getTaxon(i);
            Date date = (Date)taxon.getAttribute("date");
            double d = 0.0;
            if (date != null) {
                d = date.getAbsoluteTimeValue();
            }
            if (d > dateMax) {
                dateMax = d;
            }
            if (d < dateMin) {
                dateMin = d;
            }
            dates.put(taxon.getId(), d);
        }

        if (Math.abs(dateMax - dateMin) < 1.0E-8) {
            // probably contemporaneous tips
            contemporaneous = true;
        }
    }

    public void setTargetRate(double targetRate) {
        this.targetRate = targetRate;
    }

    public boolean isContemporaneous() {
        return contemporaneous;
    }

    public Tree findRoot(Tree tree) {

        double[] dates = getDates(tree);
        return findGlobalRoot(tree, dates);
    }

    public Regression getRootToTipRegression(Tree tree) {

        if (contemporaneous) {
            throw new IllegalArgumentException("Cannot do a root to tip regression on contemporaneous tips");
        }
        double[] dates = getDates(tree);
        double[] distances = getRootToTipDistances(tree);
        return new Regression(dates, distances);
    }

    private double[] getRootToTipDistances(Tree tree) {

        double[] d = new double[tree.getExternalNodeCount()];
        for (int i = 0; i < tree.getExternalNodeCount(); i++) {
            NodeRef tip = tree.getExternalNode(i);
            d[i] = getRootToTipDistance(tree, tip);
        }
        return d;
    }

    private double[] getDates(Tree tree) {
        double[] d = new double[tree.getExternalNodeCount()];
        for (int i = 0; i < tree.getExternalNodeCount(); i++) {
            NodeRef tip = tree.getExternalNode(i);
            Double date = dates.get(tree.getNodeTaxon(tip).getId());
            if (date == null) {
                throw new IllegalArgumentException("Taxon, " + tree.getNodeTaxon(tip) + ", not found in taxon list");
            }
            d[i] = date;
        }
        return d;
    }

    private Tree findGlobalRoot(final Tree source, final double[] dates) {

        FlexibleTree bestTree = new FlexibleTree(source);
        double minF = findLocalRoot(bestTree, dates);
        double minDiff = Double.MAX_VALUE;

        for (int i = 0; i < source.getNodeCount(); i++) {
            FlexibleTree tmpTree = new FlexibleTree(source);
            NodeRef node = tmpTree.getNode(i);
            if (!tmpTree.isRoot(node)) {
                double length = tmpTree.getBranchLength(node);
                tmpTree.changeRoot(node, length * 0.5, length * 0.5);

                double f = findLocalRoot(tmpTree, dates);
                if (useTargetRate) {
                    Regression r = getRootToTipRegression(tmpTree);
                    if (Math.abs(r.getGradient() - targetRate) < minDiff) {
                        minDiff = Math.abs(r.getGradient() - targetRate);
                        bestTree = tmpTree;
                    }
                } else {
                    if (f < minF) {
                        minF = f;
                        bestTree = tmpTree;
                    }
                }
            }
        }
        return bestTree;
    }

    private double findLocalRoot(final FlexibleTree tree, final double[] dates) {

        NodeRef node1 = tree.getChild(tree.getRoot(), 0);
        NodeRef node2 = tree.getChild(tree.getRoot(), 1);

        final double length1 = tree.getBranchLength(node1);
        final double length2 = tree.getBranchLength(node2);

        final double sumLength = length1 + length2;

        final Set<NodeRef> tipSet1 = Tree.Utils.getExternalNodes(tree, node1);
        final Set<NodeRef> tipSet2 = Tree.Utils.getExternalNodes(tree, node2);

        final double[] y = new double[tree.getExternalNodeCount()];

        UnivariateFunction f = new UnivariateFunction() {
            public double evaluate(double argument) {
                double l1 = argument * sumLength;

                for (NodeRef tip : tipSet1) {
                    y[tip.getNumber()] = getRootToTipDistance(tree, tip) - length1 + l1;
                }

                double l2 = (1.0 - argument) * sumLength;

                for (NodeRef tip : tipSet2) {
                    y[tip.getNumber()] = getRootToTipDistance(tree, tip) - length2 + l2;
                }

                if (!contemporaneous) {
                    Regression r = new Regression(dates, y);
                    return -r.getCorrelationCoefficient();
                } else {
                    return DiscreteStatistics.variance(y);
                }
            }

            public double getLowerBound() { return 0; }
            public double getUpperBound() { return 1.0; }
        };

        UnivariateMinimum minimum = new UnivariateMinimum();

        double x = minimum.findMinimum(f);

        double fminx = minimum.fminx;

        double l1 = x * sumLength;
        double l2 = (1.0 - x) * sumLength;

        tree.setBranchLength(node1, l1);
        tree.setBranchLength(node2, l2);

        return fminx;
    }

    private double getRootToTipDistance(Tree tree, NodeRef node) {
        double distance = 0;
        while (node != null) {
            distance += tree.getBranchLength(node);
            node = tree.getParent(node);
        }
        return distance;
    }


}