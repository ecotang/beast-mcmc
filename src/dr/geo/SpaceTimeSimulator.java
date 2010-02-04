/*
 * SpaceTimeSimulator.java
 *
 * Copyright (C) 2002-2010 Alexei Drummond and Andrew Rambaut
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

package dr.geo;

import dr.math.distributions.MultivariateNormalDistribution;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Alexei Drummond
 */
public class SpaceTimeSimulator {

    // the diffusion kernel
    MultivariateNormalDistribution D;

    public SpaceTimeSimulator(MultivariateNormalDistribution D) {
        this.D = D;
    }

    /**
     * @param start    the start time and location
     * @param rejector
     * @param dt       the time steps
     * @param steps    the number of steps
     * @return a path in space starting at start and continuing to time t = start.time + dt*steps
     */
    public List<SpaceTime> simulatePath(SpaceTime start, SpaceTimeRejector rejector, double dt, int steps) {

        ArrayList<SpaceTime> list = new ArrayList<SpaceTime>();
        list.add(start);
        for (int i = 0; i < steps; i++) {

            SpaceTime lastSpaceTime = list.get(list.size() - 1);
            SpaceTime newST;
            do {
                double[] newPoint = D.nextScaledMultivariateNormal(lastSpaceTime.getX(), dt);
                newST = new SpaceTime(lastSpaceTime.getTime() + dt, newPoint);
            } while (rejector.reject(newST, 0));
            list.add(newST);
        }
        return list;
    }

    /**
     * @param spaceTime the start time and location
     * @param rejector
     * @param dt        the time steps
     * @param steps     the number of steps
     * @return a path in space starting at start and continuing to time t = start.time + dt*steps
     */
    public SpaceTime simulate(SpaceTime spaceTime, SpaceTimeRejector rejector, double dt, int steps) {

        SpaceTime newST = new SpaceTime(spaceTime);
        SpaceTime nextST = new SpaceTime(spaceTime);
        for (int i = 0; i < steps; i++) {

            do {
                double[] newPoint = D.nextScaledMultivariateNormal(nextST.getX(), dt);
                newST.time = nextST.getTime() + dt;
                newST.space = newPoint;
            } while (rejector.reject(newST, 0));
            nextST.time = newST.time;
            nextST.space = newST.space;
        }
        return nextST;
    }
}