import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

public class EdgeDetector {
    private static int xPixels = 0;
    private static int yPixels = 0;
    private static double[][] GaussianFilter;
    private static double[][] SobelX;
    private static double[][] SobelY;

    public static void main(String[] args) {
        if(args.length < 1) {
            System.out.println("Goodbye");
            System.exit(0);
        }
        Image image = loadImage(args);
        String outputFileName = getOutputFileName(args);
        initArrays();
        double[][] l = image.getLightnessArray();

        l = blur(l);

        double[][][] gradient = gradient(l);

        double[][][] edges = suppressNonMax(gradient[0], gradient[1]);

        l = edgeTrack(edges[0], edges[1], edges[2]);

        output(outputFileName, l);
    }

    /**
     * Thins edges by only keeping a pixel if its gradient is greater than the gradients of the 2 pixels adjacent in
     * the direction of the pixel's own gradient.
     * Then marks each pixel as strong (if above some upper threshold) or weak (if between upper and lower thresholds)
     * or suppresses it (if below lower threshold).
     * Weak pixels can then be considered for edge tracking (hysteresis)
     */
    public static double[][][] suppressNonMax(double[][] magnitudes, double[][] angles) {
        double[][] newMatrix = new double[xPixels][yPixels];
        double[][] strongPixels = new double[xPixels][yPixels];
        double[][] weakPixels = new double[xPixels][yPixels];
        double maxGradient = arrayMax(magnitudes);
        double highThreshold = 0.1 * maxGradient;                               //rather arbitrary values for the upper and lower thresholds that I found to work well:
        double lowThreshold = 0.05 * highThreshold;                               //0.3, 0.5 for less noise; 0.15, 0.3 for a good medium, and 0.1, 0.05 for more detail
        for(int x = 0; x < xPixels; x++) {
            for (int y = 0; y < yPixels; y++) {
                double angle = angles[x][y];
                double currValue = magnitudes[x][y];
                double one = 0; double two = 0;                                 //gradients of pixels adjacent in direction of current pixel's gradient

                int addx = 0; int addy = 0; int subx = 0; int suby = 0;         //to check for indices that are out of bounds
                if(x > 0) subx = 1;
                if(y > 0) suby = 1;
                if(x < xPixels-1) addx = 1;
                if(y < yPixels-1) addy = 1;

                if(angle < 0 || angle > 180) {                                  //just in case
                    System.out.println("Angle out of bounds");
                }
                if(angle <= 22.5 || angle > 157.5) {                            //for angles in [0,22.5] and (157.5,180]
                    one = magnitudes[x - subx][y];                              //compare to pixels along east-west axis
                    two = magnitudes[x + addx][y];
                }
                else if(angle > 22.5 && angle <= 67.5) {                        //for angles in (22.5, 67.5]
                    one = magnitudes[x - subx][y - suby];                       //compare to pixels along northwest-southeast axis
                    two = magnitudes[x + addx][y + addy];
                }
                else if(angle > 67.5 && angle <= 112.5) {                       //for angles in (67.5, 112.5]
                    one = magnitudes[x][y - suby];                              //compare to pixels along north-south axis
                    two = magnitudes[x][y + addy];
                }
                else if(angle > 112.5 && angle <= 157.5) {                      //for angles in (112.5, 157.5]
                    one = magnitudes[x - subx][y + addy];                       //compare to pixels along southwest-northeast axis
                    two = magnitudes[x + addx][y - suby];
                }
                double s = 0;
                double w = 0;
                double newValue = 0;
                if(currValue >= one && currValue >= two) {                      //only keeps pixel if its gradient is greater than the gradients of both adjacent pixels
                    if(currValue >= highThreshold) {                            //now compares values to upper and lower thresholds;
                        newValue = 1;                                           //if above high threshold, pixel is marked strong and set to 1 (white)
                        s = 1; w = 0;                                           //if below low threshold, pixel is ignored
                    }                                                           //if between thresholds, pixel is marked weak for hysteresis and set to current gradient value
                    else if(currValue >= lowThreshold) {
                        newValue = magnitudes[x][y];
                        s = 0; w = 1;
                    }
                    else {
                        newValue = 0;
                        s = 0; w = 0;
                    }
                   }
                else {
                    newValue = 0;
                    s = 0; w = 0;
                }
                newMatrix[x][y] = newValue;
                strongPixels[x][y] = s;
                weakPixels[x][y] = w;
            }
        }
        return new double[][][]{newMatrix, strongPixels, weakPixels};
    }

    /**
     * Fills in gaps in edges by making each weak pixel strong if it is adjacent to an already strong pixel
     */
    public static double[][] edgeTrack(double[][] l, double[][] strong, double[][] weak) {
        double[][] newMatrix = new double[xPixels][yPixels];
        for (int x = 0; x < xPixels; x++) {
            for (int y = 0; y < yPixels; y++) {
                if(weak[x][y] == 1) {
                    int addx = 0; int addy = 0; int subx = 0; int suby = 0;   //checking for out of bounds indices again
                    if(x > 0) subx = 1;
                    if(y > 0) suby = 1;
                    if(x < xPixels-1) addx = 1;
                    if(y < yPixels-1) addy = 1;
                    if(     strong[x - subx][y - suby] == 1 ||
                            strong[x][y - suby] == 1 ||
                            strong[x + addx][y - suby] == 1 ||
                            strong[x -subx][y] == 1 ||
                            strong[x + addx][y] == 1 ||
                            strong[x - subx][y + addy] == 1 ||
                            strong[x][y + addy] == 1 ||
                            strong[x + addx][y + addy] == 1){
                        newMatrix[x][y] = 1;
                    }
                    else {
                        newMatrix[x][y] = 0;
                    }
                }
                else {
                    newMatrix[x][y] = l[x][y];
                }
            }
        }
        return newMatrix;
    }

    /**
     * Convolves the given channel (like r, g, b, or lightness) with the Gaussian filter to reduce noise
     */
    public static double[][] blur(double[][] channelArray) {
        return convolve(channelArray, GaussianFilter);
    }

    /**
     * Finds gradient magnitude and direction for each pixel and returns in separate arrays
     */
    public static double[][][] gradient(double[][] channelArray) {

        double[][] xGradient = convolve(channelArray, SobelX);
        double[][] yGradient = convolve(channelArray, SobelY);
        double[][] gradientMagnitude = new double[xPixels][yPixels];
        double[][] gradientDirection = new double[xPixels][yPixels];

        for(int x = 0; x < xPixels; x++) {
            for (int y = 0; y < yPixels; y++) {
                double a = xGradient[x][y];
                double b = yGradient[x][y];
                gradientMagnitude[x][y] = Math.sqrt(a*a + b*b);    //gradient = sqrt(x_gradient^2 + y_gradient^2)
                double c = Math.toDegrees(Math.atan2(b, a));       //angle of gradient = arctan(y_gradient / x_gradient)
                if(c < 0) c += 180;                                //angle is between -180 and 180 degrees, but only the axis (not sign) matters, so reduced to 0 to 180 space
                gradientDirection[x][y] = c;
            }
        }
        return new double[][][]{gradientMagnitude, gradientDirection};
    }

    /**
     * Takes in array of pixel values and the convolution filter and returns resulting array of values (same size as original)
     */
    public static double[][] convolve(double[][] original, double[][] cv) {
        double[][] newMatrix = new double[original.length][original[0].length];
        for(int x = 0; x < xPixels; x++) {
            for(int y = 0; y < yPixels; y++) {
                double newValue = 0;
                for(int e = 0; e < cv.length; e++) {          //number of rows
                    for(int f = 0; f < cv[0].length; f++) {   //number of columns
                        int dy = e - (int)(cv.length / 2);      //for checking boundary conditions; when convolution array goes off the edge,
                        int yIndex = y + dy;                    //the nearest edge pixel is used instead
                        if(y + dy < 0) {
                            yIndex = 0;
                        }
                        if(y + dy >= yPixels) {
                            yIndex = yPixels - 1;
                        }
                        int dx = f - (int)(cv[0].length / 2);
                        int xIndex = x + dx;
                        if(x + dx < 0) {
                            xIndex = 0;
                        }
                        if(x + dx >= xPixels) {
                            xIndex = xPixels - 1;
                        }
                        newValue += (original[xIndex][yIndex] * cv[e][f]);
                    }
                }
                newMatrix[x][y] = newValue;
            }
        }
        return newMatrix;
    }

    /**
     * Reads and returns the name of the output file
     */
    public static String getOutputFileName(String[] args) {
        if(args.length < 2) {
            return "output.png";
        }
        else return args[1];
    }

    /**
     * Creates and returns new Image from given file name; also sets xPixels and yPixels
     */
    public static Image loadImage(String[] args) {
        Image image = null;
        try {
            BufferedImage img = ImageIO.read(new File(args[0]));
            xPixels = img.getWidth();
            yPixels = img.getHeight();
            image = new Image(img);
        }
        catch(IOException e) {
            System.out.println("IO Exception: Not a valid file");
            System.exit(0);
        }
        return image;
    }

    /**
     * Finds maximum value in given array
     */
    public static double arrayMax(double[][] array) {
        double max = 0;
        for(int i = 0; i < array.length; i++) {
            for(int j = 0; j < array[0].length; j++) {
                if(array[i][j] > max) max = array[i][j];
            }
        }
        return max;
    }

    /**
     * Sets up a roughly Gaussian distributed array for blurring to reduce noise
     * and the Sobel arrays to find the X and Y gradients
     */
    public static void initArrays() {
        GaussianFilter = new double[][]{new double[]{2.0/159, 4.0/159, 5.0/159, 4.0/159, 2.0/159},
                         new double[]{4.0/159, 9.0/159, 12.0/159, 9.0/159, 4.0/159},
                         new double[]{5.0/159, 12.0/159, 15.0/159, 12.0/159, 5.0/159},
                         new double[]{4.0/159, 9.0/159, 12.0/159, 9.0/159, 4.0/159},
                         new double[]{2.0/159, 4.0/159, 5.0/159, 4.0/159, 2.0/159}};

        SobelY = new double[][]{new double[]{-1.0/4, -2.0/4, -1.0/4},
                new double[]{0.0, 0.0, 0.0},
                new double[]{1.0/4, 2.0/4, 1.0/4}};
        SobelX = new double[][]{new double[]{-1.0/4, 0.0, 1.0/4},
                new double[]{-2.0/4, 0.0, 2.0/4},
                new double[]{-1.0/4, 0.0, 1.0/4}};
    }


    /**
     * Creates new image, sets every pixel to the corresponding value in the given 2-dimensional array,
     * and saves to file with given name
     **/
    public static void output(String fileName, double[][] l) {
        BufferedImage b = new BufferedImage(xPixels, yPixels, BufferedImage.TYPE_4BYTE_ABGR);
        WritableRaster wr = b.getRaster();
        for (int x = 0; x < xPixels; x++) {
            for (int y = 0; y < yPixels; y++) {
                int lightness = Math.max(Math.min((int)(l[x][y] * 255), 255), 0);               //clamps values to between 0 and 255
                int[] color = {lightness, lightness, lightness, 255};
                wr.setPixel(x, y, color);
            }
        }
        try {
            ImageIO.write(b, "png", new File(fileName));
        }
        catch(IOException e) {
            System.out.println("IO Exception: Can't write this file");
            System.exit(0);
        }
    }
}
