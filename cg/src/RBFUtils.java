import java.util.Arrays;

/**
 * Created by tanczosm on 10/1/2015.
 */
public class RBFUtils {
    // min is smallest possible value in range, and max is largest possible value in range
    // This method simply calculates an evenly spaced spread of features between min and max
    public static double[] getCenters (double min, double max, int numberFeatures)
    {
        double maxdist = max - min;

        assert maxdist > 0 : "The difference between max and min must be greater than zero";
        assert numberFeatures > 0 : "The number of features must be greater than zero";

        double gap = maxdist / (numberFeatures - 1);
        double[] result = new double[numberFeatures];

        for (int i = 0; i < numberFeatures; i++)
        {
            result[i] = min + (i*gap);
        }

        return result;
    }

    public static double[] processDataIntoFeatures (double data, double width, double[] centers)
    {
        double[] result = new double[centers.length];

        for (int i = 0; i < centers.length; i++)
        {
            result[i] = Math.pow(data - centers[i], 2) / width;
            result[i] = Math.exp(-result[i]);
        }

        return result;
    }

    /*
    TESTING CODE
     */
    /*
    public static void main (String[] args)
    {
        int featureCount = 11;
        double min = -2;
        double max = 2;

        // Value we want to run the RBF on within this range
        double data = 0;

        double[] centers = getCenters(min, max, featureCount);
        double[] out = processDataIntoFeatures(data, max, centers);
        System.out.println(Arrays.toString(centers));
        System.out.println(Arrays.toString(out));
    }
    */
}
