package com.edcbo.research;

/**
 * Interface for optimizers that record optimization history.
 * Used for generating CEC2017 charts (Convergence, Diversity).
 */
public interface RecordableOptimizer {
    /**
     * Get the convergence curve (Best Fitness per iteration).
     */
    double[] getConvergenceCurve();

    /**
     * Run the optimization algorithm.
     * 
     * @return Best fitness value.
     */
    double optimize();

    /**
     * Get the diversity curve (Population Diversity per iteration).
     */
    double[] getDiversityCurve();

    /**
     * Get the trajectory curve (1st Dimension of the first/best agent).
     */
    double[] getTrajectoryCurve();

    /**
     * Get the average fitness curve (Mean fitness of population per iteration).
     */
    double[] getAverageFitnessCurve();

    /**
     * Get search history snapshots (Positions of all agents at specific
     * iterations).
     * Returns a list of population states (double[PopSize][Dimensions]).
     */
    java.util.List<double[][]> getSearchHistory();
}
